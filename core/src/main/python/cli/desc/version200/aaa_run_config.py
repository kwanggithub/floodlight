import run_config

#
# --------------------------------------------------------------------------------

def running_config_user(context, config, data):
    detail = data.get('detail')
    if context.bigdb.enabled():
        bigdb_running_config(context, 'core/aaa/local-user', detail)


user_running_config_tuple = (
(
       {
           'optional'   : False,
           'field'      : 'running-config',
           'type'       : 'enum',
           'values'     : 'user',
           'short-help' : 'Configuration for user accounts',
           'doc'        : 'running-config|show-user',
       },
    ),
)


run_config.register_running_config('user', 1000, None,
                                   running_config_user,
                                   user_running_config_tuple)

#
# --------------------------------------------------------------------------------

def running_config_group(context, config, data):
    detail = data.get('detail')
    if context.bigdb.enabled():
        bigdb_running_config(context, 'core/aaa/group', detail)


group_running_config_tuple = (
(
       {
           'optional'   : False,
           'field'      : 'running-config',
           'type'       : 'enum',
           'values'     : 'group',
           'short-help' : 'Configuration for group accounts',
           'doc'        : 'running-config|show-group',
       },
    ),
)


run_config.register_running_config('group', 2000, None,
                                   running_config_group,
                                   group_running_config_tuple)

