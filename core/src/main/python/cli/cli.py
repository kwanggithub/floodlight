#!/usr/bin/python
#
# cli -- The BigSwitch CLI
# (c) 2013 by Big Switch Networks
# All rights reserved.

#
#
#

import subprocess
import os
import sys, traceback                   # traceback.print_exc()
from optparse import OptionParser
from types import StringType
import collections
import datetime
import json
import re
import time
import urllib2
import httplib                          # provides error processing for isinstance
import socket
import select
import errno
import fcntl
import posixpath
import copy
import imp
import locale
import pwd

import bsn_constants
import utif
import error
import rest_api
import debug
import feature
import run_config
import doc
import command
import bigdb
import url_cache
import term

#
# --------------------------------------------------------------------------------
#

class Desc():
    # Manage the relationship between the cli and command descriptions
    # Find and load all the command descriptions based on cli parameters

    def __init__(self, options):
        self.load_command_descriptions(options)

    #
    # --------------------------------------------------------------------------------
    #
    def load_command_descriptions(self, options):
        # command option, then env, then default
        self.desc_version = options.desc_version
        if self.desc_version == None:
            self.desc_version = os.getenv('BIGCLI_COMMAND_VERSION')
        if self.desc_version == None:
            self.desc_version = '2.0'   # 'version200'

        if self.desc_version:
          self.desc_version = self.desc_version_to_path_elem(self.desc_version)

        self.add_command_packages(self.desc_version, options)

    #
    # --------------------------------------------------------------------------------
    #
    def command_packages_path(self):
        command_descriptions = 'desc'
        desc_path = os.path.join(os.path.dirname(__file__), command_descriptions)
        if os.path.exists(desc_path):
            return desc_path
        return None


    #
    # --------------------------------------------------------------------------------
    #
    def command_packages_exists(self, version):
        desc_path = self.command_packages_path()
        if desc_path == None:
            return None
        version_path = os.path.join(desc_path, version)
        if os.path.exists(version_path):
            return version_path
        return None


    #
    # --------------------------------------------------------------------------------
    #
    def add_command_packages(self, version, options):
        """
        Add all command and output format components

        """
        desc_path = self.command_packages_path()
        if desc_path == None:
            print 'No Command Descriptions subdirectory'
            return

        for path_version in [x for x in os.listdir(desc_path) if x.startswith(version)]:
            result_tuple = imp.find_module(path_version, [desc_path])
            imp.load_module(version, result_tuple[0], result_tuple[1], result_tuple[2])
            new_path = result_tuple[1]
            cmds_imported = []
            for cmds in os.listdir(new_path):
                (prefix, suffix) = os.path.splitext(cmds)
                if prefix == '__init__':
                    continue
                if (suffix != '.py' and suffix != '.pyc'):
                    continue
                if prefix not in cmds_imported:
                    cmds_imported.append(prefix)
                    result_tuple = imp.find_module(prefix, [new_path])
                    module = imp.load_module(prefix, result_tuple[0], result_tuple[1], result_tuple[2])
                    command.add_commands_from_module(version, module, options.dump_syntax)
            # print cmds_imported

    #
    # --------------------------------------------------------------------------------
    #
    def desc_version_to_path_elem(self, version):
        """
        Version numbers like 1.0 need to be converted to the path
        element associated with the number, like version100
        """
        try:
            version_number = float(version)
            version = 'version%s' % int(version_number * 100)
        except:
            pass

        # temporary, use until version100 exists.
        if version == 'version100' and not self.command_packages_exists(version):
            version = 'version200'

        return version

#
# --------------------------------------------------------------------------------
#

