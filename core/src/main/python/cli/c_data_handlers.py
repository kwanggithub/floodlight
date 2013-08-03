#
# Copyright (c) 2011,2012 Big Switch Networks, Inc.
# All rights reserved.
#

#
# DATA HANDLERS
#

import re
import time
import datetime
import traceback

import utif
import error
import debug
import command

COMMAND_CIDR_RE = re.compile(r'^((\d{1,3}\.){3}\d{1,3})/(\d{1,2}?)$')


def split_cidr_data_handler(value, data,
                            dest_ip='ip', dest_netmask='netmask', neg = False):
    """
    Split a cidr address (e.g. 192.168.1.1/24) into separate IP address
    and netmask value. The names of the ip and netmask fields are
    specified (typically directly in the same block/dictionary where
    the argument data handler is specifed) with a 'dest-ip' and a
    'dest-netmask' values.
    """
    global bigsh

    m = COMMAND_CIDR_RE.match(value)
    if m:
        bits = int(m.group(3))
        if bits > 32:
            raise error.ArgumentValidationError("max cidr block is 32")

        data[dest_ip] = m.group(1)
        if neg:
            data[dest_netmask] = utif.inet_ntoa(~(0xffffffff << (32 - bits)))
        else:
            data[dest_netmask] = utif.inet_ntoa((0xffffffff << (32 - bits)))


def alias_to_value_handler(value, data, field,
                           path = None, other_path = None):
    """
    Compute the alias value for the named field for the path.
    Place the resulting converted field into the data dictionary.

    Since this is a data-handler, the data dict must be updated
    even if this isn't an alias, otherwise the field value is lost.
    """
    global bigsh
    if debug.description():
        print 'alias_to_value_handler: ', value, path, other_path, data, field

    if path == None and other_path == None:
        raise error.CommandInternalError("alias_to_value_handler path or other-path requred")

    # ought to assert bigsh.bigsb.enabled()
    items = {}
    alias_path = other_path if other_path else path
    alias_path = alias_path.split('|')[0] # other_path may have trailing keys
    for (key, alias) in bigsh.bigdb.alias_key_value(alias_path, field, data):
        if alias == value:
            data[field] = key
            break
    else:
        data[field] = value


def enable_disable_to_boolean_handler(value, data, field):
    if value == 'enable':
        data[field] = True
    if value == 'disable':
        data[field] = False


def date_to_integer_handler(value, data, field):
    if (value == 'now' or value == 'current'):
        data[field] = int(time.time()*1000)

    try:
        data[field] = int(value)
    except:
        pass

    for f,pre in [('%Y-%m-%dT%H:%M:%S', None),
                  ('%Y-%m-%d %H:%M:%S', None),
                  ('%Y-%m-%dT%H:%M:%S%z', None),
                  ('%Y-%m-%d %H:%M:%S%z', None),
                  ('%Y-%m-%d', None),
                  ('%m-%d', '%Y-'),
                  ('%H:%M', '%Y-%m-%dT')]:
        try:
            t = value
            if pre:
                pref = datetime.datetime.now().strftime(pre)
                f = pre + f
                t = pref + t

            thetime = datetime.datetime.strptime(t, f)
            data[field] = int(time.mktime(thetime.timetuple())*1000)
        except:
            pass


HEX_RE = re.compile(r'^0x[0-9a-fA-F]+$')

def hex_to_integer_handler(value, data, field):
    if HEX_RE.match(str(value)):
        _value = str(int(value, 16))
    else:
        _value = str(int(value))
    data[field] = _value


def _invert_netmask(value):
    split_bytes = value.split('.')
    return "%s.%s.%s.%s" % (255-int(split_bytes[0]),
                            255-int(split_bytes[1]),
                            255-int(split_bytes[2]),
                            255-int(split_bytes[3]))


def convert_inverse_netmask_handler(value, data, field):
    data[field] = _invert_netmask(value)


