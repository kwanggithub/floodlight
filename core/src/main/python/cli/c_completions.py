#
# Copyright (c) 2011,2012 Big Switch Networks, Inc.
# All rights reserved.
#

import os
import re
import error
import debug
import command
import collections
import utif

def check_rest_result(result, message=None):
    if isinstance(result, collections.Mapping):
        error_type = result.get('error_type')
        if error_type:
            raise error.CommandRestError(result, message)


def pretty(text):
    """
    For object-type's, remove dashes, capitalize first character
    """
    return text.replace('-', ' ').capitalize()


#
# COMPLETION PROCS
#
# 'completions' is a dictionary, where the keys are the actual text
# of the completion, while the value is the reason why this text 
# was added.  The 'reason' provides the text for the two-column
# help printed for the '?' character.
#

def complete_object_field(field, data, completions,
                          path = None,
                          other_path = None,
                          mode = None,
                          parent_field = None,
                          parent_id = None,
                          prefix = None,
                          no_command = None,
                          scoped = None):
    """
    Populate 'completions' with  the values of the primary key for
    the particular path
    """
    if debug.description():
        print "complete_object_field: ", path, other_path, mode, field, data, scoped

    if path == None:
        top = bigsh.mode_stack[-1]
        path = top.get('path')
        if path == None:
            raise error.CommandDescriptionError("path required")
        
    filter = {}
    if scoped:
        filter = dict(data) # completion version of data
        bigsh.bigdb.add_mode_stack_paths(filter)

    if no_command:
        bigsh.bigdb.completions(completions, path, field, prefix, filter)
    else:
        # Include complete-config-field as a second completion,
        # it will remove items which are already configured from the
        # other choices which currently exist.
        deletions = {}
        bigsh.bigdb.completions(deletions, path, field, prefix, filter)
        if debug.description():
            print 'complete_object_field: removing ', deletions
        for delete in deletions:
            if delete in completions:
                del completions[delete]



def complete_from_another(field, data, completions, no_command,
                          path = None,
                          other_path = None,
                          prefix = None,
                          parent_field = None,
                          parent_id = None,
                          scoped = None,
                          mode_switch = None,
                          explicit=None):
    """
    Completion function used when another path is used to populate
    values for the current path

    the 'other-path' field identifies the path to use to collect choices from,
    it can consist of two parts  other|field.  When field isn't described here,
    it comes from the description parameter, however, the 'field' value there may
    be in use to describe the value of the associated action.

    """
    if debug.description():
        print "complete_from_another:", path, other_path, field, data, parent_field, parent_id

    # complete_from_another is intended to include other fields, which
    # shouldn't apply for a no command.
    if no_command:
        return

    bigdb = bigsh.bigdb
    filter = data if scoped else {}
    # XXX scoped <- need data from submode stack.
    if other_path.find('|') >= 0:
        parts = other_path.split('|')
        other_path = parts[0]
        field = parts[1]
    bigdb.completions(completions, other_path, field,
                      prefix, filter, mode_switch = mode_switch)


def complete_alias_choice(field, data, prefix, completions, no_command,
                          path = None, other_path = None, scoped = None):
    """
    Complete selections from an external object (unlreated to this
    object stack's details), only returning unique keys, either
    aliases for the path, or primary keys.

    This ought to be improved, objects_starting_with() in
    the bigcli.py, is primarily intended to be use within bigcli.py
    """
    if debug.description():
        print "complete_alias_choice:", path, other_path, field, data, prefix, scoped

    if path == None:
        raise error.CommandDescriptionError("path requrired")

    filter = data if scoped else {}
    # XXX scoped <- need data from submode stack.
    bigdb_path = other_path if other_path else path
    bigsh.bigdb.completions(completions, bigdb_path, field, prefix, filter)


