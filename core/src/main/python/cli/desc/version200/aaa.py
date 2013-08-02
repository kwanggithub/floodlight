import os
import stat
import shutil
import readline

import command
import error

# For local testing, export TEST_USER to the base directory where
# you want to keep history
#

def aaa_user_directory(data, no_command):
    user_name = data.get('user-name')
    home_directory = os.environ.get("TEST_USER", "/home")

    if user_name == None:
        raise error.CommandSemanticError('aaa_user_directory: missing user-name')
    if no_command:
        if user_name != 'admin':
            user_directory = os.path.join(home_directory, user_name)
            # XXX what about error for this user?
            shutil.rmtree(user_directory)
    else:
        dir_modes = ((stat.S_IRUSR | stat.S_IWUSR | stat.S_IXUSR) +
                     (stat.S_IRGRP | stat.S_IWGRP | stat.S_IXGRP) +
                     (stat.S_IROTH | stat.S_IXOTH))
        if user_name == 'admin':
            # currently the admin home directory is not removed.
            # perhaps validate it excists?
            return

        if not os.path.exists(home_directory):
            raise error.CommandSemanticError('aaa_user_directory: %s directory' %
                                             home_directory)

        user_directory = os.path.join(home_directory, user_name)
        if not os.path.exists(user_directory):
            try:
                os.mkdir(user_directory, dir_modes)
            except Exception, e:
                raise error.CommandSemanticError('aaa_user_directory: can\'t create '
                                                 'user subdirectory:' +
                                                 user_directory)
            else:
                # the mkdir seems to honor umask.
                os.chmod(user_directory, dir_modes)

                #
                hush_login = os.path.join(user_directory, '.hushlogin')
                if not os.path.exists(hush_login):
                    fno = os.open(hush_login, os.O_WRONLY | os.O_CREAT, 0600)
                    os.close(fno)

        

command.add_action('aaa-user-directory', aaa_user_directory,
                   { 'kwargs' : { 'data'       : '$data',
                                  'no_command' : '$is-no-command',}})


USER_SUBMODE_COMMAND_DESCRIPTION = {
    'name'                : 'user',
    'path'                : 'core/aaa/local-user',
    'mode'                : 'config*',
    'short-help'          : 'Enter configuration submode for user accounts',
    'command-type'        : 'config-submode',
    'submode-name'        : 'config-local-user',
    'short-help'          : 'Enter user submode, configure user details',
    'rbac-group'          : 'admin',
    'show-this'           : 'show user %(user-name)s',
    'item-name'           : 'user-name',
    'rc-order'            : 7000000,
    'action'              : (
                              'push-mode-stack',
                              'aaa-user-directory',
                            ),
    'no-action'           : (
                              'delete-objects',
                              'aaa-user-directory',
                            ),
    'args'                : (
        {
            'field'      : 'user-name',
            'type'       : 'identifier',
            'completion' : [ 'complete-object-field' ],
        }
    ),
}

import getpass

def aaa_password_prompt(data, field, old_field = None):
    if old_field:
        old_password = getpass.getpass('Current password:')
        data[old_field] = old_password
        first_password = getpass.getpass('New password:')
    else:
        first_password = getpass.getpass()
    second_password = getpass.getpass('Re-enter:')
    if first_password != second_password:
        raise error.CommandSemanticError('Passwords don\'t match')
    data[field] = first_password
    

command.add_action('aaa-password-prompt',  aaa_password_prompt,
                    { 'kwargs' : {   'data'      : '$data',
                                     'field'     : '$field',
                                     'old_field' : '$old-field',
                                }})


def aaa_encrypt_field( data, field):
    bigdb = command.bigsh.bigdb
    hash = bigdb.hash_request(data[field])
    if not hash:
        raise error.CommandRestError(message='Unable to hash password')
    data['password'] = hash
    del data[field]

command.add_action('aaa-encrypt-field',  aaa_encrypt_field,
                    { 'kwargs' : {   'data'  : '$data',
                                     'field' : '$field'
                                }})