def interface_ranges(names):
    """
    Given a list of interfaces (strings), in any order, with a numeric suffix,
    collect together the prefix components, and create ranges with
    adjacent numeric interfaces, so that a large collection of names
    becomes easier to read.  At the worst, the list will be as
    complicated as the original (which would typically be very unlikely)

    Example: names <- ['Eth0', 'Eth1', 'Eth2',  'Eth4', 'Eth5', 'Eth8']
             result <- ['Eth0-2', 'Eth4-5', 'Eth8']

             names <- ['1','2','3']
             result <- ['1-3']

    """
    # collect the interfaces into dictionaries based on prefixes
    # ahead of groups of digits.
    groups = {}

    def is_digit(c):
        c_ord = ord(c)
        if c_ord >= ord('0') and c_ord <= ord('9'):
            return True
        return False

    for name in names:
        if is_digit(name[-1]):
            for index in range(-2, -len(name)-1, -1):
                if not is_digit(name[index]):
                    index += 1
                    break;
            else:
                index = -len(name)

            prefix = name[:index]
            number = int(name[index:])
            if not prefix in groups:
                groups[prefix] = []
            groups[prefix].append(number)
        else:
            groups[name] = []

    for prefix in groups:
        groups[prefix] = sorted(utif.unique_list_from_list(groups[prefix]))
    
    ranges = []
    for (prefix, value) in groups.items():
        if len(value) == 0:
            ranges.append(prefix)
        else:
            low = value[0]
            prev = low
            for next in value[1:] + [value[-1] + 2]: # +[] flushes last item
                if next > prev + 1:
                    if prev == low:
                        ranges.append('%s%s' % (prefix, low))
                    else:
                        ranges.append('%s%s-%s' % (prefix, low, prev))
                    low = next
                prev = next
 
    return ranges


#print interface_ranges(['1','2','3', 'oe'])
#print interface_ranges(['Eth1','Eth2','Eth3', 'Eth4', 'o5', 'o6'])


def check_missing_interface(switch, interfaces, remedy):
    #
    # The switch value could be a compound key reference to a
    # switch, if there's a '|' in the switch valud, try to guess
    # which entry is the switch

    if type(interfaces) == str or type(interfaces) == unicode:
        interfaces = [interfaces]

    parts = switch.split('|')
    if len(parts) > 1:
        for part in parts:
            if utif.COMMAND_DPID_RE.match(part):
                switch = part
                break
        else:
            switch = part[0]

    bigdb = bigsh.bigdb
    try:
        (schema, result) = \
            bigdb.schema_and_result('core/switch', {'dpid' : switch })

        final_result = result.expect_single_result()
        if final_result == None:
            raise error.ArgumentValidationError("switch not connected")
    except:
        # no switch
        if debug.description() or debug.cli():
            traceback.print_exc()
        bigsh.warning('switch %s currently not active, '
                      'interface %s may not exist' %
                      (switch, ', '.join(interfaces)))
        return

    if not 'interface' in final_result:
        bigsh.warning('switch %s currently not active, '
                      'interface %s may not exist' %
                      (switch, ', '.join(interfaces)))
        return
        
    known_interfaces = [x['name'] for x in final_result['interface']]
    if_names = [x.lower() for x in known_interfaces]
    for interface in interfaces:
        if not interface.lower() in if_names:
            # pre-servce case, try to identify unique ranges
            ranges = interface_ranges(known_interfaces)

            bigsh.warning( 'active switch has no interface "%s", ' 
                      'known: %s' % (interface, ', '.join(ranges)) + 
                      remedy)
            return


def warn_missing_interface(value, data, field, is_no, obj_value, many = False):
    if not is_no:
        bigdb = bigsh.bigdb

        if debug.description() or debug.cli():
            print 'warn_missing_interface:', value, data, field, is_no, \
                                             obj_value, many
        # need switch, if_name 
        pk_data = {}
        bigdb.add_mode_stack_paths(pk_data)
        switch = pk_data.get('switch')
        if switch == None:
            switch = pk_data.get('dpid')
        if switch == None:
            switch = data.get('switch')
        if switch == None:
            switch = data.get('dpid')
        if bigdb.enabled and switch == None:
            for (n,v) in data.items() + pk_data.items():
                if n.find('/') >= 0:
                    parts = n.split('/')
                    for suffix in ['switch', 'switch-dpid', 'switch-id']:
                        if parts[-1] == suffix:
                            switch = v
        if switch == None:
            if debug.description():
                print 'warn_missing_interface:', data
            raise error.ArgumentValidationError("Can't identify switch for validation")
        force = True if data.get('force', '') != '' else False
        # check to see if the interface is a list or a range, 
        ifs = value.split(',')
        range_re = re.compile(r'([A-Za-z0-9-/\.:]*?)(\d+)-(\d+)$')
        if many:
            interfaces = []
            remedy = ''
            for if_name in ifs:
                # check for trailing "-<integer>" which is intended to
                # identify a range, if so split that into multiple entites
                m = range_re.match(if_name)
                if m:
                    print 'TAIL MATCH', m.group(1), m.group(2), m.group(3)
                    for suffix in range(int(m.group(2)), int(m.group(3))+1):
                        interfaces.append('%s%s' % (m.group(1), suffix))
                else:
                    interfaces.append(if_name)
        else:
            remedy = '\nUse \"exit; no interface %s\" to remove' % value
            interfaces = [value]
            
        check_missing_interface(switch, interfaces, remedy)
    data[field] = value