def complete_config(prefix, data, completions, copy = False):
    """
    Complete selections for the 'copy' command.
    """

    configs = bigsh.store.get_user_data_table('', "latest")

    # exclude source if its in the data
    source = data.get('source','')
    src_dst = 'source' if source == '' else 'destination'

    any = False
    any_config = False

    if copy:
        if 'running-config'.startswith(prefix):
            if source != 'running-config':
                completions['running-config '] = 'running-config %s' % src_dst

    for c in configs:
        if ('config://' + c['name']).startswith(prefix):
            if source != "config://" + c['name']:
                completions["config://" + c['name'] + ' '] = \
                    'Saved Configuration %s' % src_dst
            any_config = True

    if source != '' and 'config://'.startswith(prefix):
        completions['config://'] = 'config prefix %s' % src_dst

    if copy:
        for additions in ["http://", "file://", "ftp://", "tftp://", 'config://' ]:
            if additions.startswith(prefix):
                completions[additions] = 'other %s' % src_dst


def complete_config_file(prefix, data, completions):
    """
    Complete selections for the 'show config-file' command.
    """

    base_dir = command.bigsh.saved_configs_dirname
    for fn in  [x for x in os.listdir(base_dir) if x.startswith(prefix)]:
        if not os.path.isdir(os.path.join(base_dir, fn)):
            completions[fn] = 'config-file %s' % fn


def complete_interface_list(prefix, data, completions):
    """
    Interface lists are comma separated interfaces or range
    of interfaces.  

    The prefix here plays an important role in determining what
    ought to appear nest.
    """
    if not 'switch' in data:
        return

    def switch_interfaces_startingwith(interfaces, intf, prefix, completions):
        result = [prefix + x for x in interfaces.keys() if x.startswith(intf)]
        completions.update(dict([[x, "known interface"] for x in result]))
        return

    def higher_interfaces(interfaces, intf, prefix, completions):
        # depend on having an integer as the last component
        last_digits = re.compile(r'(.*)(\d+)$')
        match = last_digits.search(intf)
        if match: 
            if_name = match.group(1)
            first = int(match.group(2))
            for i in interfaces:
                match = last_digits.search(i)
                if match and match.group(1) == if_name and int(match.group(2)) > first:
                    completions[prefix + match.group(2)] = 'inteface choice.'

 
    ports = rest_to_model.get_model_from_url('interfaces', data)
    interfaces = dict([[x['name'], x] for x in ports])
    sic = bigsh.get_table_from_store('switch-interface-config',
                                     'switch', data['switch'])
    interfaces.update(dict([[x['name'], x] for x in sic]))

    # peek at the last character in the prefix:
    #  if it's a dash, then choose interfaces with the same prefix,
    #  if its a comma, then chose another interface
    
    front_item = ''
    if len(prefix) > 0:
        if prefix[-1] == '-':
            # complete more choices
            previous = prefix[:-1]
            if len(previous):
                last_item = previous.split(',')[-1]
                if last_item in interfaces:
                    higher_interfaces(interfaces, last_item, prefix, completions)
            return
                
        if prefix[-1] != ',':
            if len(prefix) > 2:
                parts = prefix.split(',')
                last_item = parts[-1]
                # see if the last_item of prefix is a known interface.
                if last_item in interfaces:
                    completions[prefix + ',']     = 'List of interfaces'
                    completions[prefix + '-']     = 'Range of interfaces'
                    completions[prefix + ' <cr>'] = 'Current interfaces selection'
                    return
                # see if the last item is a range (intf in front, then a dash)
                c = [y for y in [x for x in interfaces if last_item.startswith(x)]
                                if len(last_item) > len(y) and last_item[len(y)] == '-']
                if len(c):
                    # found interface with a dash afterwards
                    # could actually check that everything after '-' is digits
                    completions[prefix + ',']     = 'List of interfaces'
                    completions[prefix + ' <cr>'] = 'Current interfaces selection'
                    return

                first_items = ''.join(['%s,' % x for x in parts[:-1]])
                switch_interfaces_startingwith(interfaces,
                                               last_item,
                                               first_items,
                                               completions)
                return

            # single token prefix
            switch_interfaces_startingwith(interfaces, prefix, '', completions)
            return
        # last character is a comma
        if len(prefix) == 1:
            return # just a comma

        # crack into parts, see if the last is a range, if so, then
        # the choices are a comma or a <cr>
        parts = prefix.split(',')
        front_item = ','.join(parts[:-1]) + ','
        prefix = parts[-1]
        # fall through

    switch_interfaces_startingwith(interfaces, prefix, front_item, completions)
    return


