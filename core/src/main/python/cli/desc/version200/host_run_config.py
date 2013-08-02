import run_config

#
# --------------------------------------------------------------------------------

def running_config_host(context, scoreboard, data):
    """
    Add host details, including tags.
    """

    detail = data.get('detail', False)
    context.running_config('core/device', detail)


host_running_config_tuple = (
    (
       {
           'optional'   : False,
           'field'      : 'running-config',
           'type'       : 'enum',
           'values'     : 'host',
           'short-help' : 'Configuration for hosts',
           'doc'        : 'running-config|show-host',
       },
       {
            'field'        : 'word',
            'type'         : 'host',
            'completion'   : 'complete-from-another',
            'other'        : 'host|mac',
            'parent-field' : None,
            'data-handler' : 'alias-to-value',
            'action'       : 'legacy-cli',
            'optional'     : True,
       }
    ),
)

# device manager enabled?
run_config.register_running_config('host', 5000, lambda x: False,
                                   running_config_host,
                                   host_running_config_tuple)