class Finder():
    # Manage activites associated with finding commands, this includes
    # first-word command completion

    def __init__(self, options, controller, rest_api):
        # both Run and Finder need access to the same mode_stack.
        self.mode_stack = ModeStack(options, controller, rest_api)

        self.completion_reset()

        self.completion_skip = False
        self.completion_cache = True

        readline.set_completer(self.completer)
        readline.set_completer_delims("\t ")

        # readline.set_pre_input_hook(self.pre_input_hook)
        readline.set_completion_display_matches_hook(self.matches_hook)

    #
    # --------------------------------------------------------------------------------
    #
    def matches_hook(self, subs, matches, max_len):

        # one shot disabler, used to disable printing of completion
        # help for two-column help display (for '?' character).
        # completion printing here can only display the possible selections,
        # for two-column mode, the reason why each keyword was added
        # needs to be displayed, which is no longer available here.

        if self.completion_print == False:
            self.completion_print = True
            return

        choices_text = term.choices_text_builder(matches, max_len)
        self.print_completion_help(choices_text)


    #
    # --------------------------------------------------------------------------------
    # completion_reset
    #
    def completion_reset(self):
        self.last_line = None
        self.last_options = None
        self.completion_cache = True
        self.last_completion_char = readline.get_completion_type()

    #
    # --------------------------------------------------------------------------------
    # complete_all_commands
    #
    def complete_all_commands(self, words, text, completion_char):
        """
        Completion for the help command must be done using the collection
        of command descriptions; ie: help uses the command descriptions
        to complete the help commands.
        """
        if completion_char == ord('?'):
            if len(words) > 1:
                command.do_command_completion_help(words[1:], text)
            else:
                print self.help_splash([], text)
            return
        if len(words) == 1:
            items = self.commands_for_current_mode_starting_with(text)
            return utif.add_delim(items, ' ')
        else:
            return command.do_command_completion(words[1:], text)

    #
    # --------------------------------------------------------------------------------
    # complete_echo_command
    #
    def complete_echo_command(self, words, text, completion_char):
        return 

    #
    # --------------------------------------------------------------------------------
    # directly_completed_commands
    #
    def directly_completed_commands(self, word):
        if word == 'help':
            return self.complete_all_commands
        if word == 'watch':
            return self.complete_all_commands
        if word == 'echo':
            return self.complete_echo_command
        return None

    #
    # --------------------------------------------------------------------------------
    # print_completion_help
    #
    def print_completion_help(self, completion_help_text):
        origline = readline.get_line_buffer()
        end = readline.get_endidx()
        cur_command = origline[0:end]

        help_text = "\n%s\n%s%s" % ( completion_help_text,
                                    self.mode_stack.get_prompt(),
                                    cur_command)
        self.completion_skip = True
        sys.stdout.write(help_text)

    #
    # --------------------------------------------------------------------------------
    # completer
    #  This is the main function that is called in order to complete user input
    #
    def completer(self, text, state):
        question_mark = ord('?')
        if readline.get_completion_type() == question_mark:
            # zero characters, OR first token
            if text == readline.get_line_buffer():
                #
                # manage printing of help text during command completion
                help_text = self.help_splash(None, text)
                if help_text != "":
                    self.print_completion_help(help_text)
                    return
            elif len(text) and text[0] == "'":
                readline.insert_text('?')
                return None

        try:
            origline = readline.get_line_buffer()
            # See if we have a cached reply already
            if (self.completion_cache and origline == self.last_line and
                self.last_completion_char == readline.get_completion_type() and
                self.last_options):

                if state < len(self.last_options):
                    return self.last_options[state]
                else:
                    # apparently, for the linux VM choice don't print
                    if self.last_options and \
                      len(self.last_options) > 1 and \
                      self.last_completion_char == ord('\t'):
                        choices_text = term.choices_text_builder(self.last_options)
                        self.print_completion_help(choices_text)

                    if self.completion_skip:
                        self.completion_cache = False
                        self.completion_skip = False
                    return None

            self.completion_reset()

            # parse what user has typed so far

            begin = readline.get_begidx()
            end = readline.get_endidx()

            # Find which command we're in for a semicolon-separated list of single commands
            # LOOK! This doesn't handle cases where an earlier command in the line changed
            # the mode so the completion for later commands in the line should be different.
            # For example, if you typed "enable; conf" it won't detect that it should be
            # able to complete "conf" to "configure" because the enable command has not been
            # executed yet, so you're not in enable mode yet. Handling that case would be
            # non-trivial I think, at least with the current CLI framework.
            command_begin = 0
            command_end = 0
            while True:
                command_end = utif.find_with_quoting(origline, ';', start_index=command_begin)
                if command_end < 0:
                    command_end = len(origline)
                    break
                if begin >= command_begin and end <= command_end:
                    break
                command_begin = command_end + 1

            # Skip past any leading whitespace in the command
            while command_begin < begin and origline[command_begin].isspace():
                command_begin += 1

            words = origline[command_begin:end].split()

            # remove last term if it is the one being matched
            if begin != end:
                words.pop()

            # LOOK! there are at least three places that try to parse the valid options:
            # 1. When actually handling a command
            # 2. When trying to show completions (here)
            # 3. When displaying help

            # complete the first word in a command line
            if not words or begin == command_begin:
                options = self.commands_for_current_mode_starting_with(text, completion = True)
                options = [x if x.endswith(' ') else x + ' '  for x in sorted(options)]
            # Complete the 2nd word or later
            else:
                commands = self.commands_for_current_mode_starting_with(words[0])
                if len(commands) == 1:
                    try:
                        # options[0] is expanded while words[0] is not
                        method = self.directly_completed_commands(commands[0])
                        if method:
                            options = method(words, text, readline.get_completion_type())
                            if not options:
                                # no match
                                return None
                        else:
                            if readline.get_completion_type() == question_mark:
                                options = command.do_command_completion_help(words, text)
                                # matches_hook only seems to be called when more than
                                # one choice is available.  For two column mode, the
                                # matches choices are disabled, but if there's a single
                                # choice, an additinoal message will display.   Here, then
                                # another choice is added, which causes matches_hook to get
                                # called, and not print any additional data. BSC-3645
                                if len(options) == 1:
                                    options.append(' --forget this-- ' )
                            else:
                                options = command.do_command_completion(words, text)

                    except AttributeError:
                        if debug.cli():
                            traceback.print_exc()
                        return None

                else:
                    options = None

        except Exception, e:
            if debug.cli():
                traceback.print_exc()

        try:
            if options:
                self.last_line = origline
                self.last_options = options
                self.last_completion_char = readline.get_completion_type()
                return options[state]
        except IndexError:
            return None

        return None


    #
    # --------------------------------------------------------------------------------
    # title_of
    #
    @staticmethod
    def title_of(command):
        return command['title'] if type(command) is dict else command


    #
    # --------------------------------------------------------------------------------
    # commands_feature_enabled
    #
    def commands_feature_enabled(self, commands):
        return [self.title_of(x) for x in commands
                if (not self.title_of(x) in command.command_name_feature) or
                    command.is_command_feature_active(self.title_of(x),
                           command.command_name_feature[self.title_of(x)])]


    #
    # --------------------------------------------------------------------------------
    # commands_rbac_enabled
    #
    def commands_rbac_enabled(self, commands):
        def rbac_group_allowed(rbac_groups):
            try:
                command.action_invoke('rbac-required', ({}, rbac_groups))
                return True
            except error.CommandUnAuthorized, e:
                return False

        return [self.title_of(x) for x in commands
                if (not self.title_of(x) in command.command_name_rbac_group) or
                    rbac_group_allowed(command.command_name_rbac_group[self.title_of(x)])]


    #
    # --------------------------------------------------------------------------------
    # commands_for_mode
    #
    def commands_for_mode(self, mode):
        """
        Walk the command dict, using interior submodes and compiling
        the list of available commands (could rebuild command_dict()
        to contain all the possible commands, but its good to know
        exactly which commands apply to this submode)
        """

        # make a new list, so that items don't get added to the source
        ret_list = list(command.command_nested_dict.get('login', []))
        if mode == 'login':
            ret_list += command.command_dict.get('login', [])
            return ret_list
        ret_list += command.command_nested_dict.get('enable', [])
        if mode == 'enable':
            ret_list += command.command_dict.get('enable', [])
            return ret_list

        if mode == 'config':
            ret_list += command.command_nested_dict.get('config', [])
            ret_list += command.command_dict.get('config', [])
            return ret_list

        for idx in [x for x in command.command_nested_dict.keys() if mode.startswith(x)]:
            ret_list += command.command_nested_dict.get(idx, [])

        ret_list += command.command_dict.get(mode, [])

        # manage command who's names are regular expressions
        result = [x['re'] if type(x) == dict else x  for x in ret_list]

        return result
    #
    # --------------------------------------------------------------------------------
    # commands_for_current_mode_starting_with
    #
    def commands_for_current_mode_starting_with(self,
                                                start_text = "", completion = None):
        """
        One of the difficult issues here is when the first item
        isn't a token, but rather a regular expression.  This currently occur
        in a few places in the command description, and the mechanism for
        dealing with the issue here is ... uhm ... poor.  The code here is
        a stopgap, and assumes the only regular expression supported
        is the <digits> one.  This could be make a bit better based on
        the submode, but really, this entire first-token management should
        be improved.
        """
        if completion == None:
            completion = False

        command.commands_for_submode(start_text)
        mode_list = self.commands_for_mode(self.mode_stack.current_mode())
        ret_list = self.commands_feature_enabled(utif.unique_list_from_list(mode_list))

        def prefix(x, start_text, completion):
            if type(x) == str and x.lower().startswith(start_text.lower()):
                return True
            if not completion and type(x) == re._pattern_type:
                return x.match(start_text)
            return False

        def pattern_items(ret_list, prefix):
            matches = []
            for p in [x for x in ret_list if type(x) == re._pattern_type]:
                # the m*n behavior here is only acceptable since
                # there are very few command with re._pattern_type
                for c in command.command_registry:
                    if c['mode'] != self.mode_stack.current_mode():
                        continue
                    if type(c['name']) != dict:
                        continue
                    first_word = c['name']
                    if 'completion' not in first_word:
                        continue
                    completion = first_word['completion']
                    if first_word['pattern'] == p.pattern:
                        result = {}
                        scopes = [ first_word,
                                   {
                                    'completions' : result,
                                    'data'        : {},
                                    'text'        : prefix,
                                   },
                                 ]
                        command._call_proc(completion,
                                           command.completion_registry,
                                           scopes, c)
                        matches = result.keys()
            return matches


        def filter_unauthorized_commands(previous_matches):
            matches = []
            for p in previous_matches:
                not_allowed = False
                for c in command.command_registry:
                    first_word = c['name']
                    if type(first_word) == dict:
                        continue
                    # this m*n search ought to be improved
                    if first_word != p:
                        continue
                    modes = c['mode']
                    # possibly should 'modes == None' generate a warning here?
                    if type(modes) == str or type(modes) == unicode:
                        modes = [modes]
                    if not command._match_current_modes(c,
                                                        self.mode_stack.current_mode(),
                                                        modes):
                        continue
                    rbac_group = c.get('rbac-group')
                    if rbac_group:
                        try:
                            command.action_invoke('rbac-required', ({}, rbac_group))
                        except error.CommandUnAuthorized, e:
                            not_allowed = True
                            continue # other variations of the command may be allowed.
                        else:
                            matches.append(p)
                            break
                    else:
                        matches.append(p)
                        break
                else:
                    # only add the variation when there was no un-authorized
                    # variations which were discoverd.
                    if not_allowed == False:
                        matches.append(p)
            return matches

        matches = [x for x in ret_list if prefix(x, start_text, completion)]

        matches = filter_unauthorized_commands(matches)

        if completion:
           matches += pattern_items(ret_list, start_text)

        return matches

    #
    # --------------------------------------------------------------------------------
    # help_splash
    #
    def help_splash(self, words, text):
        ret = ""
        if not words:
            if text == "":
                ret += "For help on specific commands: help <topic>\n"

            count = 0
            longest_command = 0
            # this submode commands
            s_ret = ""
            mode = self.mode_stack.current_mode()
            nested_mode_commands = [self.title_of(x) for x in
                                    command.command_nested_dict.get(mode, [])]
            possible_commands = [self.title_of(x) for x in
                                 command.command_dict.get(mode, []) ] + \
                                nested_mode_commands
            available_commands = self.commands_feature_enabled(possible_commands)
            available_commands = self.commands_rbac_enabled(available_commands)
            submode_commands = sorted(utif.unique_list_from_list(available_commands))
            if len(submode_commands):
                longest_command = max([len(x) for x in submode_commands
                                       if x.startswith(text)])
            for i in submode_commands:
                if not i.startswith(text):
                    continue
                count += 1
                short_help = command.get_command_short_help(i)
                if short_help:
                    s_ret += "  %s%s%s\n" % (i,
                                          ' ' * (longest_command - len(i) + 1),
                                          short_help)
                else:
                    s_ret += "  %s\n" % i

            # commands
            c_ret = ""
            upper_commands = [x for x in self.commands_for_current_mode_starting_with()
                              if not x in submode_commands and x.startswith(text)]
            commands = sorted(upper_commands)
            if len(commands):
                longest_command = max([len(x) for x in commands if x.startswith(text)] +
                                      [longest_command])
            for i in commands:
                if not i.startswith(text):
                    continue
                count += 1
                short_help = command.get_command_short_help(i)
                if short_help:
                    c_ret += "  %s%s%s\n" % (i,
                                          ' ' * (longest_command - len(i) + 1),
                                          short_help)
                else:
                    c_ret += "  %s\n" % i

            if (text == "" or count > 0) and s_ret != "":
                ret += "Commands:\n"
                ret += s_ret

            if (text == "" or count > 0) and c_ret != "":
                ret += "All Available commands:\n"
                ret += c_ret
        else:
            try:
                ret = command.get_command_documentation(words)
            except:
                if debug.cli():
                    traceback.print_exc()
                ret = "No help available for command %s\n" % words[0]
        return ret