def complete_staticflow_actions(prefix, data, completions):
    # peek at the last character in the prefix:
    #  if it's a comma, then choose all the possible actions
    #  if its a equal, then display the choices for this option
    
    prefix_parts = []

    actions = {
        'output='            : 'Describe packet forwarding',
        'enqueue='           : 'Enqueue packet',
        'strip-vlan='        : 'Strip Vlan',
        'set-vlan-id='       : 'Set Vlan',
        'set-vlan-priority=' : 'Set Priority',
        'set-src-mac='       : 'Set Src Mac',
        'set-dst-mac='       : 'Set Dst Mac',
        'set-tos-bits='      : 'Set TOS Bits',
        'set-src-ip='        : 'Set IP Src',
        'set-dst-ip='        : 'Set IP Dst',
        'set-src-port='      : 'Set Src IP Port',
        'set-dst-port='      : 'Set dst IP Port',
    }

    action_choices = {
        ('output=', 'all')          : 'Forward to all ports',
        ('output=', 'controller')   : 'Forward to controller',
        ('output=', 'local')        : 'Forward to local',
        ('output=', 'ingress-port') : 'Forward to ingress port',
        ('output=', 'normal')       : 'Forward to ingress port',
        ('output=', 'flood')        : 'Forward, flood ports',
        ('output=', ('<number>', '<number>'))  : 'Forward, to a specific port',

        ('enqueue=', ('<portNumber>.<queueID>', '<portNumber>.<queueID>')) : 'Enqueue to port, queue id',

        ('set-vlan-id=',('<vlan number>','<vlan number>')) : 'Set vlan to <vlan number>',
        
        ('set-vlan-priority=',('<vlan prio>','<vlan prio>')) : 'Set vlan priority to <prio>',

        ('set-tos-bits=',('<number>',)) : 'Set TOS bits',
        ('set-src-mac=',('<src-mac-address>',)) : 'Set src mac address',

        ('set-dst-mac=',('<dst-mac-address>',)) : 'Set dst mac address',
        
        ('set-src-ip=',('<src-ip-address>',)) : 'Set src mac address',
        
        ('set-dst-ip=',('<src-ip-address>',)) : 'Set dst ip address',
    }

    for ps in prefix.split(','):
        ps_parts = ps.split('=')
        if len(ps_parts) == 1 and ps_parts[0] != '':
            # possibly incomplete item before the '='
            for choice in [x for x in actions.keys() if x.startswith(ps_parts[0])]:
                completions[choice] = actions[choice]
            return
        elif len(ps_parts) == 2:
            if len(ps_parts[0]) and len(ps_parts[1]):
                prefix_parts.append((ps_parts[0], ps_parts[1]))
            elif len(ps_parts[0]) and len(ps_parts[1]) == 0:
                prefix_parts.append((ps_parts[0], ))

    if prefix == '' or prefix.endswith(','):
        completions.update(actions)
    elif prefix.endswith('='):
        last = prefix_parts[-1]
        for ((match, next), desc) in action_choices.items():
            if match[:-1] != last[0]:
                continue
            if type(next) == str:
                completions[match + next] = desc
            elif type(next) == tuple:
                completions[(match + next[0], match + next[0])] = desc
            # else?  display error?
    elif len(prefix_parts):
        last = prefix_parts[-1]
        if len(last) == 1:
            pass
        elif len(last) == 2:
            # try to find the left item
            for ((match, next), desc) in action_choices.items():
                if match[:-1] != last[0]:
                    continue
                if type(next) == str and next == last[1]:
                    eol = prefix + ' <cr>'
                    completions[(eol, eol)] = 'Complete Choice'
                    another = prefix + ','
                    completions[(another, another)] = 'Add another action'
                elif type(next) == str and next.startswith(last[1]):
                    base_part = ''.join(prefix.rpartition(',')[:-1])
                    completions[base_part + last[0] + '=' + next] = 'Complete selection'
                elif len(last[1]):
                    # hard to say what choices can be added here,
                    # there are some characters after '=', but none
                    # which match some prefix.
                    pass

                # how to match the values?


