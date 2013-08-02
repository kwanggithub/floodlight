import command
 

def host_running_config_id_to_fields(context, path, results):
    """
    Used during automated running config generation to change
    the results value from bigdb into items which the submode
    command can consume.
    """
    bigdb = context.bigdb

    def missing(results):
        """
        Assume any one of <mac>, <entity-class-name>, or <vlan> indicates
        that a query isn't needed.
        """
        if 'mac' in results:
            return False
        if 'entity-class-name' in results:
            return False
        if 'vlan' in results:
            return False
        return True

    if path == 'core/device' and 'vlan' in results:
        vlan = results['vlan']
        if type(vlan) == list:
            if len(vlan) > 0 and vlan[0] == -1: 
                del results['vlan']
            elif len(vlan) > 1:
                print 'host_running_config_id_to_fields: vlan len > 1', vlan
            else:
                results['vlan'] = vlan[0]

    if path == 'core/device' and 'id' in results and missing(results):
        # transform using the oracle.
        id = results['id']
        (schema, result) = bigdb.schema_and_result('core/device-oracle',
                                                   { 'id': id } )
        oracle_result = result.expect_single_result()
        if oracle_result == None:
            return
        if 'vlan' in results:
            if type(results['vlan']) == list:
                if len(results['vlan']) > 0:
                    if len(results['vlan']) > 1:
                        print 'Device %s with multiple vlans %s' % (
                               results['id'], results['vlan'])
                    results['vlan'] = results['vlan'][0]
                    if results['vlan'] == -1: # BSC-3166
                        del results['vlan']
                else:
                    del results['vlan']


HOST_SUBMODE_COMMAND_DESCRIPTION = {
    'name'                : 'host',
    'path'                : 'core/device',
    'mode'                : 'config*',
    'feature'             : 'bvs', 
    'command-type'        : 'config-submode',
    'submode-name'        : 'config-host',
    'short-help'          : 'Host submode, configure host details',
    'doc'                 : 'host|host',
    'doc-example'         : 'host|host-example',
    'running-config'      : {
                                'id-to-fields' : host_running_config_id_to_fields,
                            },
    'data'                : {
                                'entity-class-name' : 'default',
                            },
    'action'              : (
                                {
                                    'proc' : 'device-compute-device-id',
                                },
                                {
                                    'proc' : 'push-mode-stack',
                                },
                            ),
    'args'                : (
         {
            'field'           : 'entity-class-name',
            'tag'             : 'address-space',
            'type'            : 'string',
            'optional'        : True,
            'optional-for-no' : True,
            'completion'      : 'complete-from-another',
            'other'           : 'address-space',
            'doc'             : 'host|host-address-space',
         },
         {
            'field'           : 'vlan',
            'tag'             : 'vlan',
            'type'            : 'integer',
            'range'           : (0,4095),
            'optional'        : True,
            'optional-for-no' : True,
            'doc'             : 'host|host-vlan',
         },
         {
             'field'          : 'mac',
             'type'           : 'host',
             'completion'     : [ 'complete-alias-choice',
                                ],
             'other'          : 'host',
             'scoped'         : True,
             'data-handler'   : 'alias-to-value',
         }
    )
}

SHOW_HOST_ENTITIES_COMMAND_DESCRIPTION = {
    'name'         : 'show',
    'mode'         : 'login',
    'feature'      : 'bvs', 
    'short-help'   : 'Show host summaries',
    'doc'          : 'host|show',
    'doc-example'  : 'host|show-example',
    'no-supported' : False,
    'command-type' : 'show',
    'args' : (
         {
            'token'  : 'host-entities',
            'action' : {
                'proc'     : 'show',
                'path'     : 'core/device/entity',
                'data'     : { 'id' : "64656661756C74-02-06C1ECE792A8-FFFFFFFF" },
                'style'    : 'table',
            },
         }
    )
}

def host_device_oracle_request(id):
    # use the device oracle to populate the other fields
    bigdb = command.bigsh.bigdb
    (schema, result) = bigdb.schema_and_result('core/device-oracle',
                                               { 'id': id } )
    return result.builder()[0]