def warn_monitor_service_interface(value, data, field, is_no, obj_value):
    if not is_no:
        # need switch, if_name
        switch = pk_data.get('switch')
        if switch == None:
            switch = pk_data.get('dpid')
        if switch == None:
            switch = data.get('switch')
        if switch == None:
            switch = data.get('dpid')
        interface = obj_value.split('|')[1]
        role = data.get('bigtap-role')
        row = rest_to_model.get_model_from_url('switches', {'dpid' : switch })
        if len(row) == 0 or row[0].get('ip-address', '') == '':
            bigsh.warning('switch %s currently not active, '
                      'interface %s may not exist and bigtap role '
                      '%s may not be applied' % (switch, interface, role))
        else:
            action = row[0]['actions']
            if (action == 0) and (role == 'service'):
                bigsh.warning('Service interface cannot be configured on switch '
                              'in monitor bind mode')
            if (action == 0) and (role == 'delivery'):
                bigsh.warning('Delivery interface configured on switch in monitor '
                              'bind mode. This is valid only if filter interface '
                              'is on same switch and analysis tool is directly '
                              'connected to delivery interface configured as'
                              ' mirror destination port.')
    data[field] = value


def convert_interface_to_port(value, data, field, other = None, scoped = None):
    # look for the switch name in data
    if scoped:
        dpid = data.get(scoped)
    elif 'dpid' in data:
        dpid = data['dpid']
    else:
        dpid = data.get('switch', '') # possibly other choices

    # if its not a specific switch, no conversion is possible
    # should the value be passed through?
    if dpid == '':
        data[field] = value
        return

    ports = rest_to_model.get_model_from_url('interfaces', {'dpid' : dpid})
    for port in ports:
        if port['portName'] == value:
            data[field] = port['portNumber'] # should this be a string?
            break
    else:
        raise error.ArgumentValidationError("Can't find port %s on switch %s" %
                (value, dpid))


def convert_tag_to_parts(value, data, namespace_key, name_key, value_key):
    """
    Split a tag of the form [ns].name=value into the three
    component parts
    """

    if debug.description():
        print "convert_tag_to_parts: %s %s %s %s %s" % (
                value, data, namespace_key, name_key, value_key)

    tag_and_value = value.split('=')
    if len(tag_and_value) != 2:
        raise error.ArgumentValidationError("tag <[tag-namespace.]name>=<value>")

    tag_parts = tag_and_value[0].split('.')
    if len(tag_parts) == 1:
        tag_namespace = "default"
        tag_name = tag_parts[0]
    elif len(tag_parts) >= 2:
        tag_namespace = '.'.join(tag_parts[:-1])
        tag_name = tag_parts[-1]

    # should the names have some specific validation?
    data[namespace_key] = tag_namespace
    data[name_key]      = tag_name
    data[value_key]     = tag_and_value[1]


CICR_RANGE_RE = re.compile(r'^(\d+)\-(\d+)$')
CICR_SINGLE_RE = re.compile(r'^(\d+)$')