def complete_integer_comma_ranges(prefix, data, no_command, completions):
    # XXX range, values.
    values_range = (0,4096)
    values = [ 25, 26, 27,  1000, 1001, 1002 ]
    lower = values_range[0]
    upper = values_range[1]

    def validate_integer(parm):
        try:
            prefix_integer = int(parm)
        except ValueError: # invalid syntax, not an integer
            return None
        if prefix_integer < lower or prefix_integer > upper:
            # not in acceptable range
            return None
        return prefix_integer
        
    if no_command:
        # the 'no' command, pick what's currently selected
        pass
    else:
        # not the 'no' command,  return new choices (items NOT currently selected)
        #
        # select groups of values which aren't currently set in the values.
        # values is a list of integers representing selected items.
        # parse the current values of prefix and add these to values.

        # populate the values from the ranges, validate the syntax of each range
        prefix_ranges = prefix.split(',')
        for prefix_range in prefix_ranges[:-1]:
            if len(prefix_range) == 0:
                # prefix parts ended in a ',' or the prefix is ''
                if len(prefix_ranges) > 0:
                    completions[prefix] = 'Bad syntax, no choice between commas'
                continue
            prefix_parts = prefix_range.split('-')
            # since the length of prefix_range isn't zero,
            # prefix_parts has a mininum length of 1.
            if len(prefix_parts) == 1:
                if len(prefix_parts[0]) == 0:
                    # "-", or ",-", both invalid
                    completions[prefix] = 'Bad syntax, missing integer before dash'
                    return
                start_integer = validate_integer(prefix_parts[0])
                if start_integer == None:
                    completions[prefix] = 'Bad syntax, "%s": not an integer' % prefix_parts[0]
                    return
                if not start_integer in values:
                    values.append(start_integer)
            elif len(prefix_parts) == 2:
                if len(prefix_parts[0]) == 0:
                    # "--" or ",-" both invalid
                    completions[prefix] = 'Bad syntax, missing integer before dash'
                    return
                start_integer = validate_integer(prefix_parts[0])
                if start_integer == None:
                    completions[prefix] = 'Bad syntax, "%s": not an integer' % prefix_parts[0]
                    return
                # the length parts[1] may be 0
                if len(prefix_parts[1]) == 0:
                    end_integer = start_integer
                else:
                    end_integer = validate_integer(prefix_parts[1])
                    if end_integer == None:
                        completions[prefix] = 'Bad syntax, "%s": not an integer' % prefix_parts[1]
                        return

                if end_integer < start_integer:
                    completions[prefix] = 'Bad syntax, "%s" not greater than "%s"' % (
                            end_integer, start_integer)
                    return
                for current in range(start_integer, end_integer + 1):
                    if current not in values:
                        values.append(current)
            else:
                # inalid syntax, more than one dash
                return

        # prefix_range is the last item in the collection of values.
        # Use the last character of prefix to determine what to add
        # If its a digit, then more digits could follow, as could a dash, or a comma
        #  and if its a digit, this may be the last digit of the second integer of a range
        # If it's a comma, then add all possible unused ranges,
        # If its a dash, then add the end of the unset range,
        # if the dash represents a set value, then don't provide a choice
        # use the collection of unset values to select completions
        last_prefix_range = prefix_ranges[-1]
        if last_prefix_range == '' or last_prefix_range[-1] == ',':
            # add all range
            current = lower
            while current <= upper:
                if not current in values:
                    # begin collecting a range of values
                    end = current + 1
                    if end in values:
                        completions[prefix + "%s"] = 'Single unset value'
                    else:
                        end += 1
                        while (not end in values) and (end <= upper):
                            end += 1
                        unset_range = '%s-%s' % (current, end - 1)
                        completions[prefix + unset_range] = (
                                'Currently unset range: %s' % unset_range)
                        while (end in values) and (end <= upper):
                            end += 1
                    current = end + 1
                else:
                    current += 1
        elif last_prefix_range[-1] in '0123456789':
            last_prefix_ranges = last_prefix_range.split('-')
            if len(last_prefix_ranges) == 1:
                lower_range = lower
                start_integer = validate_integer(last_prefix_range)
                if start_integer == None:
                    completions[prefix] = 'Bad Syntax ("%s": not integer)' % last_prefix_range
                    return
                if not start_integer in values:
                    completions[prefix + '-'] = 'Complete range'
                    completions[prefix + ','] = 'Add more ranges'
                base_prefix = ''.join(['%s,' %  x for x in prefix_ranges[:-1]])
                last_prefix_integer = last_prefix_range
            elif len(last_prefix_ranges) == 2:
                last_prefix_integer = last_prefix_ranges[1]
                end_integer = validate_integer(last_prefix_integer)
                if end_integer == None:
                    return
                if end_integer in values:
                    completions[prefix + ','] = 'Value %s already set' % end_integer
                    return

                lower_range = validate_integer(last_prefix_ranges[0])
                if lower_range == None:
                    completions[prefix] = 'Bad Syntax ("%s": lower range)' % last_prefix_ranges[0]
                    return

                completions[prefix + ','] = 'End of Range, add additional range'
                completions[prefix] = 'End of Range'

                base_prefix = ''.join(['%s,' %  x for x in prefix_ranges[:-1]])
                base_prefix += last_prefix_ranges[0] + '-'
            else:
                completions[prefix] = 'Bad Syntax (too many dashes "-" for range)'
                return

            many = len(last_prefix_integer) + 1
            # first find all the possible completion digits which are unset
            # try not to create thousands of choices, when there's too many then
            # provide ambiguous unselectable entries.
            proposed = {}
            short_proposed = {}
            for current in range(lower_range, upper + 1):
                curr_str = str(current)
                if not current in values and curr_str.startswith(last_prefix_integer):
                    if len(curr_str) >= many:
                        short_proposed[base_prefix + curr_str[:many]] = 'More digits from unselected choices'
                        short_proposed[base_prefix + curr_str[:many] + ' ...'] = 'More digits from unselected choices'
                        proposed[base_prefix + curr_str] = 'More digits from unselected choices'
            if len(proposed) <= 20: # arbitrary number of choices
                completions.update(proposed)
            else:
                completions.update(short_proposed)
            
        elif last_prefix_range[-1] == '-':
            start_integer = validate_integer(last_prefix_range[:-1])
            if start_integer == None:
                completions[prefix] = 'Bad Syntax ("%s": not integer)' % last_prefix_range[:-1]
                return
            if start_integer in values:
                completions[prefix] = 'Bad Syntax "%s": beginning range already set' % (
                                                start_integer)
                return
            end_integer = start_integer + 1
            while (not end_integer in values) and (end_integer <= upper):
                end_integer += 1
            if end_integer < upper:
                completions[prefix + str(end_integer - 1) + ','] = 'End of range, add additional range'
            completions[prefix + str(end_integer - 1)] = 'End of Range (last range)'
        else:
            completions[prefix] = 'Bad Syntax: "%s" (not an integer)' % prefix


