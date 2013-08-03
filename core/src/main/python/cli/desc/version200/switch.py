import command
 
SWITCH_SUBMODE_COMMAND_DESCRIPTION = {
    'name'          : 'switch',
    'short-help'    : 'Enter switch submode, configure switch details',
    'mode'          : 'config*',
    'rbac-group'    : 'admin',
    'path'          : 'core/switch',
    'command-type'  : 'config-submode',
    'submode-name'  : 'config-switch',
    'show-this'     : 'show switch %(dpid)s',
    'doc'           : 'switch|switch',
    'item-name'     : 'switch',
    'rc-order'       : 4000000,
    'running-config' : {
                        'data-map' : 'sample',
                       },
    'args' : (
        {
            'field'        : 'dpid',
            'type'         : 'dpid',
            'other-path'   : 'core/switch',
            'completion'   : [
                              'complete-alias-choice',
                              'complete-from-another',
                             ],
            'data-handler' : 'alias-to-value',
            'syntax-help'  : 'Configure a new switch with dpid',
        }
    )
}

# use the defined column header from a specific attribute
def column_header(path, default = None):
    return command.column_header(path, default)

switch_ip_address_header = column_header('core/device/ip-address', 'IP Address')

#
# ------------------------------------------------------------------------------
# show switch
#

show_switch_pipeline = (
    {
        'proc'     : 'show',            # need a better name for this
        'path'     : 'core/switch',
        'style'    : 'table',
        'select'   : 'switch',
        'format'   : {
                       'switch' : {
                                    'default' : [
                                                    '#',
                                                    'dpid',
                                                    'alias',
                                                    'connected-since',
                                                    ('ip', switch_ip_address_header),
                                                    # ('inet-port', 'TCP Port'),
                                                    'core-switch',
                                                ],
                                    'details' : [
                                                    '#',
                                                    'dpid',
                                                    'alias',
                                                    'connected-since',
                                                    ('ip', switch_ip_address_header),
                                                    ('inet-port', 'TCP Port'),
                                                    'core-switch',
                                                ],
                                  },
                        'interface' :  {
                                    'default' : [
                                                    '#',
                                                    'switch',
                                                    'name',
                                                    'number',
                                                    'hardware-address',
                                                    'config-flags',
                                                    'state-flags',
                                                    'advertised-features',
                                                    'current-features',
                                                    'supported-features',
                                                    'peer-features',
                                                ],
                                  },
                     },
    },
)

SWITCH_SHOW_COMMAND_DESCRIPTION = {
    'name'                : 'show',
    'mode'                : 'login',
    'command-type'        : 'show',
    'rbac-group'          : 'admin',
    'all-help'            : 'Show switch information',
    'short-help'          : 'Show switch summary',
    'path'                : 'core/switch',
    'doc'                 : 'switch|show',
    'doc-example'         : 'switch|show-example',
    'args' : (
        {
            'token'  : 'switch',
            'action' : show_switch_pipeline,
            'doc'    : 'switch|show',
        },
    )
}

SWITCH_SHOW_WITH_DPID_COMMAND_DESCRIPTION = {
    'name'                : 'show',
    'mode'                : 'login',
    'rbac-group'          : 'admin',
    'short-help'          : 'Show switch details via query',
    'command-type'        : 'show',
    'path'                : 'core/switch',
    'args' : (
        {
            'token'        : 'switch',
            'action'       : show_switch_pipeline,
        },
        {
            'choices' : (
                {
                    'field'        : 'dpid',
                    'completion'   : 'complete-alias-choice',
                    'type'         : 'dpid',
                    'help-name'    : 'switch dpid or alias',
                    'data-handler' : 'alias-to-value',
                },
                {
                    'token'        : 'all',
                },
            ),
        },
        {
            'optional'   : True,
            'choices' : (
                (
                    {
                        'token' : 'by',
                        'doc'   : 'switch|show-switch-order',
                    },
                    {
                        'choices' : (
                            {
                                'token'      : 'ip-address',
                                'sort'       : { 'switch' : 'ip' },
                                'action'     : show_switch_pipeline,
                                'short-help' : 'Sort by ip-address',
                            },
                            {
                                'token'      : 'connect-time',
                                'sort'       : { 'switch' : '-connected-since' },
                                'action'     : show_switch_pipeline,
                                'short-help' : 'Sort by connect time',
                            },
                        )
                    }
                ),
            )
        },
        {
            'field'      : 'detail',
            'type'       : 'enum',
            'values'     : ('details','brief'),
            'optional'   : True,
            'doc'        : 'switch|show-switch-format-+',
        },
    )
} 

