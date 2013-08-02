#
#  (c) in 2010, 2011, 2012 by Big Switch Networks
#

import command

INTERNAL_SUBMODE_COMMAND_DESCRIPTION = {
    'name'                : 'internal',
    'mode'                : 'config',
    'no-supported'        : False,
    'feature'             : 'experimental',
    'help'                : 'Enter Internal CLI debugging mode',
    'short-help'          : 'Enter CLI internal debugging mode',
    'doc'                 : 'internal|internal',
    'doc-example'         : 'internal|internal-example',
    'command-type'        : 'config-submode',
    'submode-name'        : 'config-internal',
    'args'                : (),
}

def display_schema(data):
    return command.bigsh.bigdb.schema_detail(data['path'])

command.add_action('display-schema',  display_schema,
                   {'kwargs' : { 'data' : '$data' } } )

INTERNAL_SHOW_SCHEMA_COMMAND_DESCRIPTION = {
    'name'              : 'show',
    'mode'              : 'config-internal',
    'feature'           : 'experimental',
    'no-supported'      : False,
    'short-help'        : 'Show controller devices',
    'action'            : 'display-schema',
    'args' : (
        'schema',
        {
            'field'     : 'path',
            'type'      : 'string',
        },
    ),
}

import json

def display_bigdb_query(data):
    path = data['curl']
    if len(path) == 0:
        return
    if path[0] == "'" and path[-1] == "'":
        url = url[1:-1]
    select = []
    if path.find('?') != -1:
        parts = path.split('?')
        path = parts[0]
        for part in parts[1:]:
            if part.find('=') != -1:
                lr = part.split('=')
                if lr[0].startswith('select'):
                    select.append(lr[1])
    if select:
        print 'display_bigdb_query: select', path, select
    (schema, result) = command.bigsh.bigdb.schema_and_result(path, {})
    if schema == None:
        print 'No schema for path', path
        return

    if result == None:
        print 'No result for path', path
        return

    return json.dumps(result.builder(), sort_keys = True, indent=5,
                                        separators=(',', ':  '))

command.add_action('display-bigdb-query',  display_bigdb_query,
                   {'kwargs' : { 'data' : '$data' } } )


INTERNAL_QUERY_SCHEMA_COMMAND_DESCRIPTION = {
    'name'              : 'show',
    'mode'              : 'config-internal',
    'feature'           : 'experimental',
    'no-supported'      : False,
    'short-help'        : 'Show controller devices',
    'action'            : 'display-bigdb-query',
    'args' : (
        'query',
        {
            'field'     : 'curl',
            'type'      : 'string',
        },
    ),
}

def display_curl(data):
    url = data['curl']
    if len(url) == 0:
        return
    if url[0] == "'" and url[-1] == "'":
        url = url[1:-1]
    
    result = command.bigsh.store.rest_simple_request(url, use_cache = False)

    json_result = json.loads(result)

    return json.dumps(json_result, sort_keys = True, indent=5,
                                        separators=(',', ':  '))

command.add_action('display-curl',  display_curl,
                   {'kwargs' : { 'data' : '$data' } } )



INTERNAL_CURL_SCHEMA_COMMAND_DESCRIPTION = {
    'name'              : 'show',
    'mode'              : 'config-internal',
    'feature'           : 'experimental',
    'no-supported'      : False,
    'short-help'        : 'Show controller devices',
    'action'            : 'display-curl',
    'args' : (
        'curl',
        {
            'field'     : 'curl',
            'type'      : 'string',
        },
    ),
}


def lint_action(data):
    words = []
    if 'command' in data:
        words.append(data['command'])
    command.lint_command(words)

command.add_action('lint-action',  lint_action,
                   {'kwargs' : { 'data' : '$data' } } )

INTERNAL_LINT_COMMAND_DESCRIPTION = {
    'name'         : 'lint',
    'mode'         : 'config-internal',
    'no-supported' : False,
    'action'       : 'lint-action',
    'args'         : {
        'optional' : True,
        'field'    : 'command',
        'type'     : 'string',
    }
}


def permute_action(data):
    words = []
    if 'command' in data:
        words.append(data['command'])
    return command.permute_command(words, data.get('qualify'))

command.add_action('permute-action',  permute_action,
                   {'kwargs' : { 'data' : '$data' } } )


INTERNAL_PERMUTE_COMMAND_DESCRIPTION = {
    'name'         : 'permute',
    'mode'         : 'config-internal',
    'no-supported' : False,
    'action'       : 'permute-action',
    'data'         : { 'qualify' : False },
    'args'         : (
        {
            'optional' : True,
            'field'    : 'command',
            'type'     : 'string',
        },
    )
}


INTERNAL_QUALIFY_COMMAND_DESCRIPTION = {
    'name'         : 'qualify',  # berate
    'mode'         : 'config-internal',
    'no-supported' : False,
    'action'       : 'permute-action',
    'data'         : { 'qualify' : True },
    'args'         : (
        {
            'optional' : True,
            'field'    : 'command',
            'type'     : 'string',
        },
    )
}


def bigdoc_action(data):
    words = []
    if 'command' in data:
        words.append(data['command'])
    return command.get_bigdoc(words)

command.add_action('bigdoc-action',  bigdoc_action,
                   {'kwargs' : { 'data' : '$data' }, } )


INTERNAL_BIGDOC_COMMAND_DESCRIPTION = {
    'name'         : 'bigdoc',
    'mode'         : 'config-internal',
    'no-supported' : False,
    'action'       : 'bigdoc-action',
    'args'         : {
        'optional' : True,
        'field'    : 'command',
        'type'     : 'string',
    }
}

def bigwiki_action(data):
    words = []
    if 'command' in data:
        words.append(data['command'])
    return command.get_bigwiki(words)

command.add_action('bigwiki-action',  bigwiki_action,
                   {'kwargs' : { 'data' : '$data' }, } )
INTERNAL_BIGWIKI_COMMAND_DESCRIPTION = {
    'name'         : 'bigwiki',
    'mode'         : 'config-internal',
    'no-supported' : False,
    'action'       : 'bigwiki-action',
    'args'         : {
        'optional' : True,
        'field'    : 'command',
        'type'     : 'string',
    }
}

SHOW_CLI_COMMAND_DESCRIPTION = {
    'name'         : 'show',
    'mode'         : 'config-internal',
    'short-help'   : 'Show CLI details',
    'doc'          : 'internal|show-cli',
    'action'       : 'display-cli',
    'no-supported' : False,
    'args'         : (
        'cli',
        {
            'optional' : True,
            'field'    : 'detail',
            'type'     : 'enum',
            'values'   : ('details'),
        },
    )
}