def host_compose():
    """
    Currently the composer will create several tables for hosts,
    since the leaf-list entries for attachment-point, vlan, and ip-address
    get created as separate tables.   Those tables are "pulled-up" into
    the host table, with a limited number of entries,  although there is
    only intended to be one attachment point and vlan, multiple ip-address
    are not all that unusual.
    """
    bigdb_show = command.bigdb_show
    if len(bigdb_show.tables) == 0:
        return
    if not 'device' in bigdb_show.tables:
        return
    # index the host.
    host_invert = dict([[x['id'], x] for x in bigdb_show.tables['device']])
    # add aliases, since the other tables will refer to the host with the host alias
    for row in bigdb_show.tables['device']:
        alias = row.get('alias')
        if alias:
            host_invert[alias] = row
    del_idx = []

    for (name, value) in bigdb_show.tables.items():
        if name == 'device':
            for row in value:
                if not 'mac' in row:
                    result = host_device_oracle_request(row['id'])
                    row.update(result)
                if 'vlan' in row:
                    if type(row['vlan']) == list:
                        if len(row['vlan']) > 0:
                            if len(row['vlan']) > 1: # odd device-manager implementation detail
                                print 'Device %s with multiple vlans' % (
                                       row['id'], row['vlan'])
                            row['vlan'] = row['vlan'][0]
                        if row['vlan'] == -1: # BSC-3166
                            del row['vlan']
                    else:
                        del row['vlan']
 
        elif name == 'attachment-point':
            for item in value:
                device_id = item.get('id')
                if device_id == None:
                    continue
                dpid = item.get('dpid')
                if_name = item.get('interface-name')
                if not 'attachment-point' in host_invert[device_id]:
                    host_invert[device_id]['attachment-point'] = []
                new_ap = '%s/%s' % (dpid, if_name)
                host_invert[device_id]['attachment-point'].append(new_ap)
                ipv4 = item.get('ip-address')
                if ipv4:
                    if not 'ip-address' in host_invert[device_id]:
                        host_invert[device_id]['ip-address'] = []
                    host_invert[device_id]['ip-address'].append(ipv4)
                mac = item.get('mac')
                if mac:
                    if not 'mac' in host_invert[device_id]:
                        host_invert[device_id]['mac'] = []
                    host_invert[device_id]['mac'] = mac
            del_idx.append(name)
                
        else:
            # pull any fields into the host.
            for item in value:
                device_id = item.get('id')
                if device_id == None:
                    continue
                if 'vlan' in item:
                    vlan = item['vlan']
                    if not 'vlan' in host_invert[device_id]:
                        host_invert[device_id]['vlan'] = []
                    if vlan == -1:
                        continue
                    host_invert[device_id]['vlan'].append(str(item['vlan']))
                if 'ip-address' in item:
                    if not 'ip-address' in host_invert[device_id]:
                        host_invert[device_id]['ip-address'] = []
                    host_invert[device_id]['ip-address'].append(item['ip-address'])
            del_idx.append(name)

    # clean up list items in the 'device' table.
    for row in command.bigdb_show.tables['device']:
        if 'vlan' in row:
            vlans = row['vlan']
            if len(vlans) > 2:
                row['vlan'] = vlans[0] + ',' + vlans[1] + '+(%s)' % (len(vlans) - 2)
            else:
                row['vlan'] = ','.join(vlans)
        if 'ip-address' in row:
            ips = row['ip-address']
            if len(ips) > 2:
                row['ip-address'] = ips[0] + ',' + ips[1] + '+(%s)' % (len(ips) - 2)
            else:
                row['ip-address'] = ','.join(ips)
        if 'attachment-point' in row:
            aps = row['attachment-point']
            row['attachment-point'] = aps[0]
            if len(aps) > 1:
                row['attachment-point'] += '+(%s)' % (len(aps) - 1)

    # remove unused tables from table_names
    bigdb_show.remove_tables(del_idx)


command.add_action('host-compose',  host_compose, )

# use the defined column header from a specific attribute
def column_header(path, default = None):
    return command.column_header(path, default)