#
# --------------------------------------------------------------------------------
#

class Audit():
    # Manage command auditing

    def __init__(self, bigdb):
        self.bigdb = bigdb
        self.suppress_audit = False
        if os.getenv('BIGCLI_SUPPRESS_AUDIT'):
            self.suppress_audit = True

    #
    # --------------------------------------------------------------------------------
    # get_application_keyfile
    #
    @staticmethod
    def get_application_keyfile():
        if pwd.getpwuid(os.getuid()).pw_name == "bsn":
            keyfile = bsn_constants.audit_cli_keyfile
        else:
            keyfile = os.path.expanduser(bsn_constants.user_cli_audit_keyfile)

        return keyfile

    #
    # --------------------------------------------------------------------------------
    # audit_command
    #
    def audit_command(self, words):
        """Generate an audit log for this command.

        XXX roth -- currently no handling for REST errors, though in
        general an accounting log failure is considered non-fatal.

        XXX roth -- at the point that we audit the command, any
        "failed" command has already exited early.  In this case we
        would need to rely on the REST call trace.
        """
        if self.suppress_audit:
            return;

        # do not bother trying to generate an audit if there is no app key
        appAuthFile = self.get_application_keyfile()
        if appAuthFile is None:
            return

        q = {}

        q['event-type'] = 'bigcli.command'
        q['attributes'] = [{'attribute-key' : 'cmd_args',
                            'attribute-value' : " ".join(words),},]
        # XXX roth -- no way to infer "command success" (ret_val is
        # free-flowing text)

        try:
            self.bigdb.post("core/aaa/audit-event", q,
                            cookieAuth=False, appAuthFile=appAuthFile)
        except urllib2.HTTPError, e:
            if e.code in (403, 401):
                # (not authorized, reauth) is OK during auditing
                pass
            else:
                if debug.cli():
                    traceback.print_exc()
        except Exception as e:
            if debug.cli():
                traceback.print_exc()