USER_PASSWORD_COMMAND_DESCRIPTION = {
    'name'          : 'password',
    'mode'          : 'config-local-user',
    'command-type'  : 'config',
    'no-supported'  : False,
    'field'         : 'unhashed-password',
    'short-help'    : 'Associate a password with the user',
    'doc'           : 'user|password',
    'action'        : (
        {
            'proc'  : 'aaa-password-prompt',
        },
        {
            'proc'  : 'aaa-encrypt-field',
        },
        {
            'proc'  : 'write-fields',
        },
    ),
    'args'          : (
        {
            'optional'        : True,
            'field'           : 'unhashed-password',
            'type'            : 'string',
            'syntax-help'     : 'Associate password name with user',
            'completion-text' : 'password',
            'action'    : (
                {
                    'proc'    : 'echo',
                    'message' : 'To avoid the password being saved in the clear in the command history, use the password command without an argument and you will be prompted for the new password',
                },
                {
                    'proc'  : 'aaa-encrypt-field',
                },
                {
                    'proc'  : 'write-fields',
                },
            ),
        },
    )
}

HASHED_PASSWORD_COMMAND_DESCRIPTION = {
    'name'          : 'hashed-password',
    'mode'          : 'config-local-user',
    'command-type'  : 'config',
    'no-supported'  : False,
    'field'         : 'unhashed-password',
    'short-help'    : 'Associate a hashed password with the user',
    'doc'           : 'user|hashed-password',
    'args'          : (
        {
            'field'           : 'password',
            'tyoe'            : 'string',
            'syntax-help'     : 'Associate hashed password name with user',
            'completion-text' : 'hashed-password',
            'completion'      : 'complete-object-field',
        },
    ),
}


USER_FULL_NAME_COMMAND_DESCRIPTION = {
    'name'          : 'full-name',
    'mode'          : 'config-local-user',
    'command-type'  : 'config',
    'short-help'    : 'Associate a descriptive name with the user',
    'doc'           : 'user|full-name',
    'args'          : {
        'field'           : 'full-name',
        'type'            : 'string',
        'syntax-help'     : 'Associate description name with user',
        'completion-text' : 'full-name',
        'completion'      : 'complete-object-field',
    },
}

import atexit
import json
import urllib
import urllib2

# history and user accounts are intertwined, so the
# history command is described here.

def history_read(user_name = None):
    global history_file, history_user
    base_dir = '/home'

    def history_path(user_name):
        if user_name == 'admin':
            return os.path.join(os.environ["HOME"], ".bigsh_history")
        else:
            return os.path.join(os.environ.get("TEST_USER", base_dir),
                                user_name, ".bigsh_history")

    bigdb = command.bigsh.bigdb
    if user_name:
        history_user = user_name
        history_file = history_path(user_name)
    elif bigdb.enabled():

        bigdb.cache_session_details()
        if bigdb.cached_user_name:
            user_name = bigdb.cached_user_name
            cached_schema_result = bigdb.cached_session_schema
            child_schemas = cached_schema_result['listElementSchemaNode']['childNodes']
            created_schema = child_schemas['created']
            create_time = bigdb.cached_session_response['created']
            formatted_time = bigdb.bigdb_format_value(create_time,
                                                      created_schema)

            last_address = bigdb.cached_session_response['last-address']
            # nice_name = command.bigsh.controller_name_for_ip(last_address)
            # XXX need <ip> -> <controller-name> map
            nice_name = None
            if nice_name:
                if last_address == '127.0.0.1':
                    last_address = '%s' % nice_name
                else:
                    last_address = '%s (%s)' % (nice_name, last_address)
            else:
                nice_name = last_address

            print 'Logged in as %s, authenticated %s, auth request from %s' % (
                    user_name, formatted_time, nice_name)

            history_user = user_name
            history_file = history_path(user_name)

            # ensure the directory for the user_name exists.
            try:
                aaa_user_directory({'user-name' : user_name} , False)
            except Exception, e:
                if command.bigsh.description:
                    print 'Unable to create history directory:', e
                print 'No history from previous CLI sessions available'
                readline.clear_history()
                history_file = None
                return
        else:
            # no user mans no history file.
            print 'BigDB: User request failed. No history available'
            history_file = None
            readline.clear_history()
            return
    else:
        # backward compatability
        history_file = os.path.join(os.environ["HOME"], ".bigsh_history")

    # create the initial history file group-writable,
    # allowing tacacs users to write to it
    if os.path.exists(history_file):
        st = os.stat(history_file)
        if not (st[stat.ST_MODE] & stat.S_IWGRP):
            buf = open(history_file).read()
            os.rename(history_file, history_file + "~")
            mask = os.umask(007)
            fno = os.open(history_file, os.O_WRONLY | os.O_CREAT, 0660)
            os.umask(mask)
            os.write(fno, buf)
            os.close(fno)
    else:
        mask = os.umask(007)
        try:
            fno = os.open(history_file, os.O_WRONLY | os.O_CREAT, 0660)
        except Exception, e:
            print 'No history from previous CLI sessions available'
            if command.bigsh.description:
                print 'BigDB: %s: %s' % (history_file, e)
            readline.clear_history()
            return

        os.umask(mask)
        os.close(fno)
    try:
        readline.clear_history()
        readline.read_history_file(history_file)
    except IOError:
        pass