host_show_mac_action = (
    {
        'proc'     : 'show-compose',
        'path'     : 'core/device',
        'style'    : 'table',
        'label'    : 'host',
    },
    {
        'proc'     : 'host-compose',
    },
    # {   # add any associated tags
        # 'proc'       : 'join-table',
        # 'obj-type'   : 'tag-mapping',
        # 'key'        : 'mac',
        # 'key-value'  : 'tag', # causes list creation for multiple matches
        # 'join-field' : 'mac',
        # 'add-field'  : 'tag',
    # },
    #{
        #'proc'       : 'display',
        #'format'     : 'host',
    #},
    {
        'proc'     : 'show-print',
        'style'    : 'table',
        'format'   : {
                        'device' : {
                                    'default' : [
                                                    '#',
                                                    # 'id',
                                                    'alias',
                                                    'entity-class-name',
                                                    'mac',
                                                    'vlan',
                                                    'ip-address',
                                                    ('attachment-point', 'Switch/OF Port (Physical Port)'),
                                                    'last-seen',
                                                ],
                                 },
                     },
    },
)

HOST_SHOW_COMMAND_DESCRIPTION = {
    'name'         : 'show',
    'mode'         : 'login',
    'feature'      : 'bvs',
    'short-help'   : 'Show host summaries',
    'doc'          : 'host|show',
    'doc-example'  : 'host|show-example',
    'no-supported' : False,
    'command-type' : 'sho',
    'args' : (
         {
            'token'  : 'host',
            'action' : host_show_mac_action,
         }
    )
}

HOST_SHOW_MAC_COMMAND_DESCRIPTION = {
    'name'         : 'show',
    'mode'         : 'login',
    'feature'      : 'bvs',
    'short-help'   : 'Show host details based on query',
    'doc'          : 'host|show-host',
    'doc-example'  : 'host|show-host-example',
    'no-supported' : False,
    'command-type' : 'show',
    #'obj-type'     : 'host',
    'path'         : 'core/device',
    'action'       : host_show_mac_action,
    'args' : (
        'host',
        {
            'choices' : (
                {
                    'field'        : 'attachment-point/mac',
                    'type'         : 'host',
                    'short-help'   : 'Show the hosts with the given MAC or alias',
                    'help-name'    : 'host mac or alias',
                    'completion'   : 'complete-alias-choice',
                    # 'data-handler' : 'alias-to-value',
                    'doc'          : 'host|show-host-mac',
                },
                {
                    'field'        : 'attachment-point/entity-class',
                    'short-help'   : 'Show the hosts with the given IPv4 address',
                    'tag'          : 'address-space',
                    'type'         : 'identifier',
                    'help-name'    : 'address space',
                    'completion'   : 'complete-object-field',
                    'doc'          : 'host|show-host-address-space',
                },
                {
                    'field'        : 'attachment-point/ip-address',
                    'short-help'   : 'Show the hosts with the given IPv4 address',
                    'tag'          : 'ip-address',
                    'type'         : 'ip-address',
                    'help-name'    : 'ip address',
                    'completion'   : 'complete-object-field',
                    'doc'          : 'host|show-host-ipv4',
                },
                {
                    'field'        : 'attachment-point/dpid',
                    'short-help'   : 'Show the hosts attached to the given switch',
                    'tag'          : 'switch',
                    'type'         : 'dpid',
                    'help-name'    : 'switch dpid or alias',
                    'completion'   : 'complete-object-field',
                    #'completion'   : 'complete-alias-choice',
                    'data-handler' : 'alias-to-value',
                    'doc'          : 'host|show-host-switch',
                },
                {
                   'field'     : 'host',
                   'short-help': 'Show all hosts',
                   'type'      : 'enum',
                   'values'    : 'all',
                   'doc'       : 'host|show-host-all',
                },
            )
        },
        {
            'optional' : True,
            'choices' : (
                (
                    {
                        'token'      : 'by',
                        'short-help' : 'Sort displayed hosts',
                        'doc'        : 'reserved|by',
                    },
                    {
                        'token'    : 'last-seen',
                        'short-help': 'Sort by the last seen time',
                        'sort'     : '-last-seen',
                        'action'   : 'display-table',
                        'doc'      : 'host|show-host-by-last-seen',
                    },
                ),
            )
        },
        {
            'optional'   : True,
            'field'      : 'detail',
            'type'       : 'enum',
            'short-help' : 'Display either detailed or brief information',
            'values'     : ('details','brief'),
            'doc'        : 'format|+',
        },
    )
}


