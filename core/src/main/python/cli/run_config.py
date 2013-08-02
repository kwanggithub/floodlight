#
#  bigsh - The BigSwitch Controller Shell
#  (c) in 2012, 2013 by Big Switch Networks
#  All rights reserved
#

#
# show running-config
#  and associated
#

import urllib2
import datetime
import re

import utif

from bigdb import BigDB_RC_scoreboard, BigDB_run_config


running_config_registry = {}

running_config_command_choices = {
    'optional' : True,
    'choices'  : (
    )
}

running_config_command_choices_registry = {}


#
# --------------------------------------------------------------------------------

def is_rbac_admin(context):
    context.bigdb.cache_session_details()
    # get the rbac details for this account
    if context.bigdb.cached_session_cookie == None:
        return False
    if not 'admin' in context.bigdb.cached_user_groups:
        return False

#
# --------------------------------------------------------------------------------

def bigdb_running_config(context, path = None, detail = None):
    if context.bigdb_run_config == None:
        context.bigdb_run_config = BigDB_run_config(context,
                                                    context.bigdb)
    if path == None:
        return

    return context.bigdb_run_config.generate(path, rc_scoreboard, detail)

#
# --------------------------------------------------------------------------------

def register_running_config(name, order, feature, running_config_proc, command_tuple = None):
    """
    Register a callback to manage the display of component running configs

    @feature a predicate to call, returns True/False to enable/disable this entry
    """
    global bigsh

    running_config_registry[name] = { 'order'   : order,
                                      'feature' : feature,
                                      'proc'    : running_config_proc,
                                      'choice'  : command_tuple }

#
# --------------------------------------------------------------------------------

def align_running_config_command_choices(context):
    global running_config_command_choices
    global running_config_command_choices_registry
    global bigsh

    for (name, value) in running_config_registry.items():
        command_tuple = value['choice']
        if running_config_registry[name]['feature'] == None or \
            running_config_registry[name]['feature'](context) == True:
                # add it if its not already there.
                if not name in running_config_command_choices_registry:
                    running_config_command_choices['choices'] += command_tuple
                    running_config_command_choices_registry[name] = \
                        len(running_config_command_choices['choices']) - 1
        else:
            if name in running_config_command_choices_registry:
                delete_index = running_config_command_choices_registry[name]
                # tuples are immutable, build a new one without this item
                running_config_command_choices['choices'] = \
                    tuple([x for (i, x) in
                           enumerate(running_config_command_choices['choices'])
                           if i != delete_index])
                del running_config_command_choices_registry[name]
                for (n,v) in running_config_command_choices_registry.items():
                    if v > delete_index:
                        running_config_command_choices_registry[n] = v - 1
                

#
# --------------------------------------------------------------------------------

def registry_items_enabled(context):
    """
    Return a list of active running config entries, this is a subset of
    the registered items, only items which are currently enabled via features
    """
    return [name for name in running_config_registry.keys()
            if running_config_registry[name]['feature'] == None or
               running_config_registry[name]['feature'](context) == True]


#
# --------------------------------------------------------------------------------

def perform_running_config(name, context, scoreboard, data, with_errors = None):
    """
    Callout to append to config
    """
    global rc_scoreboard
    rc_scoreboard = scoreboard
    if scoreboard == None:
        rc_scoreboard = BigDB_RC_scoreboard(with_errors = with_errors)
        context.rc_scoreboard = rc_scoreboard

    if name in running_config_registry:
        try:
            running_config_registry[name]['proc'](context, rc_scoreboard, data)
        except urllib2.HTTPError, e:
            if context.description:
                print 'perform_running_config: rest error', e
            rc_scoreboard.add_error(e.code)


#
# --------------------------------------------------------------------------------

def running_config_submode_command_builder(command, priority, previous = []):
    # build the same style tuple used by the automated rc generator
    # see bigdb.submode_enter_commands; last to see its constrution
    new_sc_command = [(
                        command, 
                        None,         # submoe
                        None,         # command-name (command['self'])
                        None,         # command associated field name:values
                        priority,     # prioriy
                     )]
    if previous:
        return previous + new_sc_command
    return new_sc_command

#
# --------------------------------------------------------------------------------

def implement_show_running_config(context, data):
    """
    Manager for the 'show running-config' command, which calls the
    specific detail functions for any of the parameters.
    """

    # use the bigdb scoreboard to collect the running-config.
    global rc_scoreboard
    rc_scoreboard = BigDB_RC_scoreboard()
    context.rc_scoreboard = rc_scoreboard

    if len(data):
        # pick the word
        word = data['running-config']
        choice = utif.full_word_from_choices(word, registry_items_enabled(context))
        if choice: 
            perform_running_config(choice, context, rc_scoreboard, data)
        else:
            yield context.error_msg("unknown running-config item: %s" % word)
            return
        # config[:-1] removes the last trailing newline

        # display any erros associated with the generation of the running config
        for item in rc_scoreboard.generate_errors():
            print 'Warning; \n' + item
            yield '!\n'
            yield '! Warning: \n' +  item
    else:
        # Create the order based on the registration value
        running_config_order = sorted(registry_items_enabled(context),
                                      key=lambda item: running_config_registry[item]['order'])

        # Since the scoreboard defines the ouutput order, the
        # running-config generators can be run in any order.
        for rc in running_config_order:
            perform_running_config(rc, context, rc_scoreboard, data)

        # display any erros associated with the generation of the running config
        for item in rc_scoreboard.generate_errors():
            print 'Warning: \n' + item
            yield '!\n'
            yield '! Warning: \n' +  item

        yield "!\n"
        yield "! %s" % 'NEED VERSION STRING\n' # context.do_show_version
        yield "! Current Time: %s\n" % \
                datetime.datetime.now().strftime("%Y-%m-%d.%H:%M:%S %Z")
        yield "!\n"
        yield "version 1.0\n" # need a better determination of command syntax version

    for item in rc_scoreboard.generate():
        yield item

    rc_scoreboard = None
    context.rc_scoreboard = None