def history_save():
    if history_file:
        aaa_user_directory({ 'user-name' : history_user }, False)

        try:
            readline.write_history_file(history_file)
        except Exception, e:
            if command.bigsh.description:
                print 'history_save: %s: failed to save:' % history_file, e
            print 'No history saved from this session'


def history_setup():
    # this is intended to be called by bigcli.py, via
    # action execution, although it can also be used by
    # command descriptions.

    global history_file
    history_file = None

    if not command.bigsh.batch:
         history_read()
         atexit.register(history_save)

command.add_action('history-setup', history_setup, )


def history_reinit(data):
    # used by 'reauth', user name is in the data dict.
    global history_file
    if history_file:
        try:
            readline.write_history_file(history_file)
        except Exception, e:
            if command.bigsh.description:
                print 'history_reinit: %s: failed to save:' % history_file, e
        readline.clear_history()
        history_file = None
    if 'user' in data:
        user_name = data['user']
    else:
        bigdb = command.bigsh.bigdb
        if bigdb.cached_user_name != None: # XXX need an api here
            user_name = bigdb.cached_user_name
        else:
            return
    aaa_user_directory({'user-name' : user_name} , False)
    history_read(user_name)

command.add_action('history-reinit', history_reinit,
                   {'kwargs': {'data': '$data'}})


def history_show(data):
    num_commands = readline.get_current_history_length() + 1
    how_many = data.get('count', num_commands - 1)
    # bound the number of entries by the history length.
    if num_commands-int(how_many) < 1:
        how_many = num_commands - 1
    for i in range(num_commands-int(how_many), num_commands):
        yield "%s: %s\n" % (i, readline.get_history_item(i))


command.add_action('history-show', history_show,
                   {'kwargs': {'data': '$data'}})


HISTORY_COMMAND_DESCRIPTION = {
    'name'          : 'history',
    'mode'          : 'login',
    'short-help'    : 'Show commands recently executed',
    'command-type'  : 'show',
    'args'          : (
        {
            'field'       : 'count',
            'type'        : 'integer',
            'short-help'  : 'Number of commands to show',
            'syntax-help' : 'Number of commands to show',
            'optional'    : True,
        },
    ),
    'action'        : 'history-show',
}

# group, and group submode commands.


GROUP_SUBMODE_COMMAND_DESCRIPTION = {
    'name'                : 'group',
    'path'                : 'core/aaa/group',
    'mode'                : 'config*',
    'short-help'          : 'Configuration a group',
    'command-type'        : 'config-submode',
    'submode-name'        : 'config-group',
    'short-help'          : 'Enter group submode, configure group details',
    'rbac-group'          : 'admin',
    'show-this'           : 'show group %(name)s',
    'item-name'           : 'name',
    'doc'                 : 'group|group',
    'rc-order'            : 6000000,
    'args'                : (
        {
            'field'      : 'name',
            'type'       : 'identifier',
            'completion' : [ 'complete-object-field' ],
        }
    ),
}