def attachment_point_compose():
    """
    Device id is ugly.
    """
    bigdb_show = command.bigdb_show
    if len(bigdb_show.tables) == 0:
        return
    if not 'device' in bigdb_show.tables:
        return
    # index the host.
    host_invert = dict([[x['id'], x] for x in bigdb_show.tables['device']])
    del_idx = []

    # pull ip-address/vlan into device
    for (name, value) in bigdb_show.tables.items():
        if name not in ['device', 'attachment-point']:
            for item in value:
                device_id = item.get('device')
                if device_id == None:
                    continue
                if 'vlan' in item:
                    vlan = item['vlan']
                    if not 'vlan' in host_invert[device_id]:
                        host_invert[device_id]['vlan'] = []
                    if vlan == -1:
                        continue
                    host_invert[device_id]['vlan'].append(str(item['vlan']))
                if 'ip-address' in item:
                    if not 'ip-address' in host_invert[device_id]:
                        host_invert[device_id]['ip-address'] = []
                    host_invert[device_id]['ip-address'].append(item['ip-address'])
            del_idx.append(name)

    # leaf-list items ip-address, vlan to strings
    for (host_name, host) in host_invert.items():
        if 'ip-address' in host:
            host['ip-address'] = ', '.join(host['ip-address'])
        if 'vlan' in host:
            host['vlan'] = ', '.join(host['vlan'])

    # decorate attachment points with device items.
    for (name, value) in bigdb_show.tables.items():
        if name == 'attachment-point':
            for item in value:
                if 'device' in item and item['device'] in host_invert:
                    item.update(host_invert[item['device']])
                    del item['device']


command.add_action('attachment-point-compose',  attachment_point_compose, )