def switch_flow_show_compose(path, filter, data, remove_tables = None):
    """
    """
    # Move first 'action' to the 'flow' table.  If there's only one, remoce the row,
    # if the 'action' table is gone, remove the table.

    bigdb = command.bigsh.bigdb

    if data.get('dpid') == 'all':
        del data['all']

    # poor man's query generation... pick items from data to form the query
    # based on the keys for the path.  No schema here, use bigdb's search_keys
    keys = bigdb.search_keys[path]
    query = {}
    if keys:
        for k in keys:
            if k in data:
                query[k] = data[k]
            else:
                long_name = path + '/' +  k
                if long_name in data:
                    query[long_name] = data[long_name]

    # need support for 'select' included into 'show-compose'
    (schema, result) = bigdb.schema_and_result(path, query, select = filter)
    if schema == None:
        print 'No schama for:', path
        return

    command.action_invoke('show-init', ())

    detail = data.get('detail')
    bigdb_show = command.bigdb_show
    bigdb_show.compose_show(path, schema, result.builder(), 'table', detail)

    # remove the named tables, eg: 'switch', before the flow-table-is-empty test.
    if remove_tables:
        if type(remove_tables) != list: # test for str or unicode instead?
            remove_tables = [remove_tables]
        for remove_table in remove_tables:
            bigdb_show.remove_tables(remove_table)

    if len(bigdb_show.tables) == 0:
        return
    if not 'flow' in bigdb_show.tables or not 'action' in bigdb_show.tables:
        return

    # fix vlan, -1 means none. (bigdb workaround)
    for row in bigdb_show.tables['flow']:
        if row.get('dl-vlan') == '-1':
            row['dl-vlan'] = ''
        
    flow_invert = dict([[x['+flow'], x] for x in bigdb_show.tables['flow']])
    action_index = {}
    actions = {}
    for (index, row) in enumerate(bigdb_show.tables['action']):
        flow = row['+flow']
        if not flow in actions:
            actions[flow] = []
        actions[flow].append(row)
        action_index[flow] = index

    def stringify_action(action):
        non_action_type = ['%s=%s' % (n,v) for (n,v) in action.items()
                            if not n in ['action-type', '+action', '+flow', 'dpid', 'switch']]
        return '%s: %s' % (action['action-type'], ', '.join(non_action_type))
                                                          
    del_items = []
    for (flow, action) in actions.items():
        if len(action) >= 1:
            if len(action) > 1:
                flow_invert[flow]['actions'] = "(see table below, %d entries)" % len(action)
            else:
                flow_invert[flow]['actions'] = stringify_action(action[0])
                del_items.append(action_index[flow])

    # sort from highest to lowest to preserve list indices.
    del_items.sort(reverse = True)
    for item in del_items:
        del bigdb_show.tables['action'][item]
    if len(bigdb_show.tables['action']) == 0:
        bigdb_show.remove_tables('action')


command.add_action('switch-flow-show-compose',  switch_flow_show_compose,
                   {'kwargs' : {'data'          : '$data',
                                'path'          : '$path',
                                'filter'        : '$filter',
                                'remove_tables' : '$remove-tables',
                                } } )


switch_show_print_format = {
    'flow' : {
                'default' : [
                                '#',
                                'dpid',
                                ('packet-count', 'Pkts'),
                                ('byte-count', 'Bytes'),
                                ('sec', 'Dur(s)'),
                                ('cookie', 'Author'),
                                ('priority', 'Pri'),
                                ('table-id', 'T'),
                                ('in-port', 'In Port'),
                                'dl-src',
                                'dl-dst',
                                ('dl-vlan', 'VLAN'),
                                ('dl-vlan-pcp', 'VP'),
                                ('dl-type', 'Ether Type'),
                                ('nw-proto', 'Proto'),
                                ('nw-src', 'Src IP'),
                                ('nw-dst', 'Dst IP'),
                                ('nw-tos', 'TOS'),
                                ('tp-src', 'Src TP'),
                                ('tp-dst', 'Dst TP'),
                                ('actions', 'Actions'),
                            ],
                'details' : [
                                '#',
                                'dpid',
                                ('packet-count', 'Pkts'),
                                ('byte-count', 'Bytes'),
                                ('sec', 'Dur(s)'),
                                ('hard-timeout', 'Hard Timeout'),
                                ('idle-timeout', 'Idle Timeout'),
                                ('cookie', 'Author'),
                                ('priority', 'Pri'),
                                ('table-id', 'T'),
                                ('in-port', 'In Port'),
                                'dl-src',
                                'dl-dst',
                                ('dl-vlan', 'VLAN'),
                                ('dl-vlan-pcp', 'VP'),
                                ('dl-type', 'Ether Type'),
                                ('nw-proto', 'Proto'),
                                ('nw-src', 'Src IP'),
                                ('nw-dst', 'Dst IP'),
                                ('nw-tos', 'TOS'),
                                ('tp-src', 'Src TP'),
                                ('tp-dst', 'Dst TP'),
                                ('actions', 'Actions'),
                            ],
             },
}