#
# --------------------------------------------------------------------------------
#

class ModeStack():
    # Manage mode_stack for the cli

    def __init__(self, options, controller, rest_api):
        self.rest_api = rest_api
        self.mode_stack = []

        # cached_current_role_init()
        # role cache, to improve command feature relationship for ha, and prompt
        self.cached_last_current_role = {}
        self.cached_last_updated_current_role = {}

        if controller:
            self.set_controller_for_prompt(controller)

        # mode stack is state associated with this BigSh instance
        # since setting the mode means computing the prompt, and computing
        # the prompt depends on role, initialization for cached_current_role
        # must occur before the mode is initialized
        self.push_mode("login")

        # check for specific requested starting mode,
        starting_mode = options.starting_mode
        if starting_mode == None:
            starting_mode = os.getenv('BIGCLI_STARTING_MODE')

        if starting_mode:
            if starting_mode == 'login':
                pass
            elif starting_mode == 'enable':
                self.push_mode("enable")
            elif starting_mode == 'config':
                self.push_mode("enable")
                self.push_mode("config")
            else:
                print 'Only login, enable or config allowed as starting modes'

    #
    # --------------------------------------------------------------------------------
    # push_mode
    #
    #  The exitCallback is the nane of a method to call when the current pushed
    #  level is getting pop'd.
    #
    def push_mode(self, mode_name,
                        path=None,
                        obj=None,
                        exitCallback=None,
                        item_name=None,
                        show=None):
        self.mode_stack.append( {                            # e.g.:
                                  'mode_name' : mode_name,   # config-switch
                                  'path'      : path,        # core/switch
                                  'obj'       : obj,         # <dpid>
                                  'exit'      : exitCallback,
                                  'name'      : item_name,   # 'switch'
                                  'show_this' : show,        # show switch <dpid>
                               } )
        if debug.description():
            print 'push_mode: last', self.mode_stack[-1]
        self.update_prompt()


    #
    # --------------------------------------------------------------------------------
    #
    def mode_stack_top(self):
        """
        Return the top of the mode stack.
        """
        return self.mode_stack[-1]


    #
    # --------------------------------------------------------------------------------
    # pop_mode
    #  Pop the top of the stack of mode's.
    #
    def pop_mode(self):
        m = self.mode_stack.pop()
        if len(self.mode_stack) == 0:
            self.run = False
        else:
            self.update_prompt()
        return m


    #
    # --------------------------------------------------------------------------------
    # mode_stack_to_rest_dict
    #  Convert the stack of pushed modes into a collection of keys.
    #  Can be used to build the rest api dictionary used for row creates
    #
    def mode_stack_to_rest_dict(self, rest_dict = None, max_depth = None):
        if rest_dict == None:
            rest_dict = {}
        #
        if max_depth == None:
            max_depth = len(self.mode_stack)
        for x in self.mode_stack[:max_depth]:
            if x['mode_name'].startswith('config-'):
                if x.get('path') != None:
                    rest_dict[x['path']] = x['obj']

        return rest_dict

    #
    # --------------------------------------------------------------------------------
    # current_mode
    #  Return the string describing the current (top) mode.
    #
    def current_mode(self):
        if len(self.mode_stack) < 1:
            return ""
        return self.mode_stack[-1]['mode_name']

    #
    # --------------------------------------------------------------------------------
    # get_current_mode_obj
    #  Gets the name of the current mode's selected row value (key's value)
    #  This can return None.
    #
    def get_current_mode_obj(self):
        return self.mode_stack[-1]['obj']

    #
    # --------------------------------------------------------------------------------
    # get_current_mode_path
    #  Gets the name of the current mode's selected row value (key's value)
    #  This can return None.
    #
    def get_current_mode_path(self):
        return self.mode_stack[-1]['path']

    #
    #
    # --------------------------------------------------------------------------------
    # set_contoller_for_prompt
    #
    def set_controller_for_prompt(self, controller):
        self.controller = controller

        if controller == "127.0.0.1:8000":
            self.controller_for_prompt = socket.gethostname()
        else:
            self.controller_for_prompt = controller
            self.update_prompt()

    #
    # --------------------------------------------------------------------------------
    # current_role
    #
    def current_role(self, controller_ip = None):
        if controller_ip == None:
            controller_ip = self.controller

        url = "http://%s/rest/v1/system/ha/role" % controller_ip
        try:
            result = self.rest_api.rest_simple_request(url,
                                                       use_cache = False,
                                                       timeout = 1)
        except httplib.BadStatusLine:
            # should the CLI exit at this point?
            return 'REST-API-DOWN'

        except urllib2.HTTPError, e:
            return 'URL-ERROR: %s' % e.code

        except urllib2.URLError, e:
            if type(e.args) == tuple:
                if len(e.args) == 1:
                    (failure,) = e.args
                    if failure:
                        if failure.errno == errno.EHOSTUNREACH:
                            return 'UNREACHABLE'
            if isinstance(e.reason, socket.timeout):
                return 'REST-API-DOWN'
            if debug.cli():
                print 'ERROR IN HA ROLE COMPUTATION', e
            return ''

        except Exception, e:
            if debug.cli():
                print 'current_role: ', e
            return ''
        else:
            # XXX? self.check_rest_result(result)
            try:
                ha_role = json.loads(result)
            except:
                return 'JSON-ERROR'
            return ha_role['role']


    #
    # --------------------------------------------------------------------------------
    # cached_current_role
    #
    def cached_current_role(self, controller_ip = None):
        if controller_ip == None:
            controller_ip = self.controller

        now = datetime.datetime.now()
        if not controller_ip in self.cached_last_updated_current_role or \
           self.cached_last_updated_current_role[controller_ip] < now:

            age = 1 # every second look up the role
            self.cached_last_current_role[controller_ip] = self.current_role(controller_ip)
            self.cached_last_updated_current_role[controller_ip] = (
                now + datetime.timedelta(0, age))

        return self.cached_last_current_role[controller_ip]

    #
    # --------------------------------------------------------------------------------
    # update_prompt
    #  There are several different prompts depending on the current mode:
    #  'host'>                                  -- login mode
    #  'host'#                                  -- enable mode
    #  'host'(config)#                          -- config mode
    #
    def update_prompt(self):
        self.prompt_current_role = self.cached_current_role()

        current_role = ''
        if self.prompt_current_role != 'MASTER':
            current_role = self.prompt_current_role + ' ' # space separator
            if current_role == None or current_role == ' ':
                # current_role = '<role: excpetion> '
                current_role = ''

        if self.current_mode().startswith("config"):
            current_mode = "(" + self.current_mode()
            self.prompt = current_role + str(self.controller_for_prompt) + current_mode + ")# "
        elif self.current_mode() == "enable":
            self.prompt = current_role + str(self.controller_for_prompt) + "# "
        else: # login
            self.prompt = current_role + str(self.controller_for_prompt) + "> "

    #
    # --------------------------------------------------------------------------------
    # get_prompt
    def get_prompt(self, update = None):
        if update:
            self.update_prompt()
        return self.prompt