HOST_SHOW_MAC_ITEMS_COMMAND_DESCRIPTION = {
    'name'         : 'show',
    'mode'         : 'login',
    'feature'      : 'bvs',
    'short-help'   : 'Show various host related details by query',
    'doc'          : 'host|show-host-items',
    'doc-example'  : 'host|show-host-items-example',
    'no-supported' : False,
    'command-type' : 'show',
    'path'         : 'core/device',
    'args' : (
        'host',
        {
            'choices' : (
                {
                    'field'        : 'attachment-point/mac',
                    'type'         : 'host',
                    'short-help'   : 'Show the hosts with the given MAC or alias',
                    'help-name'    : 'host mac or alias',
                    'completion'   : 'complete-alias-choice',
                    'data-handler' : 'alias-to-value',
                    'doc'          : 'host|show-host-mac',
                },
                {
                    'field'        : 'attachment-point/ip-address',
                    'tag'          : 'ip-address',
                    'short-help'   : 'Show the hosts with the given IPv4 address',
                    'type'         : 'ip-address',
                    'help-name'    : 'ip address',
                    'completion'   : 'complete-object-field',
                    'doc'          : 'host|show-host-ipv4',
                },
                {
                    'field'        : 'attachment-point/dpid',
                    'short-help'   : 'Show the hosts attached to the given switch',
                    'tag'          : 'switch',
                    'type'         : 'dpid',
                    'help-name'    : 'switch dpid or alias',
                    'completion'   : 'complete-object-field',
                    #'completion'   : 'complete-alias-choice',
                    'data-handler' : 'alias-to-value',
                    'doc'          : 'host|show-host-switch',
                },
                {
                   'token'     : 'all',
                   'short-help': 'Show all hosts',
                   'doc'       : 'host|show-host-all',
                },
            )
        },
        {
            'choices' : (
                 (
                     {
                        'token'      : 'attachment-point',
                        'path'       : 'core/device',
                        'action'     : (
                                        {
                                            'proc'  : 'show-compose',
                                            'style' : 'table',
                                        },
                                        {
                                            'proc' : 'attachment-point-compose',
                                        },
                                        {
                                            'proc'   : 'show-print',
                                            'style'  : 'table',
                                            'format' : {
                                                         'attachment-point' : {
                                                            'default' : [
                                                                            '#',
                                                                            ('mac', 'MAC Address'),
                                                                            ('entity-class-name', 'Address Space'),
                                                                            ('ip-address', 'IP Address'),
                                                                            ('vlan','VLAN'),
                                                                            'dpid',
                                                                            'interface-name',
                                                                            'last-seen',
                                                                            ('error-status', 'Error Status (if any)'),
                                                                        ],
                                                         },
                                                       },
                                        },
                                       ),
                        'select'     : [ 'attachment-point' ],
                        'short-help' : 'Show host attachment points',
                        'doc'        : 'host|show-host-item-attachment-point',
                    },
                    {
                        'optional' : True,
                        'choices' : (
                            (
                                {
                                    'token'      : 'by',
                                    'short-help' : 'Sort displayed hosts',
                                    'doc'        : 'reserved|by',
                                },
                                {
                                    'choices' : (
                                        {
                                            'token'      : 'host-last-seen',
                                            'sort'       : 'host,-last-seen',
                                            'obj-type'   : 'host-attachment-point',
                                            'action'     : 'display-table',
                                            'short-help' : 'Sort by the last seen time for the host',
                                            'doc'        : 'host|show-host-by-host-last-seen',
                                        },
                                        {
                                            'token'      : 'last-seen',
                                            'sort'       : '-last-seen',
                                            'obj-type'   : 'host-attachment-point',
                                            'action'     : 'display-table',
                                            'short-help' : 'Sort by the last seen time for the attachment point',
                                            'doc'        : 'host|show-host-by-last-seen',
                                        },
                                    ),
                                },
                            ),
                        ),
                    },
                    {
                        'field'      : 'detail',
                        'type'       : 'enum',
                        'values'     : ('details', 'brief'),
                        'optional'   : True,
                        'short-help' : 'Display either detailed or brief information',
                        'doc'        : 'format|+',
                    }
                 ),
                 (
                    {
                       'field'    : 'network-address',
                       'type'     : 'enum',
                       'values'   : 'ip-address',
                       'obj-type' : 'host-network-address',
                       'action'   : 'display-table',
                       'doc'      : 'host|show-host-item-network-address',
                    },
                    {
                        'optional' : True,
                        'choices' : (
                            (
                                {'token': 'by',
                                 'short-help': 'Sort displayed hosts'
                                 },
                                {
                                    
                                    'choices' : (
                                       {
                                           'token'      : 'host-last-seen',
                                           'sort'       : 'host,-last-seen',
                                           'short-help' : 'Sort by the last seen time for the host',
                                            'obj-type'  : 'host-network-address',
                                            'action'    : 'display-table',
                                            'doc'       : 'host|show-host-by-host-last-seen',
                                       },
                                       {
                                           'token'      : 'last-seen',
                                           'sort'       : '-last-seen',
                                           'short-help' : 'Sort by the last seen time for the network address',
                                           'obj-type'   : 'host-network-address',
                                           'action'     : 'display-table',
                                           'doc'        : 'host|show-host-by-last-seen',
                                       }
                                    )
                                },
                            ),
                        ),
                    },
                    {
                        'field'      : 'detail',
                        'type'       : 'enum',
                        'values'     : ('details', 'brief'),
                        'optional'   : True,
                        'short-help' : 'Display either detailed or brief information',
                        'doc'        : 'format|+'
                    }
                 ),
                 {
                    'field'      : 'alias',
                    'type'       : 'enum',
                    'values'     : 'alias',
                    'obj-type'   : 'host-alias',
                    'action'     : 'display-table',
                    'short-help' : 'Display host alias mappings',
                    'doc'        : 'host|show-host-item-alias',
                 },
            ),
        },
    )
}


HOST_HOST_ALIAS_COMMAND_DESCRIPTION = {
    'name'         : 'host-alias',
    'mode'         : 'config-host',
    'short-help'   : 'Attach alias to host',
    'doc'          : 'host|host-alias',
    'doc-example'  : 'host|host-alias-example',
    'command-type' : 'config',
    'obj-type'     : 'host-alias',
    'scoped'       : True,
    'reserved'     : [ 'switch', 'ip-address', 'address-space' ], 
    'fields'       : [ 'alias' ],
    'args'         : (
        {
            'field'           : 'alias',
            'base-type'       : 'identifier',
            'optional-for-no' : True,
            'completion'      : 'complete-object-field',
        }
    )
}


HOST_SECURITY_POLICY_BIND_IP_ADDRESS_COMMAND_DESCRIPTION = {
    'name'         : 'security',
    'mode'         : 'config-host',
    'short-help'   : 'Configure security policies for host',
    'doc'          : 'host|security',
    'doc-example'  : 'host|security-example',
    'command-type' : 'config-object',
    'path'         : 'core/device/security-ip-address',
    'args'         : (
        {
            'token' : 'policy',
            'doc'   : 'host|security-policy',
        },
        {
            'token' : 'bind',
            'doc'   : 'host|security-bind',
        },
        {
            'field'           : 'security-ip-address',
            'tag'             : 'ip-address',
            'base-type'       : 'ip-address',
            'optional-for-no' : False,
            # could possibly complete all ip-addresses
            'completion'      : 'complete-object-field',
            'action'          : 'write-object',
            'no-action'       : 'delete-objects',
            'short-help'      : 'restrict host access to ip-address',
            'doc'             : 'host|security-ip-address',
        },
    ),
}