SWITCH_SHOW_REALTIME_STATS_COMMAND_DESCRIPTION = {
    'name'                : 'show',
    'mode'                : 'login',
    'short-help'          : 'Show switch stats via direct query to switch',
    'command-type'        : 'show',
    'short-help'          : 'Show realtime stats for switch',
    'rbac-group'          : 'admin',
    'path'                : 'core/switch',
    'action'              : 'show',
    'args'                : (
        {
            'token'        : 'switch',
            # 'command-type' : 'display-rest',
        },
        {
            'choices' : (
                {
                    'field'        : 'dpid',
                    'completion'   : 'complete-alias-choice',
                    'type'         : 'dpid',
                    'help-name'    : 'switch dpid or alias',
                    'data-handler' : 'alias-to-value',
                    'data'         : { 'detail' : 'scoped' },
                },
                {
                    'token'        : 'all',
                    'doc'          : 'reserved|all',
                },
            ),
        },
        {
            'choices' : (
                (
                    {
                        'token'      : 'flow',
                        'action'     : (
                                        {
                                            'proc' : 'show-init',
                                        },
                                        {
                                            'proc' : 'update-flow-cookies',
                                        },
                                        {
                                            'proc'          : 'switch-flow-show-compose',
                                            'path'          : 'core/switch',
                                            'filter'        : 'stats/flow',
                                            'remove-tables' : 'switch',
                                        },
                                        {
                                            'proc'   : 'show-print',
                                            'style'  : 'table',
                                            'format' : switch_show_print_format,
                                        }
                                      ),
                    },
                    {
                        'field'    : 'detail',
                        'optional' : True,
                        'type'     : 'enum',
                        'values'   : ('details','brief'),
                        'doc'      : 'format|+',
                    },
                ),
                (
                    {
                        'token'      : 'aggregate',
                        'action'     : 'show',
                        'path'       : 'core/switch?select=stats/aggregate',
                        'doc'        : 'switch|realtime-aggregate',
                        'short-help' : 'Show agregate details by querying switch',
                    },
                ),
                (
                    {
                        'token'      : 'port',
                        'action'     : 'show',
                        'path'       : 'core/switch?select=stats/interface',
                        'doc'        : 'switch|realtime-port',
                        'select'     : 'interface',
                        'short-help' : 'Show port/interface details by querying switch',
                        'format'     : {
                                        'interface' : {
                                            'default' : [
                                                '#',
                                                'dpid',
                                                'name',
                                                'number',
                                                'transmit-packets',
                                                'transmit-bytes',
                                                'transmit-dropped',
                                                'transmit-errors',
                                                'collisions',
                                                'receive-packets',
                                                'receive-bytes',
                                                'receive-dropped',
                                                'receive-errors',
                                                'receive-frame-errors',
                                                'receive-overrun-errors',
                                            ],
                                        },
                        },
                    },
                ),
                (
                    {
                        'token'      : 'table',
                        'action'     : 'show',
                        'select'     : 'table',
                        'path'       : 'core/switch?select=stats/table',
                        'doc'        : 'switch|realtime-table',
                        'short-help' : 'Show table details by querying switch',
                    },
                ),
                (
                    {
                        'token'      : 'queue',
                        'action'     : 'show',
                        'path'       : 'core/switch?select=stats/queue',
                        'doc'        : 'switch|realtime-queue',
                        'short-help' : 'Show queue length by querying switch',
                    },
                ),
                (
                    {
                        'token'      : 'desc',
                        'action'     : 'show',
                        'path'       : 'core/switch?select=stats/desc',
                        'doc'        : 'switch|realtime-desc',
                        'short-help' : 'Show switch description',
                    },
                ),
                (
                    {
                        'token'      : 'attributes',
                        'action'     : 'show',
                        'path'       : 'core/switch/attributes',
                        'doc'        : 'switch|realtime-features',
                        'short-help' : 'Show description of switch',
                    },
                )
            )
        }
    )
}

SWITCH_SHOW_STATS_COMMAND_DESCRIPTION = {
    'name'                : 'show',
    'mode'                : 'login',
    'rbac-group'          : 'admin',
    'short-help'          : 'Show switch stats',
    'short-help'          : 'show stats for selected switch',
    'command-type'        : 'show',
    'parent-field'        : None,
    # 'obj-type'            : 'switches',
    'path'                : 'core/switch',
    'args'                : (
        {
            'token'        : 'switch',
            # 'command-type' : 'display-rest',
        },
        {
            'field'        : 'dpid',
            'completion'   : 'complete-alias-choice',
            'type'         : 'dpid',
            'help-name'    : 'switch dpid or alias',
            'data-handler' : 'alias-to-value',
        },
        {
            'token'        : 'stats',
            'action'       : 'legacy-cli',
            'obj-type'     : 'switch-stats',
        },
    )
}