def show_group_show_compose():
    bigdb_show = command.bigdb_show

    if len(bigdb_show.tables) == 0:
        return
    if not 'group' in bigdb_show.tables:
        return

    group_invert = dict([[x['name'], x] for x in bigdb_show.tables['group']])
    del_items = {}
    for (table, table_data) in bigdb_show.tables.items():
        if table != 'group':
            if len(table_data) == 0:
                bigdb_show.remove_tables(table)
            for (index, row) in enumerate(table_data):
                group = row.get('name')
                if 'user' in row:
                    if not 'user' in group_invert[group]:
                        group_invert[group]['user'] = []
                    # what about limiting the width of the column instead?
                    if len(group_invert[group]['user']) < 12:
                        group_invert[group]['user'].append(row['user'])
                        if table not in del_items:
                            del_items[table] = []
                        del_items[table].append(index)
                if 'rbac-permission' in row:
                    if not 'rbac-permission' in group_invert[group]:
                        group_invert[group]['rbac-permission'] = []
                    # what about limiting the width of the column instead?
                    if len(group_invert[group]['rbac-permission']) < 12:
                        group_invert[group]['rbac-permission'].append(row['rbac-permission'])
                        if table not in del_items:
                            del_items[table] = []
                        del_items[table].append(index)

    for row in bigdb_show.tables['group']:
        if 'user' in row:
            row['user'] = ', '.join(sorted(row['user']))
        if 'rbac-permission' in row:
            row['rbac-permission'] = ', '.join(sorted(row['rbac-permission']))

    bigdb_show.add_tables_column_header('group', 'user', 'User(s)')
    bigdb_show.add_tables_column_header('group', 'rbac-permission', 'Rbac Permission(s)')

    # sort from highest to lowest to preserve list indices.
    for (table, items) in del_items.items():
        for item in sorted(items, reverse = True):
            del bigdb_show.tables[table][item]
            if len(bigdb_show.tables[table]) == 0:
                bigdb_show.remove_tables(table)

command.add_action('show-group-show-compose',  show_group_show_compose, )


SHOW_GROUP_COMMAND_DESCRIPTION = {
    'name'          : 'show',
    'mode'          : 'login',
    'rbac-group'    : 'admin',
    'command-type'  : 'show',
    'short-help'    : 'Show configured groups',
    'path'          : 'core/aaa/group',
    'doc'           : 'group|show-group',
    'action'        : (
        {
            'proc'   : 'show-init',
        },
        {
            'proc'   : 'show-compose',
            'style'  : 'table',
        },
        {
            'proc'   : 'show-group-show-compose',
        },
        {
            'proc'   : 'show-print',
            'style'  : 'table',
            'format' : {
                        'group' : {
                            'default' : [
                                            '#',
                                            'name',
                                            'user',
                                            'rbac-permission',
                                        ],
                        }
                       },
        }
    ),
    'args'          : (
        'group',
        {
            'optional' : True,
            'args'     : (
                {
                    'field' : 'name',
                    'type'  : 'identifier',
                    'completion' : [ 'complete-object-field' ],
                },
                {
                    'optional' : True,
                    'field'    : 'detail',
                    'type'     : 'enum',
                    'values'   : 'details',
                }
            ),
        },
    )
}


GROUP_USER_COMMAND_DESCRIPTION = {
    'name'          : 'associate',
    'mode'          : 'config-group',
    'command-type'  : 'config',
    'command-type'  : 'config-object',
    'path'          : 'core/aaa/group/user',
    'short-help'    : 'Associate a user name with the group',
    'doc'           : 'group|associate-user',
    'args'          : (
        'user',
        {
            'field'           : 'user',
            'type'            : 'string',
            'syntax-help'     : 'Associate user name with group',
            'completion-text' : 'user-name',
            'scoped'          : True,
            'completion'      : [
                                    'complete-from-another',
                                    'complete-config-field',
                                ],
            'other-path'      : 'core/aaa/local-user|user-name',
        },
    ),
}