def convert_integer_comma_ranges(value, path, field, field_range, data):
    print 'convert_integer_comma_ranges:', value, path, field, field_range, data

    list_of_integers = []
    for r in value.split(','):
        m = CICR_RANGE_RE.match(r);
        if (m):
            lower = int(m.group(1))
            upper = int(m.group(2))

            list_of_integers += range(int(lower), int(upper) + 1)
        else:
            m = CICR_SINGLE_RE.match(r)
            print 'INCH', m.group(1)
            list_of_integers.append(int(m.group(1)))
    print 'NLIST', list_of_integers
    # with the list_of_integers, determine what the value should be.
    bigdb = bigsh.bigdb
    field_path = '%s/%s' % (path, field)
    (schema, item_index) = bigdb.schema_of_path(field_path, {})
    node_type = schema.get('nodeType')
    if node_type == 'LEAF_LIST':
        data[field_path] = list_of_integers
    elif node_type == 'LIST':
        # find the integer to set.
        print schema.keys()
        list_element_node = schema.get('listElementSchemaNode')
        list_children_nodes = list_element_node.get('childNodes')
        # examine all the children for this node. look for a field with
        # the same range.   Assume field_range is a two-tuple of (low, high)
        candidates = []
        for (child_name, child_value) in list_children_nodes.items():
            child_node_type = child_value.get('nodeType')
            child_type_details = child_value.get('typeSchemaNode')
            print 'CV', child_name, child_node_type, child_type_details
            validators = child_type_details.get('typeValidator')
            if validators:
                # look for a range validator.
                print 'TV', validators
                for validator in validators:
                    if validator['type'] == 'RANGE_VALIDATOR':
                        ranges = validator['ranges']
                        for r in ranges:
                            if r.get('start') == field_range[0] and \
                               r.get('end') == field_range[1]:
                                print 'SAME RANGE', child_name
                                candidates.append(child_name)
        if len(candidates) == 1:
            value_name = candidates[0]
            data[field_path] = [{value_name: v} for v in list_of_integers]
    else:
        print 'convert_integer_comma_ranges: no management for ', node_type


def init_data_handlers(bs):
    global bigsh
    bigsh = bs

    command.add_argument_data_handler('split-cidr-data',
                                      split_cidr_data_handler,
                        {'kwargs': {'value'        : '$value',
                                    'data'         : '$data',
                                    'dest_ip'      : '$dest-ip',
                                    'dest_netmask' : '$dest-netmask'}})

    command.add_argument_data_handler('split-cidr-data-inverse',
                                      split_cidr_data_handler,
                        {'kwargs': {'value'        : '$value',
                                    'data'         : '$data',
                                    'dest_ip'      : '$dest-ip',
                                    'dest_netmask' : '$dest-netmask',
                                    'neg'          : True}})

    command.add_argument_data_handler('alias-to-value',
                                      alias_to_value_handler,
                        {'kwargs': {'value'      : '$value',
                                    'data'       : '$data',
                                    'field'      : '$field',
                                    'path'       : '$path',
                                    'other_path' : '$other-path', }})

    command.add_argument_data_handler('enable-disable-to-boolean',
                                      enable_disable_to_boolean_handler,
                        {'kwargs': {'value'  : '$value',
                                    'data'   : '$data',
                                    'field'  : '$field'}})

    command.add_argument_data_handler('date-to-integer',
                                      date_to_integer_handler,
                        {'kwargs': {'value' : '$value',
                                    'data'  : '$data',
                                    'field' : '$field'}})

    command.add_argument_data_handler('hex-to-integer', hex_to_integer_handler,
                        {'kwargs': {'value' : '$value',
                                    'data'  : '$data',
                                    'field' : '$field'}})

    command.add_argument_data_handler('convert-inverse-netmask',
                                      convert_inverse_netmask_handler,
                        {'kwargs': {'value' : '$value',
                                    'data'  : '$data',
                                    'field' : '$field'}})

    command.add_argument_data_handler('warn-missing-interface',
                                      warn_missing_interface,
                        {'kwargs': {'value'     : '$value',
                                    'data'      : '$data',
                                    'field'     : '$field',
                                    'is_no'     : '$is-no-command',
                                    'obj_value' : '$current-mode-obj-id'}})

    command.add_argument_data_handler('warn-monitor-service-interface',
                                       warn_monitor_service_interface,
                        {'kwargs': {'value'     : '$value',
                                    'data'      : '$data',
                                    'field'     : '$field',
                                    'is_no'     : '$is-no-command',
                                    'obj_value' : '$current-mode-obj-id'}})

    command.add_argument_data_handler('convert-interface-to-port',
                                      convert_interface_to_port,
                        {'kwargs': {'value'     : '$value',
                                    'data'      : '$data',
                                    'field'     : '$field',
                                    'other'     : '$other',
                                    'scoped'    : '$scoped'}})

    command.add_argument_data_handler('convert-tag-to-parts',
                                      convert_tag_to_parts,
                        {'kwargs': {'value'          : '$value',
                                    'data'           : '$data',
                                    'namespace_key'  : '$namespace-key',
                                    'name_key'       : '$name-key',
                                    'value_key'      : '$value-key'}})

    command.add_argument_data_handler('convert-integer-comma-ranges',
                                      convert_integer_comma_ranges,
                        {'kwargs': {'value'          : '$value',
                                    'data'           : '$data',
                                    'path'           : '$path',
                                    'field_range'    : '$range',
                                    'field'          : '$field', }})