SWITCH_SHOW_STATS_OBJECT_DETAILS_COMMAND_DESCRIPTION = {
    'name'                : 'show',
    'mode'                : 'login',
    'rbac-group'          : 'admin',
    'short-help'          : 'Show statistics for a given switch',
    'parent-field'        : None,
    'command-type'        : 'show',
    # 'obj-type'            : 'switches',
    'path'                : 'core/switch',
    'args'                : (
        {
            'token'        : 'switch',
            # 'command-type' : 'display-rest',
        },
        {
            'field'        : 'dpid',
            'completion'   : 'complete-alias-choice',
            'type'         : 'dpid',
            'help-name'    : 'switch dpid or alias',
            'data-handler' : 'alias-to-value',
        },
        {
            'token'        : 'stats',
            'action'       : 'legacy-cli',
            'obj-type'     : 'switch-stats',
        },
        {
            'field'        : 'stats-type',
            'type'         : 'enum',
            'values'       : (
                                'OFActiveFlow',
                                'OFFlowMod',
                                'OFPacketIn',
                             ),
        },
        {
            'field'        : 'start-time',
            'tag'          : 'start-time',
            'type'         : 'date',
            'data-handler' : 'date-to-integer',
            'short-help'   : 'Start time for displaying the stats',
            'optional'     : True,
        },
        {
            'field'        : 'end-time',
            'tag'          : 'end-time',
            'type'         : 'date',
            'data-handler' : 'date-to-integer',
            'short-help'   : 'End time for displaying the stats',
            'optional'     : True,
        },
        {
            'field'        : 'duration',
            'tag'          : 'duration',
            'type'         : 'duration',
            'short-help'   : 'Duration from the start or end for displaying the stats',
            'optional'     : True,
        },
        {
            'field'        : 'sample-interval',
            'tag'          : 'sample-interval',
            'type'         : 'integer',
            'short-help'   : 'Spacing between sampling windows',
            'optional'     : True,
        },
        {
            'field'        : 'sample-count',
            'tag'          : 'sample-count',
            'type'         : 'integer',
            'short-help'   : 'Number of samples in each window',
            'optional'     : True,
        },
        {
            'field'        : 'sample-window',
            'tag'          : 'sample-window',
            'type'         : 'integer',
            'short-help'   : 'Window length for sampling',
            'optional'     : True,
        },
        {
            'field'        : 'data-format',
            'tag'          : 'data-format',
            'type'         : 'enum',
            'values'       : ('value', 'rate',),
            'short-help'   : 'Whether to display as a raw value or rate',
            'optional'     : True,
        },
        {
            'field'        : 'display',
            'tag'          : 'display',
            'type'         : 'enum',
            'values'       : ('latest-value', 'graph', 'table'),
            'short-help'   : 'Display the latest value, a graph, or a table',
            'optional'     : True,
        },
    ),
}


show_switch_specific_interfaces_pipeline = (
    {
        'proc'     : 'show',
        'path'     : 'core/switch/interface',
        'style'    : 'table',
        'format'   : {
                        'interface' :  {
                                    'default' : [
                                                    '#',
                                                    'name',
                                                    'number',
                                                    'hardware-address',
                                                    'config-flags',
                                                    'state-flags',
                                                    'advertised-features',
                                                    'current-features',
                                                    'supported-features',
                                                    'peer-features',
                                                ],
                                  },
                     },
    },
)

show_switch_interfaces_pipeline = (
    {
        'proc'     : 'show',            # need a better name for this
        'path'     : 'core/switch',
        'style'    : 'table',
        'select'   : 'interface',
        'format'   : {
                        'interface' :  {
                                    'default' : [
                                                    '#',
                                                    'dpid',
                                                    'name',
                                                    'number',
                                                    'hardware-address',
                                                    'config-flags',
                                                    'state-flags',
                                                    'advertised-features',
                                                    'current-features',
                                                    'supported-features',
                                                    'peer-features',
                                                ],
                                  },
                     },
    },
)