GROUP_PERMISSION_COMMAND_DESCRIPTION = {
    'name'          : 'associate',
    'mode'          : 'config-group',
    'command-type'  : 'config',
    'command-type'  : 'config-object',
    'path'          : 'core/aaa/group/rbac-permission',
    'short-help'    : 'Associate an rbac permission with the group',
    'doc'           : 'group|rbac-permission',
    'args'          : (
        {
            'token'           : 'rbac-permission',
        },
        {
            'field'           : 'rbac-permission',
            'type'            : 'string',
            'syntax-help'     : 'Associate rbac permission with this group',
            'completion-text' : 'rbac-permission',
            'scoped'          : True,
            'completion'      : [
                                    'complete-from-another',
                                    'complete-config-field',
                                ],
            'other-path'      : 'core/aaa/rbac-permission|name',
        },
    ),
}


def aaa_reauth(data):
    bigdb = command.bigsh.bigdb
    user = data.get('user')
    if user == None:
        if bigdb.cached_user_name != None: # XXX need an api here
            user = bigdb.cached_user_name
            print 'Reauth as:', user
        else:
            raise error.CommandSemanticError('Please include user name')

    password = data.get('password')

    if password == None:
        password = getpass.getpass()
        if password == '':
            raise error.CommandSemanticError('No password entered')

    result = bigdb.auth_request(user, password)

    if result['success'] == True:
        # only revoke the current session cookie if we've been
        # able to collect a new one successfully.
        cookie = os.environ.get('BSC_SESSION_COOKIE')
        if cookie:
            try:
                bigdb.revoke_session()
            except urllib2.HTTPError, e:
                if e.code != 401:
                    print 'Error: during previous cookie revocation:', e
            except Exception, e:
                print 'Error: during previous cookie revocation:', e

            del os.environ['BSC_SESSION_COOKIE']
        os.putenv('BSC_SESSION_COOKIE', result['session_cookie'])
        os.environ['BSC_SESSION_COOKIE'] =  result['session_cookie']
        command.bigsh.rest_api.cache_session_cookie()
        bigdb.cache_session_details()
    else:
        print 'Authentication failure'

command.add_action('aaa-reauth',  aaa_reauth,
                    { 'kwargs' : {   'data'  : '$data',
                                }})


USER_REAUTH_COMMAND_DESCRIPTION = {
    'name'          : 'reauth',
    'mode'          : 'login',
    'command-type'  : 'config',
    'short-help'    : 'Reauthenticate',
    'no-supported'  : False,
    'action'        : (
        'aaa-reauth',
        'history-reinit',
    ),
    'args'          : (
        {
            'optional'    : True,
            'args'        : (
                {
                    'field'       : 'user',
                    'type'        : 'string',
                    'syntax-help' : 'User Name',
                },
                {
                    'field'       : 'password',
                    'type'        : 'string',
                    'syntax-help' : 'Password',
                    'optional'    : True,
                },
            )
        },
    ),
}


def show_user_show_compose(path, data):
    bigdb = command.bigsh.bigdb
    bigdb_show = command.bigdb_show

    if len(bigdb_show.tables) == 0:
        return
    if not 'local-user' in bigdb_show.tables:
        return

    user_name = data.get('user-name')
    groups_path = 'core/aaa/group'

    user_key = bigdb.search_keys[path]
    groups_key = ['user']

    # what's really needed here is for the schema to describe loose foreign
    # key references, then bigdb can provide a mapping from the keys used
    # to search one path to the search values for another.
    filter = dict([[groups_key[idx], data[x]]
                    for (idx, x) in enumerate(user_key) if x in data])
    (schema, results) = bigdb.schema_and_result(groups_path, filter)
    
    user_invert = dict([[x['user-name'], x]
                         for x in bigdb_show.tables['local-user']])

    # groups iteration for items which matched 'filter'
    for result in results.iter():
        group = result['name']
        for user in result.get('user', []): # user is a leaf-list.
            if not user in user_invert:
                # if the user isn't in the table, don't add it back
                # if a user-specific query was requested.
                if user_name:
                    continue
                # perhaps a 'show local-user' and a 'show user' could
                # help distinguish between any user names listed
                # and any known user names.
                user_invert[user] = {'user-name' : user}
                bigdb_show.tables['local-user'].append(user_invert[user])
            if not 'groups' in user_invert[user]:
                user_invert[user]['groups'] = []
            user_invert[user]['groups'].append(group)

    for row in bigdb_show.tables['local-user']:
        if 'groups' in row:
            row['groups'] = ', '.join(sorted(row['groups']))

    bigdb_show.add_tables_column_header('local-user', 'groups', 'Groups')


