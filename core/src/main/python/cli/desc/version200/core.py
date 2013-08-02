#
#
#
import command
import run_config

#
# HELP_COMMAND_DESCRIPTION WATCH_COMMAND_DESCRIPTION are not
# complete command descriptions, these two are special cases
# since the arguments and completion is other commands.
# ECHO_COMMAND_DESCRIPTION is a special case since the CLI
# currently doesn't support the syntax for its arguments:
# an arbitary number of the same string (field).
#

HELP_COMMAND_DESCRIPTION = {
    'name'         : 'help',
    'mode'         : 'login',
    'command-type' : 'show',
    'short-help'   : 'Show help',
}

WATCH_COMMAND_DESCRIPTION = {
    'name'         : 'watch',
    'mode'         : 'login',
    'command-type' : 'show',
    'short-help'   : 'Show output of other commands',
}

ECHO_COMMAND_DESCRIPTION = {
    'name'         : 'echo',
    'mode'         : 'login',
    'command-type' : 'show',
    'short-help'   : 'Show output of other commands',
}


SHOW_RUNNING_CONFIG_COMMAND_DESCRIPTION = {
    'name'         : 'show',
    'mode'         : 'login',
    'short-help'   : 'Show the current active configuration',
    'action'       : 'running-config',
    'no-supported' : False,
    'obj-type'     : 'running-config',
    'doc'          : 'running-config|show',
    'doc-example'  : 'running-config|show-example',
    'args'         : (
        'running-config',
        run_config.running_config_command_choices,
        {
            'field'      : 'detail',
            'type'       : 'enum',
            'values'     : 'details',
            'optional'   : True,
            'short-help' : 'Include configuration matching default values',
        },
    )
}

SHOW_THIS_COMMAND_DESCRIPTION = {
    'name'         : 'show',
    'obj-type'     : 'this',
    'mode'         : 'config-*',
    'short-help'   : 'Show the object associated with the current submode',
    'doc'          : 'show-this',
    'doc-example'  : 'show-this-example',
    'action'       : 'legacy-cli',
    'no-supported' : False,
    'args'         : (
        'this',
    )
}

SHOW_VERSION_COMMAND_DESCRIPTION = {
    # Note: the output of this command is used in the
    # sys/ha-config.sh script,  if this command is changed,
    # please review that script.   also see: BSC-3559
    'name'         : 'show',
    'mode'         : 'login',
    'short-help'   : 'Show current build version number',
    'doc'          : 'core|version',
    'doc-example'  : 'core|version-example',
    'action'       : 'display-rest',
    'no-supported' : False,
    'url'          : 'system/version',
    'format'       : 'version',
    'detail'       : 'details',
    'args'         : (
        'version',
    )
}

VERSION_COMMAND_DESCRIPTION = {
    'name'                : 'version',
    'no-supported'        : False,
    'short-help'          : 'Move to a specific version of command syntax',
    'doc'                 : 'show-version',
    'doc-example'         : 'show-version-example',
    'mode'                : 'config*',
    'action'              : 'version',
    'args': {
        'field'      : 'version',
        'type'       : 'string',
        'completion' : 'description-versions'
    }
}


ENABLE_SUBMODE_COMMAND_DESCRIPTION = {
    'name'                : 'enable',
    'mode'                : 'login',
    'no-supported'        : False,
    'help'                : 'Enter enable mode',
    'short-help'          : 'Enter enable mode',
    'doc'                 : 'enable',
    'doc-example'         : 'enable-example',
    'command-type'        : 'config-submode',
    'obj-type'            : None,
    'parent-field'        : None,
    'submode-name'        : 'enable',
    'args'                : (),
}

CONFIGURE_SUBMODE_COMMAND_DESCRIPTION = {
    'name'                : 'configure',
    'mode'                : 'enable',
    'no-supported'        : False,
    'help'                : 'Enter configure mode',
    'short-help'          : 'Enter configure mode',
    'doc'                 : 'config',
    'doc-example'         : 'config-example',
    'command-type'        : 'config-submode',
    'obj-type'            : None,
    'parent-field'        : None,
    'submode-name'        : 'config',
    'args'                : {
        'token'           : 'terminal',
        'optional'        : 'true',
    },
}