SWITCH_SHOW_SWITCH_DPID_INTERFACES_COMMAND_DESCRIPTION = {
    'name'                : 'show',
    'mode'                : 'login',
    'rbac-group'          : 'admin',
    'command-type'        : 'show',
    'short-help'          : 'Show interfaces for selected switch',
    'path'                : 'core/switch',
    'args' : (
        {
            'token'        : 'switch',
        },
        {
            'choices' : (
                {
                    'field'        : 'dpid',
                    'completion'   : 'complete-alias-choice',
                    'type'         : 'dpid',
                    'help-name'    : 'switch dpid or alias',
                    'data-handler' : 'alias-to-value',
                },
                {
                    'token'        : 'all',
                    #'field'        : 'dpid',
                    #'type'         : 'enum',
                    #'values'       : 'all',
                    'short-help'   : 'Show interfaces for all switches',
                    'path'         : 'core/switch',
                    'select'       : 'interface',
                },
            ),
        },
        {
            'choices' : (
                (
                    {
                        'token'      : 'interfaces',
                        'action'     : show_switch_interfaces_pipeline,
                        'short-help' : 'Show interfaces for switches',
                        'doc'        : 'switch|show-interfaces',
                    },
                    {
                        'optional'   : True,
                        'field'      : 'core/switch/interface/name',
                        'base-type'  : 'obj-type',
                        'completion' : 'complete-from-another',
                        'other-path' : 'core/switch/interface',
                        'scoped'     : 'dpid',
                        'path'       : 'core/switch/interface',
                        'action'     : show_switch_interfaces_pipeline,
                    },
                    {
                        'token'      : 'stats',
                        'action'     : 'show',
                        'select'     : 'interface',
                        'path'       : 'core/switch?select=stats/interface',
                        'optional'   : True,
                        'short-help' : 'Show interfaces stats for switches',
                        'format'     : {
                                        'interface' : {
                                            'default' : [
                                                '#',
                                                'dpid',
                                                'name',
                                                'receive-packets',
                                                'receive-bytes',
                                                'receive-dropped',
                                                'transmit-packets',
                                                'transmit-bytes',
                                                'transmit-dropped',
                                            ],
                                            'details' : [
                                                '#',
                                                'dpid',
                                                'name',
                                                'receive-packets',
                                                'receive-bytes',
                                                'receive-dropped',
                                                'receive-errors',
                                                'transmit-packets',
                                                'transmit-bytes',
                                                'transmit-dropped',
                                                'transmit-errors',
                                                'receive-frame-errors',
                                                'receive-overrun-errors',
                                                'receive-crc-errors',
                                                'collisions',
                                            ],
                                            'brief'   : [
                                                '#',
                                                'dpid',
                                                'name',
                                                'receive-packets',
                                                'receive-bytes',
                                                'transmit-packets',
                                                'transmit-bytes',
                                            ],
                                        },
                                       },
                    },
                ),
                {
                    'field'     : 'alias',
                    'type'      : 'enum',
                    'values'    : 'alias',
                    'path'      : 'core/switch?select=alias',
                    'action'    : 'show',
                    'doc'       : 'switch|show-switch-alias',
                },
            )
        },
        {
            'optional' : True,
            'field'    : 'detail',
            'type'     : 'enum',
            'values'   : ('details', 'brief',),
            'short-help' : 'Show switch output format level',
        }
    )
}


SWITCH_SUBMODE_SHOW_INTERFACE_COMMAND_DESCRIPTION = {
    'name'         : 'show',
    'mode'         : 'config-switch*',
    'command-type' : 'show',
    'rbac-group'   : 'admin',
    'path'         : 'core/switch/interface',
    'item-name'    : 'interface',
    'short-help'   : 'Show interfaces for switch associated with current submode',
    'args'         : (
        {
            'token'       : 'interfaces',
            'action'      : 'legacy-cli',
            'scoped'      : True,
            'action'      : show_switch_interfaces_pipeline,
        },
    )
}


SWITCH_SHOW_TCPDUMP_COMMAND_DESCRIPTION = {
    'name'                : 'show',
    'mode'                : 'login',
    'rbac-group'          : 'admin',
    'short-help'          : 'Show switch tcpdump via controller',
    'command-type'        : 'show',
    'path'                : 'core/switch',
    'args' : (
        {
            'token'        : 'switch',
            'obj-type'     : 'switches',
        },
        {
            'choices'      : (
                {
                    'field'        : 'dpid',
                    'completion'   : 'complete-alias-choice',
                    'type'         : 'dpid',
                    'help-name'    : 'switch dpid or alias',
                    'data-handler' : 'alias-to-value',
                },
                {
                    'field'  : 'dpid',
                    'type'   : 'enum',
                    'values' : 'all',
                },
            )
        },
        {
            'field'      : 'tcpdump',
            'optional'   : False,
            'type'       : 'enum',
            'values'     : 'trace',
            'obj-type'   : 'switch-tcpdump',
            'action'     : 'legacy-cli',
        },
        {
            'field'     : 'oneline',
            'type'      : 'enum',
            'values'    : 'oneline',
            'optional'  : True,
        },
        {
            'field'     : 'single_session',
            'type'      : 'enum',
            'values'    : 'single-session',
            'optional'  : True,
        },
        {
            'field'     : 'echo_reply',
            'type'      : 'enum',
            'values'    : 'echo-reply',
            'optional'  : True,
            'permute'   : 'skip',
        },
        {
            'field'     : 'echo_request',
            'type'      : 'enum',
            'values'    : 'echo-request',
            'optional'  : True,
            'permute'   : 'skip',
        },
        {
            'field'     : 'features_rep',
            'type'      : 'enum',
            'values'    : 'features-rep',
            'optional'  : True,
            'permute'   : 'skip',
        },
        {
            'field'     : 'flow_mod',
            'type'      : 'enum',
            'values'    : 'flow-mod',
            'optional'  : True,
            'permute'   : 'skip',
        },
        {
            'field'     : 'flow_removed',
            'type'      : 'enum',
            'values'    : 'flow-removed',
            'optional'  : True,
            'permute'   : 'skip',
        },
        {
            'field'     : 'get_config_rep',
            'type'      : 'enum',
            'values'    : 'get-config-rep',
            'optional'  : True,
            'permute'   : 'skip',
        },
        {
            'field'     : 'hello',
            'type'      : 'enum',
            'values'    : 'hello',
            'optional'  : True,
            'permute'   : 'skip',
        },
        {
            'field'     : 'packet_in',
            'type'      : 'enum',
            'values'    : 'packet-in',
            'optional'  : True,
            'permute'   : 'skip',
        },
        {
            'field'     : 'packet_out',
            'type'      : 'enum',
            'values'    : 'packet-out',
            'optional'  : True,
            'permute'   : 'skip',
        },
        {
            'field'     : 'port_status',
            'type'      : 'enum',
            'values'    : 'port-status',
            'optional'  : True,
            'permute'   : 'skip',
        },
        {
            'field'     : 'set_config',
            'type'      : 'enum',
            'values'    : 'set-config',
            'optional'  : True,
            'permute'   : 'skip',
        },
        {
            'field'     : 'stats_reply',
            'type'      : 'enum',
            'values'    : 'stats-reply',
            'optional'  : True,
            'permute'   : 'skip',
        },
        {
            'field'     : 'stats_request',
            'type'      : 'enum',
            'values'    : 'stats-request',
            'optional'  : True,
            'permute'   : 'skip',
        },
        {
            'field'     : 'detail',
            'type'      : 'enum',
            'values'    : 'detail',
            'optional'  : True,
        },
   )
}