HOST_SECURITY_POLICY_BIND_ATTACHMENT_POINT_COMMAND_DESCRIPTION = {
    'name'         : 'security',
    'mode'         : 'config-host',
    'short-help'   : 'Configure security policies for host',
    'doc'          : 'host|security',
    'doc-example'  : 'host|security-example',
    'command-type' : 'config-object',
    'path'         : 'core/device/security-attachment-point',
    'data'         : {
                        'interface-regex' : '',
                        'dpid'            : '',
                     },
    'args'         : (
        {
            'token' : 'policy',
            'doc'   : 'host|security-policy',
        },
        {
            'token' : 'bind',
            'doc'   : 'host|security-bind',
        },
        {
            'token'           : 'attachment-point',
            'short-help'      : 'restrict host access to attachment point',
            'doc'             : 'host|security-attachment-point',
        },
        {
            'choices' :       (
                {
                    'token'           : 'all'
                },
                {
                    'field'           : 'dpid',
                    'type'            : 'dpid',
                    'completion'      : [
                                          'complete-object-field',
                                          'complete-alias-choice',
                                        ],
                    'other-path'      : 'core/switch',
                    'other'           : 'switches|dpid',
                    'help-name'       : 'switch dpid or alias',
                    'data-handler'    : 'alias-to-value',
                    'optional-for-no' : False,
                    'short-help'      : 'identify switch for attachment point',
                    'doc'             : 'host|security-attachment-point-switch',
                },
            ),
        },
        {
            'field'           : 'interface-regex',
            'optional'        : True,
            'optional-for-no' : False,
            'syntax-help'     : 'Regular expression match for interfaces',
            'action'          : 'write-object',
            'no-action'       : 'delete-objects',
            'path'            : 'core/device/security-attachment-point',
            'completion'      : [
                                  'complete-object-field',
                                  'complete-from-another',
                                ],
            'other-path'      : 'core/switch/interface',
            'scoped'          : 'switch',
            'short-help'      : 'identify interface for attachment point',
            'doc'             : 'host|security-attachment-point-interface',
        },
    ),
}


#
# FORMATS
#

import fmtcnv


HOST_FORMAT = {
    'host': {
        'field-orderings' : {
            'default' : [ 'Idx', 'id', 'address-space', 'vlan', 
                          'ips', 'attachment-points', 'tag', 'last-seen' ],
            'details' : [ 'Idx', 'mac', 'address-space', 'vlan', 'host-alias',
                          'vendor', 'ips', 'attachment-points', 'tag',
                          'last-seen' ],
            'brief'   : [ 'Idx', 'mac', 'address-space', 'vlan', 'ips', 'last-seen'],
            },


        'fields': {
            'id'                 : {
                                      'verbose-name': 'MAC Address',
                                      'formatter' : fmtcnv.print_host_and_alias,
                                    },
            'mac'                : {
                                      'verbose-name': 'MAC Address',
                                      'formatter' : fmtcnv.print_host_and_alias,
                                    },
            'address-space'       : {
                                      'verbose-name' : 'Address Space',
                                    },
            'vendor'              : {
                                      'formatter' : fmtcnv.sanitize_unicode,
                                    },
            'vlan'                : {
                                      'verbose-name': 'VLAN',
                                      'formatter' : fmtcnv.convert_to_string,
                                    },
            'ips'                 : {
                                      'verbose-name' : 'IP Address',
                                      'formatter' : fmtcnv.print_ip_addresses,
                                      'entry-formatter' : fmtcnv.print_all_ip_addresses,
                                    },
            'attachment-points'   : {
                                      'verbose-name' : 'Switch/OF Port (Physical Port)',
                                      'formatter' : fmtcnv.print_host_attachment_point,
                                      'entry-formatter' : fmtcnv.print_all_host_attachment_points,
                                    },
            'tag'                 : {
                                      'formatter' : fmtcnv.print_host_tags,
                                      'entry-formatter' : fmtcnv.print_all_host_tags,
                                    },
            'host-alias'          : {
                                    },
            'last-seen'           : {
                                      'verbose-name': 'Last Seen',
                                      'formatter' : fmtcnv.print_time_since_utc,
                                    },
             },
        },
}


