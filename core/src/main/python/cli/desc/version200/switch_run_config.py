import run_config

#
# --------------------------------------------------------------------------------

def running_config_switch(context, scoreboard, data):
    """
    Switch running-config
    """ 

    detail = data.get('detail')
    dpid = data.get('dpid') 
    if dpid:
        path = [('core/switch', { 'core/switch/dpid' : dpid }),]
        path.append(('applications/bigtap/interface-config',
                     {'switch' : dpid}),)
    else:  
        path = ['core/switch', 'applications/bigtap/interface-config']
    run_config.post_paths(context, path, detail)
           
       
switch_running_config_tuple = (
    (
       {
           'optional'   : False,
           'field'      : 'running-config', 
           'type'       : 'enum',
           'values'     : 'switch',
           'short-help' : 'Configuration for switches',
           'doc'        : 'running-config|show-switch',
       },
       {
           'field'        : 'word',
           'type'         : 'dpid',
           'completion'   : 'complete-from-another',
           'other-path'   : 'core/switch|dpid',
           'action'       : 'legacy-cli',
           'data-handler' : 'alias-to-value',
           'optional'     : True,
       }   
    ),     
)          
           
run_config.register_running_config('switch', 3000, None,
                                   running_config_switch,
                                   switch_running_config_tuple)