#
# ------------------------------------------------------------------------------
# SWITCH_TUNNEL_SHOW_COMMAND_DESCRIPTION
#


SWITCH_TUNNEL_SHOW_COMMAND_DESCRIPTION = {
    'name'         : 'show',
    'mode'         : 'login',
    'rbac-group'   : 'admin',
    'feature'      : 'bvs',
    'short-help'   : 'Show tunnels for all switches',
    'command-type' : 'display-rest',
    'url'          : 'tunnel-manager/all',
    'format'       : 'tunnel-details',
    'obj-type'     : 'switch',
    'args'         : (
        'tunnel',
    ),
}


SWITCH_TUNNEL_SHOW_WITH_DPID_COMMAND_DESCRIPTION = {
    'name'         : 'show',
    'mode'         : 'login',
    'rbac-group'   : 'admin',
    'feature'      : 'bvs',
    'short-help'   : 'Show tunnels for selected switches',
    'command-type' : 'display-rest',
    'obj-type'     : 'switch',
    'url'          : 'tunnel-manager/%(dpid)s',
    'format'       : 'tunnel-details',
    'args'         : (
        'tunnel',
        {
            'choices' : (
                {
                    'field'      : 'dpid',
                    'completion' : 'complete-object-field',
                    'type'       : 'dpid',
                },
                {
                    'field'      : 'dpid',
                    'type'       : 'enum',
                    'values'     : 'all',
                },
            )
        },
        {
            'optional' : True,
            'choices' : (
                {
                    'field'    : 'active',
                    'type'     : 'enum',
                    'values'   : 'active',
                    'action'   : 'display-rest',
                    'url'      : 'tunnel-manager/%(dpid)s',
                    'format'   : 'tunnel-details',
                },
                {
                    'type'     : 'enum',
                    'values'   : 'interfaces',
                    'action'   : 'legacy-cli',
                    'obj-type' : 'tunnel-interfaces',
                }
            )
        },
    ),
}

#
# ------------------------------------------------------------------------------
# SWITCH_CORE_SWITCH_TERMINATION_COMMAND_DESCRIPTION
#

SWITCH_CORE_SWITCH_COMMAND_DESCRIPTION = {
    'name'         : 'core-switch',
    'short-help'   : 'Enable core-switch property for this switch',
    'mode'         : 'config-switch',
    'parent-field' : 'dpid',
    'command-type' : 'config',
    'args' : (),
    'action': (
        {
            'proc' : 'write-fields',
            'data' : {'core-switch' : True}
        },
    ),
    'no-action': (
        {
            'proc' : 'write-fields',
            'data' : {'core-switch' : False},
        }
    )
}

#
# ------------------------------------------------------------------------------
# SWITCH_TUNNEL_TERMINATION_COMMAND_DESCRIPTION
#

SWITCH_TUNNEL_TERMINATION_COMMAND_DESCRIPTION = {
    'name'         : 'tunnel',
    'short-help'   : 'Enable/Disable tunnel creation for this switch',
    'mode'         : 'config-switch',
    'command-type' : 'config',
    'parent-field' : 'dpid',
    'obj-type'     : 'switch-config',
    'data'         : { 'tunnel-termination' : 'default' }, # for no command
    'args'         : (
        'termination',
        {
            'field'           : 'tunnel-termination',
            'type'            : 'enum',
            'values'          : ( "enabled", "disabled" ),
            'optional-for-no' : True,
        }
    )
}

#
# ------------------------------------------------------------------------------
# SWITCH_ALIAS_COMMAND_DESCRIPTION
#

SWITCH_SWITCH_ALIAS_COMMAND_DESCRIPTION = {
    'name'         : 'switch-alias',
    'mode'         : 'config-switch',
    'short-help'   : 'Attach alias to switch',
    'command-type' : 'config',
    'scoped'       : True,
    'args'         : (
        {
            'field'           : 'alias',
            'optional-for-no' : False,
            'completion'      : 'complete-object-field',
        }
    )
}