command.add_action('show-user-show-compose',  show_user_show_compose,
                   {'kwargs' : {'path' : '$path',
                                'data' : '$data' }})


SHOW_USER_COMMAND_DESCRIPTION = {
    'name'          : 'show',
    'mode'          : 'login',
    'rbac-group'    : 'admin',
    'command-type'  : 'show',
    'short-help'    : 'Show configured users',
    'path'          : 'core/aaa/local-user',
    'action'        : (
        {
            'proc'   : 'show-init',
        },
        {
            'proc'   : 'show-compose',
            'style'  : 'table',
        },
        {
            'proc'   : 'show-user-show-compose',
        },
        {
            'proc'   : 'show-print',
            'style'  : 'table',
            # 'select'    : 'local-user',
            'format' : {
                         'local-user' : {
                            'default' : [
                                            '#',
                                            'user-name',
                                            'full-name',
                                            'groups',
                                        ],
                         },
            },
        }
    ),
    'args'          : (
        'user',
        {
            'optional' : True,
            'args'     : (
                {
                    'field' : 'user-name',
                    'type'  : 'identifier',
                    'completion' : [ 'complete-object-field' ],
                },
                {
                    'optional' : True,
                    'field'    : 'detail',
                    'type'     : 'enum',
                    'values'   : 'details',
                }
            ),
        },
    )
}

# this is an odd command, since its not possible to create
# sessions using this method, but it is possible to delete them.
SESSION_USER_SUBMODE_COMMAND_DESCRIPTION = {
    'name'          : 'session',
    'mode'          : 'config',
    'command-type'  : 'config-submode',
    'rbac-group'    : 'admin',
    'short-help'    : 'Configure a session',
    'path'          : 'core/aaa/session',
    'submode-name'  : 'config-session',
    'show-this'     : 'show session %(id)s',
    'args'          : (
        {
            'field'      : 'id',
            'type'       : 'integer',
            'completion' : 'complete-object-field',
        },
    ),
}


def show_session_show_compose():
    bigdb_show = command.bigdb_show

    if len(bigdb_show.tables) == 0:
        return
    if not 'session' in bigdb_show.tables:
        return
    session_invert = dict([[x['id'], x] for x in bigdb_show.tables['session']])
    group_index = []

    del_items = {}
    for (table, table_data) in bigdb_show.tables.items():
        if table != 'session':
            for (index, row) in enumerate(table_data):
                session = row['id']
                if 'groups' not in session_invert[session]:
                    session_invert[session]['groups'] = []
                # what about limiting the width of the column instead?
                if len(session_invert[session]['groups']) < 4:
                    session_invert[session]['groups'].append(row['group'])
                    if table not in del_items:
                        del_items[table] = []
                    del_items[table].append(index)

    for row in bigdb_show.tables['session']:
        if 'groups' in row:
            row['groups'] = ', '.join(sorted(row['groups']))

    bigdb_show.add_tables_column_header('session', 'groups', 'Groups')

    # sort from highest to lowest to preserve list indices.
    for (table, items) in del_items.items():
        for item in sorted(items, reverse = True):
            del bigdb_show.tables[table][item]
            if len(bigdb_show.tables[table]) == 0:
                bigdb_show.remove_tables(table)


command.add_action('show-session-show-compose',  show_session_show_compose, )

SHOW_SESSIONS_COMMAND_DESCRIPTION = {
    'name'          : 'show',
    'mode'          : 'login',
    'command-type'  : 'show',
    'path'          : 'core/aaa/session',
    'short-help'    : 'Show active sessions',
    'action'        : (
        {
            'proc'   : 'show-compose',
            'style'  : 'table',
        },
        {
            'proc'   : 'show-session-show-compose',
        },
        {
            'proc'   : 'show-print',
            'style'  : 'table',
            'format' : {
                'session' : {
                    'default' : [
                                    '#',
                                    ('auth-token', '@'),
                                    ('id', 'ID'),
                                    ('user-name', 'User'),
                                    ('groups', 'Groups'),
                                    ('full-name', 'Full Name'),
                                    ('last-address', 'Last Ip Address'),
                                    ('created', 'Created'),
                                    ('last-touched', 'Last Touched'),
                                ],
                }
            },
        },
    ),
    'args'          : (
        'session',
        {
            'field'           : 'id',
            'type'            : 'integer',
            'completion-text' : 'enter Session Identifier',
            'completion'      : 'complete-object-field',
            'optional'        : True,
        },
        {
            'field'      : 'detail',
            'type'       : 'enum',
            'optional'   : True,
            'values'     : 'details',
        },
    )
}