#
# --------------------------------------------------------------------------------
#

class Run():
    # Handle Running the command for the cli

    def __init__(self, options, controller, bigdb, rest_api):
        self.finder = Finder(options, controller, rest_api)
        self.audit = Audit(bigdb)

        self.screen_length = 0


    #
    # --------------------------------------------------------------------------------
    # set_screen_length
    #
    def set_screen_length(length):
        old_screen_length = self.screen_length
        self.screen_length = length
        return old_screen_length


    #
    # --------------------------------------------------------------------------------
    #
    def run_help(self, words):
        # help_splash is a generator.
        return self.finder.help_splash(words, "")

    #
    # --------------------------------------------------------------------------------
    #
    def run_echo(self, words):
        yield " ".join(words) + '\n'

    #
    # --------------------------------------------------------------------------------
    #
    def directly_implemented_commands(self, word):
        # a few command can't use the syntax or completions methods,
        # 'help' and 'watch' rely on completing other existing commands.
        # 'echo' has no distinct syntax.
        if word == 'help':
            return self.run_help
        if word == 'echo':
            return self.run_echo
        return None

    #
    # --------------------------------------------------------------------------------
    # handle_command
    #
    def handle_command(self, command_word, words):
        if type(command_word) == str:
            # allow a few odd commands to be implemented here.
            method = self.directly_implemented_commands(command_word)
            if method:
                return method(words)
        # XXX It would be better to only call do_command if it
        # was clear that this command actually existed.
        return command.do_command([command_word] + words)

    #
    # --------------------------------------------------------------------------------
    #
    def replay(self, file, verbose = True, command_replay = False):
        # Only replay the STR values, since the code ought to
        # stuff back the JSON values.
        play = open(file)
        rest_line_format = re.compile(r'^REST ([^ ]*)(  *)([^ ]*)(  *)(.*)$')
        cmd_line_format = re.compile(r'^COMMAND (.*)$')
        skip_command = True
        for line in play.read().split('\n'):
            # the format ought to be url<space>[STR|JSON]<space> ...
            match = rest_line_format.match(line)
            if match:
                if match.group(3) == 'STR':
                    if verbose:
                        print 'REST STR', match.group(1)
                    url_cache.save_url(match.group(1), match.group(5), 1000000)
                elif match.group(3) == 'JSON':
                    if verbose:
                        print 'REST JSON', match.group(1)
                    entries = json.loads(match.group(5))
                    url_cache.save_url(match.group(1), entries, 1000000)
                else:
                    print 'REST REPLAY NOT STR|JSON'
            elif len(line):
                match = cmd_line_format.match(line)
                if command_replay and match:
                    # skip the first command since it ought to be the replay enablement
                    if skip_command:
                        if verbose:
                            print 'SKIP COMMAND %s' % match.group(1)
                        skip_command = False
                    else:
                        line = utif.split_with_quoting(match.group(1))
                        if verbose:
                            print 'COMMAND %s' % line
                        output = self.handle_multipart_line(line[0])
                        if output != None:
                            print output
                else:
                    print 'no MATCH +%s+' % line
        play.close()

    #
    # --------------------------------------------------------------------------------
    # handle_single_line
    #
    def handle_single_line(self, line):
        ret_val = None
        if len(line) > 0 and line[0]=="!": # skip comments
            return
        words = utif.split_with_quoting(line)
        if not words:
            return

        # Look for the replay keyword, use the first two tokens if the replay
        # keyword is in the first part of the command.
        if debug.cli() and len(words) >= 2:
            if words[0] == 'replay':
                # replay the file, remove the first two keywords
                self.replay(words[1], command_replay = len(words) == 2)
                if len(words) == 2:
                    return
                words = words[2:]

        matches = self.finder.commands_for_current_mode_starting_with(words[0])
        # LOOK!: robv Fix to work with field names where one name is a prefix of another
        if len(matches) > 1:
            for match in matches:
                if match == words[0]:
                    matches = [match]
                    break
        if len(matches) == 1:
            match = matches[0]
            # Replace the (possibly) abbreviated argument with the full name.
            # This is so that the handlers don't need to all handle abbreviations.
            if type(match) == str:
                words[0] = match

            ret_val = self.handle_command(words[0], words[1:])
        elif len(matches) > 1:
            ret_val = error.error_message("%s is ambiguous\n" % words[0])
            for m in matches:
                ret_val += "%s (%s)\n" % m
        else:
            ret_val = error.error_message("Unknown command: %s\n" % words[0])

        url_cache.command_finished(words)

        # do not audit 'echo', it messses up Cli sync during bigtest
        if words[0] not in ('echo',):
            self.audit.audit_command(words)

        return ret_val

    #
    # --------------------------------------------------------------------------------
    # generate_pipe_output
    #
    def generate_pipe_output(self, p, output):
        fl = fcntl.fcntl(p.stdout, fcntl.F_GETFL)
        fcntl.fcntl(p.stdout, fcntl.F_SETFL, fl | os.O_NONBLOCK)

        for item in output:
            try:
                p.stdin.write(item)
            except IOError:
                break

            try:
                out_item = p.stdout.read()
                yield out_item
            except IOError:
                pass

        p.stdin.close()

        fcntl.fcntl(p.stdout, fcntl.F_SETFL, fl)
        while True:
            out_item = p.stdout.read()
            if (out_item):
                yield out_item
            else:
                p.stdout.close()
                break
        p.wait()


    #
    # --------------------------------------------------------------------------------
    # write_to_pipe
    #
    def write_to_pipe(self, p, output):
        for item in output:
            try:
                # need a better mechanism to identify when newlines are necessary
                if len(item) and item[-1] == '\n':
                    p.stdin.write(item)
                else:
                    p.stdin.write(item + '\n')
            except IOError:
                break
        p.stdin.close()
        p.wait()

    #
    # --------------------------------------------------------------------------------
    # shell_escape
    #  Return a string, quoting the complete string, and correctly prefix any
    #  quotes within the string.
    #
    def shell_escape(self, arg):
        return "'" + arg.replace("'", "'\\''") + "'"

    #
    # --------------------------------------------------------------------------------
    # handle_pipe_and_redirect
    #
    def handle_pipe_and_redirect(self, pipe_cmds, redirect_target, output):
        # if redirect target is tftp/ftp/http/file, then we should actually stick
        # curl at the end of the pipe_cmds so it gets handled below
        if redirect_target:
            if redirect_target.startswith("tftp") or redirect_target.startswith("ftp") or \
               redirect_target.startswith("http") or redirect_target.startswith("file"):

                # add so it can be used below
                if pipe_cmds == None:
                    pipe_cmds = ""
                else:
                    pipe_cmds += " | "

                if redirect_target.startswith("ftp"): # shell_escape added quote
                    pipe_cmds += " curl -T - %s" % self.shell_escape(redirect_target)
                else:
                    pipe_cmds += " curl -X PUT -d @- %s" % self.shell_escape(redirect_target)

        if pipe_cmds:
            new_pipe_cmd_list = []
            for pipe_cmd in [x.strip() for x in pipe_cmds.split('|')]:
                # doing it this way let us handles spaces in the patterns
                # as opposed to using split/join which would compress space
                new_pipe_cmd = pipe_cmd
                m = re.search('^(\w+)(.*)$', pipe_cmd)
                if m:
                    first_tok = m.group(1)
                    rest_of_cmd = m.group(2).strip()
                    if first_tok.startswith("in"):
                        new_pipe_cmd = "grep -e " + rest_of_cmd
                    elif first_tok.startswith("ex"):
                        new_pipe_cmd = "grep -v -e" + rest_of_cmd
                    elif first_tok.startswith("begin"):
                        new_pipe_cmd =  "awk '/%s/,0'" % rest_of_cmd
                new_pipe_cmd_list.append(new_pipe_cmd)

            new_pipe_cmds = "|".join(new_pipe_cmd_list)
            if new_pipe_cmds:
                if redirect_target:
                    p = subprocess.Popen(new_pipe_cmds,
                                         shell=True,
                                         stdin=subprocess.PIPE,
                                         stdout=subprocess.PIPE,
                                         stderr=subprocess.STDOUT)
                    output = self.generate_pipe_output(p, output)
                else:
                    p = subprocess.Popen(new_pipe_cmds,
                                         shell=True,
                                         stdin=subprocess.PIPE)
                    self.write_to_pipe(p, output)
                    output = None

                    
        # only handle local file here as http/ftp were handled above via pipe
        if redirect_target: 
            if redirect_target.startswith("config://"):
                m = re.search(self.local_name_pattern, redirect_target)
                if m:
                    join_output = '\n'.join(iter(output))
                    store_result = self.rest_api.set_user_data_file(m.group(1), join_output)
                    if store_result:
                        result = json.loads(store_result)
                    else:
                        return error.error_message("rest store result not json format")
                    if 'status' in result and result['status'] == 'success':
                        return None
                    elif 'message' not in result:
                        return error.error_message("rest store result doesn't contain error message")
                    else:
                        return error.error_message(result['message'])
                else:
                    print error.error_message("invalid name-in-db (%s)\n" % redirect_target)
            else:
                return output

        return None


    #
    # --------------------------------------------------------------------------------
    # generate_command_output
    #
    @staticmethod
    def generate_command_output(ret_val):
        if (isinstance(ret_val, str) or \
            isinstance(ret_val, buffer) or \
            isinstance(ret_val, bytearray) or \
            isinstance(ret_val, unicode)):

            for line in ret_val.splitlines(True): # keep the end of line marker
                yield line
        elif ret_val != None:
            for item in ret_val:
                yield item


    #
    # --------------------------------------------------------------------------------
    # generate_line_output
    #
    # This is a generator that will generate the output of the
    # command when itss a string, or by iterating over a true generator.
    #
    def generate_line_output(self, line, dont_ask):
        while line:
            subline_index = utif.find_with_quoting(line, ';')
            if subline_index < 0:
                subline_index = len(line)
            subline = line[:subline_index]
            line = line[subline_index+1:]
            ret_val = self.handle_single_line(subline)
            cnt = 1
            total_cnt = 0

            (col_width, screen_length) = term.get_terminal_size()
            if type(self.screen_length) == int:
                screen_length = self.screen_length

            for item in self.generate_command_output(ret_val):
                if not dont_ask:
                    # This assumes an item of len(x) consumes 'x' characters, but
                    # if there are imbedded backspace characters, or other column
                    # moving characters, this isnt true
                    incr = 1 + (max((len(item.rstrip()) - 1), 0) / col_width)
                    if screen_length and cnt + incr >= screen_length:
                        raw_input('-- hit return to continue (%s) --' % total_cnt)
                        cnt = 0
                    cnt += incr
                    total_cnt += incr
                yield item


    #
    # --------------------------------------------------------------------------------
    # handle_multipart_line
    #
    # this is the outermost handler that should print
    #
    def handle_multipart_line(self, line):
        pipe_cmds = None
        redirect_target = None
        output = None

        # pattern is:
        # single line [; single line]* [| ...] [> {conf|ftp|http}]

        # first take off the potential redirect part then the pipe cmds
        redirect_index = utif.find_with_quoting(line, '>', reverse = True)
        if redirect_index >= 0:
            redirect_target = line[redirect_index+1:].strip()
            line = line[:redirect_index].strip()
            if utif.find_with_quoting(line, '>'):
                print error.error_message('Multiple redirections in command')
                return
        pipe_index = utif.find_with_quoting(line, '|')
        if pipe_index >= 0:
            pipe_cmds = line[pipe_index+1:].strip()
            line = line[:pipe_index].strip()

        # Store the pipe and redirect commands
        # Legacy commands that need to yield partial output will
        # use these commands to send the partial output through the
        # stored references to the pipe and redirect cmds
        self.pipe_cmds = pipe_cmds
        self.redirect_target = redirect_target
        # remaining text is single lines separated by ';' - handle them
        output = self.generate_line_output(line, pipe_cmds or redirect_target)

        # now output everything
        if self.pipe_cmds or self.redirect_target:
            output = self.handle_pipe_and_redirect(pipe_cmds, redirect_target, output)

        if output != None:
            for line in output:
                print line,

    #
    # --------------------------------------------------------------------------------
    # handle_watch_command
    #
    # handle this here because this is a CLI-only command
    # LOOK! This could be using curses, but that has some complications with
    # potentially messing up the terminal.  This is cheap but downside
    # is it uses up the scrollbuffer...
    #
    def handle_watch_command(self, line):
        # XXX no quoting.
        words = utif.split_with_quoting(line)
        if len(words) == 0:
            # Note: error/exception must be managed here for directly
            # implemented commands.
            print self.syntax_msg('watch: command to watch missing')
            return

        if len(words) and words[0] == 'watch':
            print error.error_message('watch command not supported for watch')
            return

        while True: # must control-C to get out of this
            # XXX: what's the effect of screen length on watch?
            if False: # XXX how to choose to clear the screen?
                os.system("clear")
            print 'Command: "%s"' % line
            self.handle_multipart_line(line)
            time.sleep(2)