#
# ------------------------------------------------------------------------------
# SWITCH_INTERFACE_COMMAND_DESCRIPTION
#  enter config-switch-if submode
#

SWITCH_INTERFACE_COMMAND_DESCRIPTION = {
    'name'                : 'interface',
    'mode'                : 'config-switch*',
    'short-help'          : 'Enter switch-if submode, configure switch interface',
    'command-type'        : 'config-submode',
    'path'                : 'core/switch/interface',
    'submode-name'        : 'config-switch-if',
    'syntax-help'         : 'Enter an interface name',
    'item-name'           : 'interface',
    'show-this'           : 'show switch %(dpid)s interfaces %(name)s',
    'args' : (
        {
            'field'        : 'name',
            'completion'   : [ 'complete-object-field',
                             ],
            'scoped'       : 'dpid',
            'data-handler' : 'warn-missing-interface',
            'syntax-help'  : 'Switch Interface',
        }
    )
}

#
# ------------------------------------------------------------------------------
# SWITCHPORT_COMMAND_DESCRIPTION
#  'switchport mode external'
#  'no switchport mode external'
#
#  deprecated in bigdb.
#

SWITCHPORT_COMMAND_DEPRECATED_DESCRIPTION = {
    'name'         : 'switchport',
    'short-help'   : 'Configure interface as connected to an external network',
    'mode'         : 'config-switch-if',
    'command-type' : 'config',
    'obj-type'     : 'switch-interface-config',
    'fields'       : ('broadcast', 'mode',),
    'action'       : 'write-fields',
    'no-action'    : 'reset-fields',
    'args'         : (
        'mode',
        {
            'field'       : 'mode',
            'type'        : 'enum',
            'values'      : 'external',
            'help-name'   : 'interface connects to external network',
            'short-help'  : 'interface connects to external network',
            'syntax-help' : 'external'
        },
    )
}

#
# ------------------------------------------------------------------------------
#
# interface-alias deprecated in bigdb.


SWITCH_INTERFACE_INTERFACE_ALIAS_COMMAND_DEPRECATED_DESCRIPTION = {
    'name'         : 'interface-alias',
    'mode'         : 'config-switch-if',
    'short-help'   : 'Attach alias to switch interface',
    'command-type' : 'manage-alias',
    'obj-type'     : 'switch-interface-alias',
    'scoped'       : True,
    'args'         : (
        {
            'field'           : 'id',
            'optional-for-no' : False,
            'completion'      : 'complete-object-field',
            'short-help'      : 'Alias string',
        }
    )
}

#
# FORMATS
#

import fmtcnv

SWITCH_FORMAT = {
    'switch' : {
        'field-orderings' : {
            'default' : [ 'Idx', '@', 'switch-alias', 'connected-since',
                          'ip-address', 'tunnels', 'core-switch', ],
            'details' : [ 'Idx','@', 'switch-alias', 'connected-since',
                          'ip-address', 'tunnels', 'tunnel-supported', 'tunnel-termination', 'core-switch', ],
            'brief'   : [ 'Idx', '@', 'switch-alias', 'connected-since',
                          'ip-address', ],
            },
        'fields' : {
            '@'                  : {
                                     'verbose-name' : 'Switch DPID',
                                   },
            'active'             : {
                                   },
            'core-switch'        : {
                                     'verbose-name' : 'Core Switch',
                                     'validate'  : 'validate_switch_core_switch',
                                   },
            'connected-since'    : {
                                     'verbose-name' : 'Connected Since',
                                     'formatter' : fmtcnv.timestamp_to_local_timestr,
                                   },
            'capabilities'       : {
                                     'formatter' : fmtcnv.decode_switch_capabilities,
                                   },
            'actions'            : {
                                     'formatter' : fmtcnv.decode_switch_actions,
                                   },
            'ip-address'         : {
                                     'verbose-name' : 'IP Address',
                                   },
            'socket-address'     : {
                                   },
            'buffers'            : {
                                   },
            'controller'         : {
                                   },
            'tables'             : {
                                   },
            'switch-alias'       : {

                                     'verbose-name' : 'Alias'
                                   },
            'tunnels'            : {
                                     'verbose-name' : 'Tunnels'
                                   },
            'tunnel-supported'   : {
                                     'verbose-name' : 'Tunnel Supported'
                                   },
            'tunnel-termination' : {
                                     'verbose-name' : 'Termination' },
            'tunnel-active'      : {
                                     'verbose-name' : 'Tunnels Active' },
            'dp-desc'            : {
                                   },
            'hw-desc'            : {
                                   },
            'sw-desc'            : {
                                   },
            'serial-num'         : {
                                   },
            }
        },
}