def whoami_compose():
    rest_api = command.bigsh.rest_api
    bigdb = command.bigsh.bigdb

    session_cookie = rest_api.session_cookie # need a get() for the cookie.
    if session_cookie:
        (schema, result) = bigdb.schema_and_result('core/aaa/session',
                                                   {'auth-token' : session_cookie } )
        if schema == None:
            return
        final = result.expect_single_result()
        if final == None:
            print 'None.'
            return
        
        # need an "add row to table" method
        bigdb_show = command.bigdb_show
        bigdb_show.tables = { 'ident' : {} }
        groups = ', '.join(final['user-info'].get('group', []))
        bigdb_show.tables['ident'] = [{
                                       'id'      : final['user-info']['user-name'],
                                       'groups'  : groups,
                                       'login'   : final['created'],
                                       'auth-ip' : final['last-address'],
                                      }]

        bigdb_show.columns['ident'] = ['id', 'groups', 'login', 'auth-ip']
        bigdb_show.column_headers['ident'] = {
                                                'ident'   : 'Ident',
                                                'groups'  : 'Groups',
                                                'login'   : 'Login',
                                                'auth-ip' : 'Auth-Ip',
                                                }
        bigdb_show.table_names = bigdb_show.tables.keys()

command.add_action('whoami-compose', whoami_compose, )


WHOAMI_COMMAND_DESCRIPTION = {
    'name'          : 'whoami',
    'mode'          : 'login',
    'command-type'  : 'show',
    'short-help'    : 'Identify the current authenticated account',
    'action'        : (
        {
            'proc'  : 'show-init',
        },
        {
            'proc'  : 'whoami-compose',
        },
        {
            'proc'  : 'show-session-show-compose',
        },
        {
            'proc'  : 'show-print',
            'style' : 'detail',
        },
    ),
    'args' : (
    )
}


SHOW_PROFILE_COMMAND_DESCRIPTION = {
    'name'          : 'show',
    'mode'          : 'login',
    'command-type'  : 'show',
    'path'          : 'core/aaa/local-user',
    'short-help'    : 'Show user profile configuration',
    'args'          : (
        'profile',
    )
}


USER_PROFILE_COMMAND_DESCRIPTION = {
    'name'                : 'profile',
    'mode'                : 'config*',
    'short-help'          : 'Enter configuration submode for user accounts',
    'no-supported'        : False,
    'command-type'        : 'config-submode',
    'submode-name'        : 'config-profile',
    'short-help'          : 'Configure user profile',
    'show-this'           : 'show profile',
    'item-name'           : 'user-name',
    'doc'                 : 'profile|profile',
    'action'              : 'push-mode-stack',
    'submode-data'        : {
                                '$cached-user-name' : 'user-name',
                            },
    'args'                : (
    ),
}


USER_PROFILE_PASSWORD_COMMAND_DESCRIPTION = {
    'name'          : 'password',
    'mode'          : 'config-profile',
    'path'          : 'core/aaa/change-password-local-user',
    'command-type'  : 'config-object',
    'no-supported'  : False,
    'field'         : 'new-password',
    'old-field'     : 'old-password',
    'short-help'    : 'Change password',
    'doc'           : 'profile|password',
    'doc-example'   : 'profile|password-example',
    'submode-data'  : {
                            'config-profile' : 'user-name',
                      },
    'action'        : (
        {
            'proc'  : 'aaa-password-prompt',
        },
        {
            'proc'  : 'write-object',
        },
    ),
    'args'          : (
    )
}