#
# --------------------------------------------------------------------------------
#

class BigSh():
    # Components:
    #
    # Run           --
    #   Finder      --
    #     ModeStack --
    #   Audit       --
    #
    # components without speficif classes: documentation

    #
    # --------------------------------------------------------------------------------
    #
    def init_features(self):
        feature.init_feature(self)

    #
    # --------------------------------------------------------------------------------
    #
    def init_bigdb(self):
        # check for BigDB
        self.bigdb = bigdb.BigDB(self.controller, self)
        if not self.bigdb.enabled():
            print 'BIGDB NOT ENABLED'
        self.bigdb_run_config = None
        self.rc_scoreboard = None


    #
    # --------------------------------------------------------------------------------
    #
    def bigdb_reinit(self):
        self.bigdb.schema_request()
        self.bigdb_run_config = None


    #
    # --------------------------------------------------------------------------------
    #
    def init_documentation(self):
        # Initialize all doc tags, use a subdirectory based on
        # the locale.   This may be incorect if there's nothing
        # in the configured locale for the returned locale value.
        lc = locale.getdefaultlocale()
        if lc == None or lc[0] == None:
            print 'Locale not configured ', lc
            lc = ('en_US', 'UTF8')

        self.doc_dir = os.path.join(os.path.dirname(__file__), 'documentation', lc[0])
        if debug.cli():
            print "doc_dir %s" % self.doc_dir
        doc.add_doc_tags(self.doc_dir)
        #


    #
    # --------------------------------------------------------------------------------
    #
    def init_command(self):
        # Initialize the command module, than add command packages
        command.init_command(self)
        self.desc = Desc(self.options)

        # Once all the packages are loaded, now align the running-config
        # choices with the currently enabled featues.
        run_config.align_running_config_command_choices(self)


    #
    # --------------------------------------------------------------------------------
    #
    def init_batch_mode(self):
        # Are we running in batch mode, i.e. "cat commands | bigcli"
        self.batch = False
        if not sys.stdin.isatty(): # should this also be managed via an option
            self.batch = True


    #
    # --------------------------------------------------------------------------------
    #
    def init_user(self, options):
        # Use the same action as the reauth command would issue
        if options.user:
            if command.action_exists('aaa-reauth'):
                data = { 'user' : options.user }
                if self.options.password:
                    data['password'] = self.options.password

                try:
                    command.action_invoke('aaa-reauth', (data,))
                except Exception, e:
                    print 'Authentication Failure:', e
                    sys.exit(1)
            else:
                print 'User/Password options not supported'


    #
    # --------------------------------------------------------------------------------
    #
    def __init__(self):

        # set up the parameters
        parser = OptionParser()
        parser.add_option("-r", "--rest", dest="controller",
                          help="Configure the REST API address (controller)",
                          metavar="CONTROLLER", default=None)
        parser.add_option("-S", "--syntax", dest='dump_syntax',
                          help="display syntax of loaded commands",
                          action='store_true', default=False)
        parser.add_option("-i", "--init", dest='init',
                          help="do not perform initialization checks",
                          action='store_true', default=False)
        parser.add_option("-d", "--debug", dest='debug',
                          help='enable debug for cli (debug cli)',
                          action='store_true', default=False)
        parser.add_option("-v", "--version", dest='desc_version',
                          help='select command versions (description group)',
                          default=None)
        parser.add_option('-m', "--mode", dest='starting_mode',
                          help='once the cli starts, nest into this mode')
        parser.add_option('-q', "--quiet", dest='quiet',
                          help='suppress warning messages',
                          action='store_true', default=False)
        parser.add_option('-c', "--command", dest='single_command',
                          help='execute single command and exit',
                          default=None)
        parser.add_option('-u', "--user", dest='user',
                          help='authenticate as requested user',
                          default=None)
        parser.add_option('-p', "--password", dest='password',
                          help='authenticate with indicated password',
                          default=None)
        (self.options, self.args) = parser.parse_args()

        # controller initializaiton
        self.controller = self.options.controller
        if not self.controller:
            self.controller = "127.0.0.1:8000"
        
        # propogate options
        if self.options.debug:
            debug.cli_set(self.options.debug)

        self.suppress_audit = os.getenv('BIGCLI_SUPPRESS_AUDIT')

        #
        self.rest_api = rest_api.RestApi(self.controller)
        self.init_bigdb()            # schema
        self.run = Run(self.options, self.controller, self.bigdb, self.rest_api)

        #
        self.init_batch_mode()       # determine running mode
        self.init_documentation()    # build documentation references
        self.init_features()         # register core command feature assciations
        self.init_command()          # command descriptions
        #
        self.init_user(self.options) # "login" as requested user

    #
    #
    # --------------------------------------------------------------------------------
    #
    def running_config(self, path = None, scoreboard = None, detail = None):
        """
        bigdb running config generator.

        If called with no parameters, ensures the running-config generator
        has been initialized.
        """

        if self.bigdb_run_config == None:
            self.bigdb_run_config = bigdb.BigDB_run_config(self,
                                                           self.bigdb,
                                                           command.command_registry)
        if scoreboard == None:
            scoreboard = self.rc_scoreboard
        self.bigdb_run_config.generate(path, scoreboard, detail)

    #
    # --------------------------------------------------------------------------------
    # scp_command
    #
    def scp_command(self, line):
        # if scp is requested, invoke it directly
        # should we have a specific ssh group to allow its use
        # Don't use shell=True, since that opens the possibility of
        # having pipes or multiple commands.
        return subprocess.call(['/usr/bin/scp'] + line.split()[1:])

    #
    # --------------------------------------------------------------------------------
    # single_command
    #
    def single_command(self, line):
        """Exeute a single command, manage exceptions"""
        try:
            url_cache.clear_cached_urls()

            m = re.search('^watch (.*)$', line)
            if m:
                self.run.handle_watch_command(m.group(1))
            else:
                self.run.finder.completion_reset()
                self.run.handle_multipart_line(line)
            sys.stdout.flush()
            return 0
        except KeyboardInterrupt:
            self.run.finder.completion_reset()
            print "\nInterrupt."
            return 1
        except urllib2.HTTPError, e:
            print 'HTTPError', e
            return 4
        except urllib2.URLError, e:
            print error.error_message("communicating with REST API server on %s "
                                      "- Network error: %s" %
                                      (self.controller, e.reason))
            return 3
        except Exception, e:
            print "\nError running command '%s'.\n" % line
            if debug.cli():
                print command._line(), ' backtrace'
                traceback.print_exc()
            return 127
        return rc

    #
    # --------------------------------------------------------------------------------
    # loop
    #
    def loop(self):
        """core dispatching command error handling"""
        self.running = True
        if not self.batch:
            print "Big Network Controller"

        # wait-for-controller will very likely be ressurected.
        if self.options.init == False and command.action_exists('wait-for-controller'):
            command.action_invoke('wait-for-controller', (5,))

        if self.controller:
            try:
                version_url = 'http://%s/rest/v1/system/version' % self.controller
                version_reply = self.rest_api.rest_simple_request(version_url)
                version = json.loads(version_reply)
            except Exception, e:
                version = [{'controller' : 'REST API FAILURE\n'}]

            if not self.batch:
                print "Controller: %s" % (version[0]['controller'],),

        command.action_invoke('history-setup', {})
        command.action_invoke('validate-switch', {})

        while self.running:
            try:
                if self.options.single_command:
                    line = self.options.single_command
                    self.running = False
                elif self.options.init:
                    line = raw_input('') # don't use prompts for --init mode
                    print 'command:', line
                else:
                    # prompt is updated at each command prompt since the
                    # controller role may change spontaneously (MASTER->SLAVE)
                    line = raw_input('%s' % self.run.finder.mode_stack.get_prompt(update = True))
            except EOFError:
                if debug.cli() or debug.description():
                    print "\nExiting."
                return 0
            except KeyboardInterrupt:
                self.run.finder.completion_reset()
                print "\nInterrupt."

            rc = self.single_command(line)