HOST_ATTACHMENT_POINT_FORMAT = {
    'host-attachment-point' : {
        'field-orderings' : {
            'default' : [ 'Idx', 'mac', 'vlan', 'address-space', 'switch', 'ingress-port', 'status', ],
            'details' : [ 'Idx', 'mac', 'vlan', 'address-space', 'switch', 'ingress-port', 'status', 'last-seen'],
            },

         'fields': {
            'mac'          : {
                              'verbose-name' : 'MAC Address',
                              'formatter' : fmtcnv.print_host_and_alias,
                             },
            'vlan'         : {
                                'verbose-name': 'VLAN',
                                'formatter' : fmtcnv.convert_to_string,
                             },

            'address-space' : {
                                'verbose-name' : 'Address Space',
                              },
            'switch'       : {
                              'verbose-name' : 'Switch ID',
                              'formatter' : fmtcnv.print_switch_and_alias
                             },
            'ingress-port' : {
                              'verbose-name': 'Port',
                              'formatter' : fmtcnv.decode_openflow_port,
                             },
            'status'       : {
                               'verbose-name': 'Error Status (if any)'
                             },
            'last-seen'    : {
                               'verbose-name': 'Last Seen',
                               'formatter' : fmtcnv.timestamp_to_local_timestr,
                             },
             },
         },
}


HOST_NETWORK_ADDRESS_FORMAT = {
    'host-network-address': {
        'field-orderings' : {
            'default' : [ 'Idx', 'mac', 'address-space', 'vlan', 'ip-address',  ],
            'details' : [ 'Idx', 'mac', 'address-space', 'vlan', 'ip-address', 'last-seen'  ],
            },
        'fields': {
            'mac'        : {
                             'verbose-name': 'MAC Address',
                           },
            'vlan'       : {
                             'verbose-name': 'VLAN',
                           },
            'address-space' : {
                                'verbose-name' : 'Address Space',
                              },
            'id'         : {
                           },
            'ip-address':  {
                             'verbose-name': 'IP Address',
                          },
            'last-seen' : {
                            'verbose-name': 'Last Seen',
                            'formatter' : fmtcnv.timestamp_to_local_timestr,
                          },
            },
        },
}


HOST_ALIAS_FORMAT = {
    'host-alias' : {
        'field-orderings' : {
            'default'     : [ 'Idx', 'id', 'address-space', 'vlan', 'mac' ]
            },
        },
}


HOST_CONFIG_FORMAT = {
    'host-config' : {
        'field-orderings' : {
            'default' : [ 'Idx', 'mac', 'vlan', 'vendor', 'ips',
                          'attachment-points', 'tag', 'last-seen' ],
            'brief'   : [ 'Idx', 'mac', 'vlan', 'ips', 'last-seen'],
            },
        'fields' : {
            'mac'                : {
                                      'verbose-name': 'MAC Address',
                                      'formatter' : fmtcnv.print_host_and_alias,
                                    },
            'vendor'              : {
                                      'formatter' : fmtcnv.sanitize_unicode,
                                    },
            'vlan'                : {
                                      'verbose-name': 'VLAN',
                                      'formatter' : fmtcnv.convert_to_string,
                                    },
            'ips'                 : {
                                      'verbose-name' : 'IP Address',
                                      'formatter' : fmtcnv.print_ip_addresses,
                                      'entry-formatter' : fmtcnv.print_all_ip_addresses,
                                    },
            'attachment-points'   : {
                                      'verbose-name' : 'Switch/OF Port (Physical Port)',
                                      'formatter' : fmtcnv.print_host_attachment_point,
                                      'entry-formatter' : fmtcnv.print_all_host_attachment_points,
                                    },
            'tag'                 : {
                                      'formatter' : fmtcnv.print_host_tags,
                                      'entry-formatter' : fmtcnv.print_all_host_tags,
                                    },
            'host-alias'          : {
                                    },
            'last-seen'           : {
                                      'verbose-name': 'Last Seen',
                                      'formatter' : fmtcnv.print_time_since_utc,
                                    },
        },
    },
}