DEBUG_CLI_COMMAND_DESCRIPTION = {
    'name'                : 'debug',
    'mode'                : ['login', 'enable', 'config*'],
    'short-help'          : 'Manage various cli debugging features',
    'doc'                 : 'debug|debug-cli',
    'doc-example'         : 'debug|debug-cli-example',
    'args'                : {
        'choices' : (
            {
                'token'      : 'cli',
                'action'     : 'cli-set',
                'no-action'  : 'cli-unset',
                'variable'   : 'debug',
                'short-help' : 'Display more detailed information on errors',
                'doc'        : 'debug|cli',
            },
            {
                'token'      : 'cli-backtrace',
                'action'     : 'cli-set',
                'no-action'  : 'cli-unset',
                'variable'   : 'cli-backtrace',
                'short-help' : 'Display backtrace information on errors',
                'doc'        : 'debug|cli-backtrace',
            },
            {
                'token'      : 'cli-batch',
                'action'     : 'cli-set',
                'no-action'  : 'cli-unset',
                'variable'   : 'cli-batch',
                'short-help' : 'Disable any prompts to allow simpler batch processing',
                'doc'        : 'debug|cli-batch',
            },
            {
                'token'      : 'description',
                'action'     : 'cli-set',
                'no-action'  : 'cli-unset',
                'variable'   : 'description',
                'short-help' : 'Display verbose debug information while processing commands',
                'doc'        : 'debug|description',
            },
            (
                {
                    'token'      : 'rest',
                    'action'     : 'cli-set',
                    'no-action'  : 'cli-unset',
                    'variable'   : 'rest',
                    'short-help' : 'Display URLs of any information retrieved via REST',
                    'doc'        : 'debug|rest',
                },
                {
                    'optional'        : True,
                    'optional-for-no' : True,
                    'choices' : (
                        {
                            'field'           : 'detail',
                            'type'            : 'enum',
                            'values'          : ('details', 'brief'),
                            'short-help'      : 'Display both URLs and returned content for REST requests',
                            'doc'             : 'debug|debug-cli-rest-format',
                        },
                        {
                            'field'           : 'record',
                            'tag'             : 'record',
                            'type'            : 'string',
                            'short-help'      : 'record rest api activitiy',
                        },
                    ),
                },
            ),
        ),
    },
}

DEBUG_CLI_PRIVILEGED_COMMAND_DESCRIPTION = {
    'name'                : 'debug',
    'mode'                : ['login', 'enable', 'config*'],
    'rbac-group'          : 'admin',
    'short-help'          : 'Manage various cli debugging features',
    'doc'                 : 'debug|debug-cli',
    'doc-example'         : 'debug|debug-cli-example',
    'args'                : {
        'choices' : (
            {
                'token'      : 'python',
                'action'     : 'shell-command',
                'command'    : 'python',
                'short-help' : 'Enter a python shell',
                'doc'        : 'debug|python',
            },
            {
                'token'      : 'bash',
                'action'     : 'shell-command',
                'command'    : 'bash',
                'short-help' : 'Enter a bash shell',
                'doc'        : 'debug|bash',
            },
            {
                'token'      : 'cassandra-cli',
                'action'     : 'shell-command',
                'command'    : 'cassandra-cli',
                'short-help' : 'Enter a cassandra shell',
                'doc'        : 'debug|assandra-cli',
            },
            {
                'token'      : 'netconfig',
                'action'     : 'shell-command',
                'command'    : 'netconfig',
                'short-help' : 'Enter a netconfig shell',
                'doc'        : 'debug|netconfig',
            },
            #  tcpdump requires that the 'tail' of the debug command be tcpdump syntax,
            #  but that would mean describing the complete tcpdump syntax here, and parsing it
            # {
                # 'token'   : 'tcpdump',
                # 'action'  : 'shell-command',
                # 'command' : '/opt/bigswitch/sys/bin/bscnetconfig',
            # },
        )
    }
}