def complete_description_versions(prefix, completions):
    for element in os.listdir(bigsh.command_packages_path()):
        if element == '__init__.py':
            pass
        elif element.startswith('version'):
            # len('element') -> 7
            version = "%2.2f" % (float(element[7:]) / 100)
            if version[-2:] == '00':
                version = version[:2] + '0'
            if version.startswith(prefix):
                completions[version] = 'VERSION'
            if version == '2.0':    # currently if 2.0 exists, so does 1.0
                if '1.0'.startswith(prefix):
                    completions['1.0'] = 'VERSION'
        else:
            # experimental
            if bigsh.feature_enabled("experimental") and element.startswith(prefix):
                completions[element] = 'VERSION'


def complete_log_names(prefix, data, completions):
    """
    Enumerate all the log file choices based on replies from the REST API.
    """
    controller = data.get('controller')
    for (c_id, ip_port) in controller_rest_api_ips(controller).items():
        url = log_url(ip_and_port = ip_port)
        log_names = command.bigsh.rest_simple_request_to_dict(url)
        for log in log_names:
            log_name = log['log']
            if log_name.startswith(prefix):
                completions[log_name + ' '] = 'Controller %s (%s) Log Selection' % (c_id, ip_port)



def init_completions(bs):
    global bigsh
    bigsh = bs

    command.add_completion('complete-object-field', complete_object_field,
                           {'kwargs': {'path'         : '$path',
                                       'parent_field' : '$parent-field',
                                       'parent_id'    : '$current-mode-obj-id',
                                       'field'        : '$field',
                                       'prefix'       : '$text',
                                       'data'         : '$data',
                                       'scoped'       : '$scoped',
                                       'other'        : '$other',
                                       'mode'         : '$mode',
                                       'no_command'   : True, # add current
                                       'completions'  : '$completions'}})

    command.add_completion('complete-config-field', complete_object_field,
                           {'kwargs': {'path'         : '$path',
                                       'parent_field' : '$parent-field',
                                       'parent_id'    : '$current-mode-obj-id',
                                       'field'        : '$field',
                                       'prefix'       : '$text',
                                       'data'         : '$data',
                                       'scoped'       : '$scoped',
                                       'other'        : '$other',
                                       'mode'         : '$mode',
                                       'no_command'   : '$is-no-command',
                                       'completions'  : '$completions'}})

    command.add_completion('complete-from-another', complete_from_another,
                           {'kwargs': {'other_path'   : '$other-path',
                                       'path'         : '$path',
                                       'parent_field' : '$parent-field',
                                       'parent_id'    : '$current-mode-obj-id',
                                       'field'        : '$field',
                                       'prefix'       : '$text',
                                       'data'         : '$data',
                                       'scoped'       : '$scoped',
                                       'completions'  : '$completions',
                                       'no_command'   : '$is-no-command',
                                       'explicit'     : '$explicit',
                                       'mode_switch'  : '$mode-switch-clue',
                                       }})

    command.add_completion('complete-alias-choice', complete_alias_choice,
                           {'kwargs': {'path'        : '$path',
                                       'field'       : '$field',
                                       'other'       : '$other',
                                       'other_path'  : '$other-path',
                                       'prefix'      : '$text',
                                       'data'        : '$data',
                                       'scoped'      : '$scoped',
                                       'completions' : '$completions',
                                       'no_command'  : '$is-no-command', }})

    command.add_completion('complete-config', complete_config,
                           {'kwargs': {'prefix'      : '$text',
                                       'data'        : '$data',
                                       'completions' : '$completions'}})

    command.add_completion('complete-config-file', complete_config_file,
                           {'kwargs': {'prefix'      : '$text',
                                       'data'        : '$data',
                                       'completions' : '$completions'}})

    command.add_completion('complete-config-copy', complete_config,
                           {'kwargs': {'prefix'      : '$text',
                                       'data'        : '$data',
                                       'completions' : '$completions',
                                       'copy'        : True }})

    command.add_completion('complete-interface-list', complete_interface_list,
                           {'kwargs': {'prefix'      : '$text',
                                       'data'        : '$data',
                                       'completions' : '$completions'}})

    command.add_completion('complete-staticflow-actions', complete_staticflow_actions,
                           {'kwargs': {'prefix'        : '$text',
                                       'data'        : '$data',
                                       'completions' : '$completions'}})

    command.add_completion('complete-integer-comma-ranges', complete_integer_comma_ranges,
                           {'kwargs': {'prefix'      : '$text',
                                       'data'        : '$data',
                                       'no_command'  : '$is-no-command',
                                       'completions' : '$completions'}})
                        
    command.add_completion('description-versions', complete_description_versions,
                           {'kwargs': {'prefix'      : '$text',
                                       'completions' : '$completions'}})

    command.add_completion('complete-log-names', complete_log_names,
                           {'kwargs': {'prefix'     : '$text',
                                       'data'       : '$data',
                                       'completions': '$completions'}})