SWITCH_CONFIG_FORMAT = {
    'switch-config' : {
        'field-orderings' : {
            'default' : [
                          'Idx',
                          'dpid',
                          'tunnel-termination',
                          'core-switch',
                        ],
        },
    },
}


SWITCH_ALIAS_FORMAT = {
    'switch-alias' : {
        'field-orderings' : {
            'default'     : [ 'Idx', 'id', 'switch' ]
            },
        },
}


SWITCH_INTERFACE_CONFIG_FORMAT = {
    'switch-interface-config' : {
        'field-orderings' : {
            'default' :     [ 'Idx', 'if-name', 'mode',
                              'bigtap-role', 'bigtap-rewrite-vlan'  ]
            },
        'fields'          : {
            'broadcast'           : {
                                    },
            'name'                : {
                                    },
            'mode'                : {
                                      'verbose-name' : 'Switchport Mode',
                                    },
            'bigtap-role'         : {
                                      'verbose-name' : 'BigTap Rode',
                                    },
            'bigtap-rewrite-vlan' : {
                                      'verbose-name' : 'BigTap Rewrite Vlan',
                                    },
            },
        },
}


SWITCH_INTERFACE_ALIAS_FORMAT = {
    'switch-interface-alias' : {
        'field-orderings' : {
            'default'     : [ 'Idx', 'id', 'switch', 'name' ]
            },
        'fields' : {
            'id'          : { 'verbose-name' : 'Alias',
                            }
            }
        },
}


SWITCH_INTERFACES_FORMAT = {
    'switch-interfaces' : {
        'field-orderings' : {
            'default' : [ 'Idx', 'switch', 'portName', 'state', 'config',
                          'receiveBytes', 'receivePackets', 'receiveErrors',
                          'transmitBytes', 'transmitPackets', 'transmitErrors',
                          'mode', 'broadcast',
                        ],
            'details' : [ 'Idx', 'switch', 'portName', 'hardwareAddress',
                          'config', 'stp-state', 'state', 'currentFeatures',
                          'advertisedFeatures', 'supportedFeatures',
                          'peer-features', 'mode', 'broadcast',
                        ],
            'brief'   : [ 'Idx', 'switch', 'portName', 'state', 'config' ],
            },

        'fields' : {
            'id'                  : {
                                    },
            'switch'              : { 'formatter' : fmtcnv.replace_switch_with_alias
                                    },
            'portName'            : { 'verbose-name' : 'IF',
                                    },
            'hardwareAddress'     : { 'verbose-name' : 'MAC Address'
                                    },
            'config'              : {
                                      'formatter' : fmtcnv.decode_port_config
                                    },
            'state'               : { 'verbose-name' : 'Link',
                                      'formatter' : fmtcnv.decode_port_up_down,
                                    },
            'stp-state'           : {
                                      'formatter' : lambda i, data : 
                                                    fmtcnv.decode_port_stp_state(data['state'],
                                                                                 data),
                                    },
            'currentFeatures'     : { 'verbose-name' : 'Curr Features',
                                      'formatter' : fmtcnv.decode_port_features
                                    },
            'advertisedFeatures'  : { 'verbose-name' : 'Adv Features',
                                      'formatter' : fmtcnv.decode_port_features
                                    },
            'supportedFeatures'   : { 'verbose-name' : 'Supp Features',
                                      'formatter' : fmtcnv.decode_port_features
                                    },
            'peer-features'       : { 'verbose-name' : 'Peer Features',
                                      'formatter' : fmtcnv.decode_port_features
                                    },
            'receiveBytes'         : { 'verbose-name' : 'Rcv Bytes',
                                       'formatter' : fmtcnv.decode_port_counter},
            'receivePackets'       : { 'verbose-name' : 'Rcv Pkts',
                                        'formatter' : fmtcnv.decode_port_counter},
            'receiveErrors'        : { 'verbose-name' : 'Rcv Errs',
                                       'formatter' : fmtcnv.decode_port_counter},
            'transmitBytes'        : { 'verbose-name' : 'Xmit Bytes',
                                       'formatter' : fmtcnv.decode_port_counter},
            'transmitPackets'      : { 'verbose-name' : 'Xmit Pkts',
                                       'formatter' : fmtcnv.decode_port_counter},
            'transmitErrors'       : { 'verbose-name' : 'Xmit Errs',
                                       'formatter' : fmtcnv.decode_port_counter},
            },
        },
}

TUNNEL_DETAILS_FORMAT = {
    'tunnel-details' : {
        'field-orderings' : {
            'default' : [ 'Idx', 'dpid', 'localTunnelIPAddr',
                           'tunnelPorts',
                        ]
        },

        'fields' : {
            'dpid'              : {
                                     'verbose-name' : 'Switch DPID',
                                     'primary_key': True,
                                     'formatter' : fmtcnv.replace_switch_with_alias,
                                  },
            'localTunnelIPAddr' : {
                                    'verbose-name' : 'Local tunnel IP',
                                  },
            'tunnelPorts' :       {
                                     'verbose-name' : 'Remote tunnel IP',
                                  },
        },
    },
}