#
# --------------------------------------------------------------------------------
# Initialization crazyness to make it work across platforms.
# Many platforms don't include GNU readline (e.g. mac os x), compensate for this
#
try:
    import readline
except ImportError:
    try:
        import pyreadline as readline
    except ImportError:
        print "Can't find any readline equivalent - aborting."
else:
    if 'libedit' in readline.__doc__:
        # needed for Mac, please fix Apple
        readline.parse_and_bind ("bind ^I rl_complete")
    else:
        readline.parse_and_bind("tab: complete")
        readline.parse_and_bind("?: possible-completions")



#
# --------------------------------------------------------------------------------
# 
def main():
    global cli
    # Uncomment the next two lines to enable remote debugging with PyDev
    # LOOK! Should have logic here that enables/disables the pydevd stuff
    # automatically without requiring uncommenting (and, more importantly,
    # remembering to recomment before committing).
    # (e.g. checking environment variable or something like that)
    #python_path = os.environ.get('PYTHONPATH', '')
    #if 'pydev.debug' in python_path:
    try:
        import pydevd
        pydevd.settrace()
    except Exception, e:
        pass

    # Start CLI
    cli = BigSh()
    if cli.options.single_command:
        command = cli.options.single_command
        if command.startswith('scp '):
            rc = cli.scp_command(command)
        else:
            rc = cli.single_command(command)
    else:
        rc = cli.loop()

    # cleanup.
    if cli.bigdb:
        cli.bigdb.revoke_session()

    sys.exit(rc)

if __name__ == '__main__':
    main()
