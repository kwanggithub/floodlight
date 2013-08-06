#
# (c) in 2012, 2013 by Big Switch Networks
# All rights reserved.
#

import sys
import os
import stat
import urllib2                  # exception management
import socket                   # socket.timeout
import errno                    # ECONNREFUSED
import time
import re                       # validation routines
import json

import utif
import error                    # validation routines
import debug
import url_cache
import rest_api
import traceback
import command

#
#
#

def string_type(value):
    if type(value) == str or type(value) == unicode:
        return True


def integer_type(value):
    if type(value) == int or type(value) == long:
        return True


def numeric_type(value):
    if (integer_type(value) or 
      type(value) == float or type(value) == complex):
        return True


def atomic_type(value):
    if (string_type(value) or
     numeric_type(value) or
     type(value) == bool):
        return True


def path_adder(prefix, nextfix):
    if prefix == '':
        return nextfix
    return '%s/%s' % (prefix, nextfix)


def is_path_prefix_of(path, prefix):
    if (path + '/').startswith(prefix):
        return True
    return False


def is_path(object):
    if type(object) == str or type(object) == unicode:
        if object.find('/') != -1:
            return True
    return False


def name_is_compound_key(name):
    if name.find('|') != -1:
        return True
    return False


def alpha_base(i):
    """
    base '26', alphabetic number
    """
    a = ''
    while True:
        a = '%s%s' % (a, chr(ord('a')+ (i % 26)))
        i /= 26
        if i == 0:
            break;
    return a


def convert_schema_boolean(value):
    if value == 'true':
        return True
    if value == 'false':
        return False
    if type(value) == boolean:
        return value
    # raise exception?
    print 'convert_boolean_schem: unknown value'. value


def select_items(si, add = None):
    # convenience function used in various places to help construct the url
    if len(si) == 0:
        return ''
    if add:
        return '&select=%s' % '&select='.join(si)
    return '?select=%s' % '&select='.join(si)

#
#

class BigDB_result():
    def __init__(self, depth, result):
        # self.depth = depth
        self.depth = 1
        self.result = result

    def get(self):
        return self.result

    def recurse(self, result, depth):
        if depth == self.depth:
            yield result
            return
        if type(result) == dict:
            yield result
            return
        for item in result:
            for item in self.recurse(item, depth + 1):
                yield item

    def iter(self):
        for item in self.recurse(self.result, 0):
            yield item

    def expect_single_result(self, failed_result = None):
        """
        In some situations, only a single result is expected.
        Collect and return the single result.  Return
        failed_result when the item is missing, or when
        more than one result exists.
        (perhaps the None and the multiple-results ought to
        be separated as failures?)
        """
        if self.result == None:
            return failed_result
        count = None
        for (count, final) in enumerate(self.iter()):
            if count: # stop after 0th iteration
                break
        if count == 0:
            return final
        return failed_result
    
    def builder(self):
        """
        Flattens the result into a single depth list.
        """
        if self.result == None:
            return None

        result_list = []
        for item in self.recurse(self.result, 0):
            result_list.append(item)
        return result_list

#
#

bigdb_type_formatter = {}
bigdb_type_formatter_association = {}


def bigdb_type_formatter_register(name, function, association=None):
    bigdb_type_formatter[name] = function
    # Some types are posed by 'applications', like bigtap, some types
    # are posted by alias management -- these are associated with 
    # schema's.  If a schema is about to be read, any previously
    # associated schema-types needs to be expunged.
    # 
    if association:
        bigdb_type_formatter_association[name] = association


def bigdb_type_formatter_delete(association):
    for (n, v) in bigdb_type_formatter_association:
        if v == association:
            del bigdb_type_formatter[name]
            del bigdb_type_formatter_association[name]


#
#

class BigDB():
    
    # Notes:
    #
    # The item order of the dictionary-like structures in the schema
    # is actually not order-ed.  The columns of these items, then
    # can't be derived from the schema.
    #

    def __init__(self, controller, bigsh):
        self.bigsh = bigsh              # access to rest api, needs work
        self.search_keys = {}           # dictionary of path to fields which can be searched
        self.search_keys_type = {}      # dictionary of path of fields to a type
        self.search_keys_xref = {}      # from single key name to path
        self.path_configurable = {}     # all configurable paths
        self.path_config_sources = {}   # path's data configuration sources
        self.path_config_only = {}      # path's data is only configured, never discovered
        self.path_cascade = {}          # path's data cascade
        self.path_mandatory = {}        # element mandatory, path values: True, False
        self.path_validator = {}        # validation for the path
        self.path_default = {}          # default value for the path
        self.path_type = {}             # typeSchemaNode['name'] indexed by path
        self.column_header = {}         # indexed by path
        self.case_sensitive = {}        # indexed by path
        self.allow_empty_string = {}    # indexed by path

        self.alias_field = {}           # path subscript returns alias field name
        self.path_alias = {}            # forward alias, from path to alias name
        self.alias_xref = {}            # reverse alias, from alias name to path
        self.alias_type_xref = {}       # based on a type, return a path 
        self.debug = False

        self.known_types = [
                            'INTEGER',
                            'STRING',
                            'BOOLEAN',     # True/False
                            'BINARY',      # bit array
                            'LEAF',
                            'LEAF_LIST',
                            'LIST',
                            'CONTAINER',
                            'REFERENCE',
                           ]

        self.known_data_sources = [
                                    'floodlight-module-data-source',
                                    'switch-data-source',
                                    'controller-data-source',
                                    'topology-data-source',
                                    'aaa-data-source',
                                    'node-local-config',
                                    'config'
                                  ]

        # ought to consider an environment variable for the port.
        self.controller = None
        self.bigdb_port = None
        self.set_controller(controller, 80)

        # rbac groups, cached to prevent constant rbac group lookups.
        self.cached_session_cookie = None
        self.cached_session_response = None
        self.cached_session_schema = None
        self.cached_user_name = None
        self.cached_user_groups = []

        #
        self.int63max = 2**63 - 1
        self.int63min = -2**63
        self.int32max = 2**32 - 1
        self.int32min = -2**32
        self.int31max = 2**31 - 1
        self.int31min = -2**31
        self.int16max = 2**16-1
        self.int16min = -2**16-1
        self.int8max = 2**8-1
        self.int8min = -2**8-1


    def enabled(self):
        if self.schema:
            return True
        return False


    def oops(self):
        # pylint: disable=W0212
        f = sys._getframe().f_back
        return 'OOPS [%s:%d]' % (f.f_code.co_filename, f.f_lineno)
        
    def log(self, s):
        if self.debug:
            print s

    def range(self, range):
        """
        Quick converter for those values which are some
        variation of int_max for a 64 bit java signed integer,
        and int_min for a 64 bit java signed integer
        """
        def single(v):
            if v == self.int63max:
                return '' # '2^63'
            if v == self.int63min:
                return '' # '-2^63'
            return v

        start = single(range['start'])
        end   = single(range['end'])

        if start == '' and end == '':
            return 'int64'
        if start == 0 and end == '':
            return 'int63'
        if start == 1 and end == '':
            return '+int63'
        if start == 0 and end == self.int32max:
            return 'int32'
        if start == 1 and end == self.int32max:
            return '+int32'
        if start == self.int31min and end == self.int31max:
            return 'int32'
        if start == 0 and end == self.int16max:
            return 'int16'
        if start == 1 and end == self.int16max:
            return '+int16'
        if start == 0 and end == self.int8max:
            return 'int8'
        if start == 1 and end == self.int8max:
            return '+int8'
        return '(%s:%s)' % (start, end)


    # isolate references to outside entities, ie:
    # bigsh and pp references need to be collected here
    # in preparation for better times.
    def controller(self):
        return self.bigsh.controller


    def set_controller(self, controller, port = None):
        new_schema = False
        controller_parts = controller.split(':')
        if controller_parts[0] != self.controller:
            self.controller = controller_parts[0]
            new_schema = True
        if port and port != self.bigdb_port:
            self.bigdb_port = port
            new_schema = True
        if new_schema:
            self.schema_request(retry = 4)


    def controller_url(self):
        if self.bigdb_port == 80:
            return 'http://%s' % self.controller
        return ('http://%s:%s' % (self.controller, self.bigdb_port))


    def auth_request(self, user, password):
        """
        Perform an auth request, return the result.  
        Its possible for the result to indicate failed authentiction,
        in which case the ...
        """
        # return the result or throw an exception
        headers = {'Content-type': 'application/json'}
        post_data = {'user': user, 'password' : password }

        tail = '/auth/login'
        url = self.controller_url() + tail
        request = urllib2.Request(url, json.dumps(post_data), headers = headers)
        try:
            response = urllib2.urlopen(request).read()
        except urllib2.HTTPError, e:
            raise e
        except Exception, e:
            raise error.CommandRestError(message=str(e))
        else:
            return json.loads(response)


    def cache_session_details(self, session_cookie = None):
        # if the session cookie is missing, try to find it in the bigsh.rest_api.
        if session_cookie == None:
            session_cookie = self.bigsh.rest_api.session_cookie
            if session_cookie == None:
                # no session 
                return
        if session_cookie == self.cached_session_cookie:
            return
        try:
            (schema, result) = self.schema_and_result('core/aaa/session',
                                                      {'auth-token' : session_cookie } )
        except Exception, e:
            if debug.description():
                print 'cache_session_details: failed', e
                traceback.print_exc()
            return

        if schema == None:
            raise error.CommandUnAuthorized('session schema missing')
        final = result.expect_single_result()
        if final == None:
            raise error.CommandUnAuthorized('no session found')
        if not 'user-info' in final:
            raise error.CommandUnAuthorized('badly formed session response (user-info)')
        user_info = final['user-info']
        if not 'user-name' in user_info:
            raise error.CommandUnAuthorized('badly formed session response (user-name)')

        self.cached_session_cookie = session_cookie
        self.cached_session_response = final
        self.cached_session_schema = schema
        self.cached_user_name = user_info['user-name']
        self.cached_user_groups = user_info.get('group', [])


    def revoke_session(self, session_cookie = None):
        """
        Revoke the session described by the session_cookie parameter.
        If the session_cookie isn't passed in, or set to None, try to use
        the session_cookie associated with the current session.

        Return if successful, throw an exception otherwise.
        """
        if os.getenv('BIGCLI_PRESERVE_SESSION'):
            if self.cached_session_cookie and not self.bigsh.options.single_command:
                print 'Preserving Session'
            return

        # if the session cookie isn't passed in, try to find it in the bigsh.rest_api.
        if session_cookie == None:
            session_cookie = self.bigsh.rest_api.session_cookie
            if session_cookie == None:
                # no session 
                return

        base_url = 'http://localhost:8082/api/v1/data/controller/core'
        url = '%s/aaa/session[auth-token="%s"]' % (base_url, session_cookie)
        headers = { 'Cookie' : 'session_cookie=%s' % session_cookie,
                    'Content-Type':'application/json'}
        request = urllib2.Request(url, headers = headers)
        request.get_method = lambda: 'DELETE'
        try:
            response = urllib2.urlopen(request)
            revoke_result = response.read()
        except urllib2.HTTPError, e:
            if e.code in [401, 403]:
                return
            raise e
        except Exception, e:
            raise error.CommandRestError(message=str(e))
        else:
            if session_cookie == self.cached_session_cookie:
                self.cached_session_cookie = None
                self.cached_session_response = None
                self.cached_session_schema = None
                self.cached_user_name = None
                self.cached_user_groups = []


    def hash_request(self, data):
        """
        Use a REST API to hash the data value.
        """
        tail = '/api/v1/data/controller/core/aaa/hash-password[password="%s"]' % data
        url = self.controller_url() + tail
        try:
            result = self.bigsh.rest_api.rest_json_request(url)
            if len(result) != 1:
                if debug.description():
                    print 'hash_request: bad length for result:', result
                return None
            hash_result = result[0]
            return hash_result['hashed-password']
        except Exception, e:
            return None


    def schema_request(self, retry = 60):
        url = ('%s/api/v1/schema/controller' % self.controller_url())
        self.schema = {}

        # The schema is typically requested during changes in the system's
        # configuration, which can include a controller restart. 
        # be somewhat resilient to the controller not answering schema requests
        # immediatly

        self.bigdb_port = 8082
        while retry > 0:
            url = ('%s/api/v1/schema/controller' % self.controller_url())
            try:
                self.log(url)
                self.schema = self.bigsh.rest_api.rest_json_request(url, timeout = 5)
                break
            except urllib2.URLError, e:
                if isinstance(e.reason, socket.timeout):
                    self.bigdb_port = 80
                else:
                    (failure,) = e.args
                    if failure.errno == errno.ECONNREFUSED:
                        retry -= 1
                        continue

            except Exception, e:
                self.bigdb_port = 80
                try:
                    self.log(url)
                    self.schema = self.bigsh.rest_api.rest_json_request(url,
                                                                     timeout = 5)
                    break
                except urllib2.URLError, e:
                    (failure,) = e.args
                    if failure.errno == errno.ECONNREFUSED:
                        retry -= 1
                        time.sleep(1)
                        continue
                except Exception, e:
                    return

        self.log('BIGDB keys %s'% self.schema.keys())
        bigdb_type_formatter_delete('schema')
        self.crack_schema(self.schema)
        if self.validate_schema(self.schema):
            print 'Exiting due to errors in schema'
            sys.exit(1)
        if self.bigsh.options.init:
            print '!\n! BIGDB', self.schema['childNodes']['applications']['childNodes'].keys()


    def remove_key_node(self, path, data, create = False):
        query_url = ''
        schema_path = ''
        if type(data) == list:
            # should the interior items of the list be examined?
            return data
        result = dict(data)
        elements = path.split('/')
        last_element = len(elements)
        forward = {}
        for (ndx, element) in enumerate(elements, 1):
            for item in [x for x in result if is_path(x)]:
                find_path = path_adder(schema_path, item)
                (schema, items_matched) = self.schema_of_path(find_path, data)
                if schema != None:
                    forward[item] = result[item]
                    del result[item]
            # accept element...
            for item in forward.keys(): # remove any prefix names from forward
                item_parts = item.split('/')
                if item_parts[0] != element:
                    if debug.description():
                        print 'remove_key_node: forward %s without element prefix %s' % (
                            item_parts, element)
                    del forward[item_parts[0]]
                else:
                    new_index = '/'.join(item_parts[1:])
                    forward[new_index] = forward[item]
                    del forward[item]
            if create and last_element == ndx:
                break
            schema_path = path_adder(schema_path, element)
            query_url = path_adder(query_url, element)
            pk_names = self.search_keys.get(schema_path, [])
            self.log("%s %s %s" % (schema_path, pk_names, data))
            for pk_name in pk_names:
                if pk_name in result:
                    del result[pk_name]
        if forward:
            for (n,v) in forward.items():
                if n == '':
                    if len(pk_names) == 1:
                        if not pk_names[0] in result:
                            result[pk_names[0]] = v
                else:
                    result[n] = v
        return result


    def rest_url_query(self, path, data):
        """
        Given a (string) path, and a dictionary (data) of possible search keys,
        build a url to query for that item.
        """

        if data == None or len(data) == 0:
            return path

        query_url = ''
        schema_path = ''
        for element in path.split('/'):
            schema_path = path_adder(schema_path, element)
            query_url = path_adder(query_url, element)
            pk_names = self.search_keys.get(schema_path, [])
            self.log("%s %s %s" % (schema_path, pk_names, data))
            for pk_name in pk_names:
                if pk_name in data:
                    query_url = path_adder(query_url, data[pk_name])
        return query_url


    def field_attribute_value(self, field_schema, attr_name, missing_value = None):
        attributes = field_schema.get('attributes')
        if attributes:
            attribute_details = attributes.get(attr_name)
            if attribute_details:
                # there's no "type' for the value of attributes, convert T/F
                if attribute_details == 'True':
                    return True
                if attribute_details == 'False':
                    return True
                return attribute_details
        return missing_value


    def xpath_query_names(self, schema, data):
        if data == None:
            return

        def deep_names(prefix, schema, name, value):
            elements = name.split('/')
            if elements[0] != prefix:
                print self.oops(), elements, prefix, 
                return
            for element in elements[1:]:
                schema_type = schema['nodeType']
                if schema_type == 'LIST':
                    list_items = schema.get('listElementSchemaNode')
                    items = list_items.get('childNodes')
                    schema = items.get(element)
                    if schema == None:
                        break
                elif schema_type == 'CONTAINER':
                    items = schema.get('childNodes')
                    schema = items.get(element)
                    if schema == None:
                        break;
                else:
                    print self.oops(), ':need more schema_type tests here.', schema_type
            else:
                collection[name] = value

        # segregate data items into two parts: deep data items
        # which have a path element in them ('/') and the shallow items
        
        collection = {}
        schema_keys = schema.keys()
        
        for (n, v) in data.items():
            if is_path(n):
                base = n.split('/')[0]
                schema_of_base = schema.get(base)
                if schema_of_base:
                    deep_names(base, schema_of_base, n, v)
            elif n in schema_keys:
                field_details = schema.get(n)
                if field_details == None:
                    print 'xpath_query_names: field "%s" missing' % n
                    continue
                if self.field_attribute_value(field_details, 'alias', False) == True:
                    continue
                collection[n] = v

        return collection


    def rest_xpath_canonicalize_query(self, key_type, name, value):
        if key_type == 'INTEGER':
            return '[%s=%s]' % (name, value)
        else:
            return '[%s="%s"]' % (name, value)


    def rest_xpath_url(self, path, data, oper):
        """
        Given a (string) path, and a dictionary (data) of possible search keys,
        build an xpath url to query for that item.

        The returned valus is a four tuple: (path, select_items, forward_data, depth)
        path is the resulting path,
        select_items is a list of strings, representing select string items.
            These may take the form: '<path>[<name>=<value>]'
        forward_data are items from data, for example bvs/name, which are
           updated to become 'name' : XXX, for paths which don't consume
           the 'name' item.  These are then refactored names.
        depth is the number of unconstrained key values in the query.

        oper is one of 'create', 'update', 'replace', 'query', or 'delete'.  The resulting
        xpath query will be different depending on the oper.
        """

        if debug.description():
            print 'rest_xpath_url: path:', path, 'data:', data, oper

        if oper not in ['create', 'update', 'replace', 'query', 'delete']:
            raise CommandInternalError('rest_xpath_url: bad oper parameter %s' % oper)
            
        schema = self.schema
        result_depth = 0
        # forward is a dictionary of name value pairs, where the name is
        # a path for an item which has not yet appeared, but will appear
        # in the path later (its been identified as a deeper element in
        # the schema.  as each element is used, the names in this table
        # are modified.
        forward = {}
        original_reference = {} # map from forward item to updated data index
                                # items are moved from updated_data only
                                # when the forward item is consumed, or
                                # when the forward item is left 
        updated_data = dict(data) # to allow fowward items to be removed

        query_url = ''
        schema_path = ''
        select_items = []
        prev_element = None
        for element in path.split('/'):
            # skip multiple path separators
            if element == '':
                continue

            schema_type = schema['nodeType']

            # Until xpath is complete, there's some switch queries
            # which need to use core/switch/<dpid>/ instead of [dpid=<>]

            query_items = ''
            if schema_type == 'LIST':
                list_items = schema.get('listElementSchemaNode')

                items = list_items.get('childNodes')
                keys = list_items.get('keyNodeNames')
                single_key = True if keys and len(keys) == 1 else False

                # not sure it makes sense to separete the keys from
                # data item lookups anymore.
                if keys:
                    incomplete_keys = []
                    if '' in forward:
                        if len(keys) == 1:
                            if type(forward['']) != str:
                                if debug.description():
                                    print 'rest_xpath_url: forward value', schema_path, forward['']

                            key = keys[0]
                            key_type = items[key]['typeSchemaNode']['leafType']
                            query_items += self.rest_xpath_canonicalize_query(key_type,
                                                                              key,
                                                                              forward[''])
                            del forward['']
                            del updated_data[original_reference['']]
                            del original_reference['']
                            keys = []
                        else:
                            forward_values = forward['']
                            del forward['']
                            del updated_data[original_reference['']]
                            del original_reference['']
                            updated_keys = list(keys)
                            for key in keys:
                                type_node = items[key]['typeSchemaNode']
                                key_type = type_node['leafType']
                                if key in forward_values:
                                    query_items += self.rest_xpath_canonicalize_query(key_type,
                                                                                      key,
                                                                                      forward_values[key])
                                    updated_keys = [x for x in updated_keys if x != key]
                            keys = updated_keys

                    for key in keys:
                        type_node = items[key]['typeSchemaNode']
                        key_type = type_node['leafType']
                        if key in updated_data:
                            query_items += self.rest_xpath_canonicalize_query(key_type,
                                                                              key,
                                                                              updated_data[key])
                            del updated_data[key]

                        # it certainly seems that the key ought to be looked up
                        # in 'forwad', but 'element' is the next element, not
                        # the currene one, which means the foward path needs to be
                        # consumed to mtch this part of the path.  and for path's
                        # like 'core', there' no '/' in the path name.
                        elif key in forward:
                            query_items += self.rest_xpath_canonicalize_query(key_type,
                                                                              key,
                                                                              forward[key])
                            # now remove this item from the updated data.
                            del forward[key]
                            del updated_data[original_reference[key]]
                            del original_reference[key]
                        else:
                            incomplete_keys.append(key)
                    else:
                        # foreign key references, XXX improve
                        if prev_element and prev_element in updated_data and single_key:
                            key = keys[0]
                            # XXX key_type is wrong here.
                            query_items += self.rest_xpath_canonicalize_query(key_type,
                                                                              key,
                                                                              data[prev_element])
                            # remove key since previous loop added it
                            if key in incomplete_keys:
                                incomplete_keys = [x for x in incomplete_keys if x != key]
                        else:
                            # key will alreay be in incomplete_keys from the
                            # first 'for key in keys' loop.
                            pass

                    if incomplete_keys:
                        result_depth += 1
                else: # no keys for path
                    if '' in forward:
                        print ': forward-path not found', schema_path, original_reference['']
                        del forward['']
                        del original_reference['']

                # find items which may match.
                for (n, v) in self.xpath_query_names(items, updated_data).items():
                    if n in keys: # already included
                        continue
                    if is_path(n):
                        # won't match at this layer, may match further down
                        forward[n] = v
                        original_reference[n] = n
                        continue
                    elif items[n]['nodeType'] == 'LEAF':
                        type_node = items[n]['typeSchemaNode']
                    elif items[n]['nodeType'] == 'LEAF_LIST':
                        type_node = items[n]['leafSchemaNode']['typeSchemaNode']
                    key_type = type_node['leafType']
                    if key_type == 'INTEGER':
                        query_items += '[%s=%s]' % (n, v)
                        del updated_data[n]
                    else:
                        query_items += '[%s="%s"]' % (n, v)
                        del updated_data[n]
                schema = items.get(element)
                if schema == None:
                    # XXX exception?
                    print 'rest_xpath_url: failed path: %s, ' \
                           'no element %s, fields %sh' % (
                            query_url, element, items.keys())
                    break
                schema_type = schema['nodeType']
            elif schema_type == 'CONTAINER':
                items = schema.get('childNodes')
                if element not in items:
                    # XXX exception?
                    print 'xpath: element not in schema', element, items.keys()
                    break
                schema = items.get(element)
                schema_type = schema['nodeType']

                for (n, v) in self.xpath_query_names(items, updated_data).items():
                    if is_path(n):
                        # won't match at this layer, may match further down
                        forward[n] = v
                        original_reference[n] = n

                # the query_item needs appending to this element.
                #for (n, v) in self.xpath_query_names(items, data).items():
                    #query_items += '[%s="%s"]' % (n, v)
            else:
                print self.oops(), ':need more schema_type tests here.'. schema_type

            query_url += query_items
            schema_path = path_adder(schema_path, element)
            query_url = path_adder(query_url, element)
            prev_element = element
            for (fw, fw_value) in forward.items():
                # 'element' ought to be a prefix in each of the names.
                fw_parts = fw.split('/')
                if fw_parts[0] != element:
                    if debug.description():
                        print 'xpath: forward prefix <%s>:%s %s doesn\'t match element <%s> of %s' % (
                                fw_parts, fw, original_reference[fw], element, path)
                    del forward[fw]
                    del updated_data[original_reference[fw]]
                    del original_reference[fw]
                else:
                    new_name = '/'.join(fw_parts[1:])
                    # should ensure the new name is not in the forward list
                    forward[new_name] = fw_value
                    del forward[fw]
                    original_reference[new_name] = original_reference[fw]
                    del original_reference[fw]

        # at the last element, more query items may still be needed.
        if schema_type == 'LIST':
            if oper != 'create':
                list_items = schema.get('listElementSchemaNode')
                items = list_items.get('childNodes')

                keys = list_items.get('keyNodeNames')
                if not keys:
                    # print 'No key for last', path, element
                    result_depth += 1
                

                query_items = ''
                # code under if in next section ought to be factored out.
                if '' in forward:
                    if len(keys) == 1:
                        if type(forward['']) != str:
                            print ': forward value', schema_path, forward['']

                        key = keys[0]
                        key_type = items[key]['typeSchemaNode']['leafType']
                        query_items += self.rest_xpath_canonicalize_query(key_type,
                                                                          key,
                                                                          forward[''])
                        del forward['']
                        del updated_data[original_reference['']]
                        del original_reference['']
                        keys = []
                    else:
                        forward_values = forward['']
                        del forward['']
                        del updated_data[original_reference['']]
                        del original_reference['']
                        updated_keys = list(keys)
                        for key in keys:
                            type_node = items[key]['typeSchemaNode']
                            key_type = type_node['leafType']
                            if key in forward_values:
                                query_items += self.rest_xpath_canonicalize_query(key_type,
                                                                                  key,
                                                                                  forward_values[key])
                                updated_keys = [x for x in updated_keys if x != key]
                        keys = updated_keys

                if keys:
                    for key in keys:
                        if not key in data and not key in forward:
                            # print 'No key value for last', path, key, data, query_url
                            result_depth += 1
                            break

                for (n,v) in self.xpath_query_names(items, forward).items():
                    # skip any forward items which are deeper.
                    if is_path(n):
                        (path_schema, path_items) = self.schema_of_path(path_adder(schema_path, n) , {})
                        if path_schema:
                            item_schema = path_schema
                            item_type = path_schema['nodeType']
                            (select_prefix, _, select_name) = n.rpartition('/')
                            # this will look like select=<select_prefix>[<n>=<v>]
                            select_items.append("%s%s" % ( select_prefix,
                                self.rest_xpath_canonicalize_query(item_type, select_name, v)))
                            # continue to add the query to the path
                        else:
                            continue # no schema at this path
                    else: # shallow, single step into the schema.
                        item_schema = items[n]
                    if item_schema['nodeType'] == 'LEAF':
                        type_node = item_schema['typeSchemaNode']
                    elif item_schema['nodeType'] == 'LEAF_LIST':
                        type_node = item_schema['leafSchemaNode']['typeSchemaNode']
                    elif item_schema['nodeType'] == 'LIST':
                        list_elements_schema = item_schema['listElementSchemaNode']
                        keys = list_elements_schema['keyNodeNames']
                        use_v = v
                        child_schemas = list_elements_schema['childNodes']
                        if len(keys) > 1:
                            if type(v) != dict:
                                print 'rest_xpath_url: multiple keys requires dict for values'
                            else: # look up the last key in the dictionary
                                if keys[-1] in v: # the key is in the dictionary
                                    use_v = v[keys[-1]]
                                elif path_adder(path, n) in v: # complete path
                                    use_v = v[path_adder(path, n)]
                                else:
                                    print 'rest_xpath_url: dict missing last key %s for path %s:%s' % \
                                            (key[-1], path, v)

                        type_node = child_schemas[keys[-1]]['typeSchemaNode']
                        # if there's more keys, add them here first
                        if len(keys) > 1:
                            for key in keys[:-1]:
                                # all keys must exist.
                                other_type_node = child_schemas[key]['typeSchemaNode']
                                key_type = other_type_node['leafType']
                                if key in v: # the key is in the dictionary
                                    this_v = v[key]
                                elif path_adder(path, key) in v: # path in the dictionary
                                    this_v = v[path_adder(path, key)]
                                else:
                                    print 'rest_xpath_url: dict missing key %s for path %s:%s' % \
                                            (key, path, v)
                                    
                                query_items += self.rest_xpath_canonicalize_query(key_type, key, this_v)
                        v = use_v
                    else:
                        print 'rest_xpath_url: NEED TYPE FOR', item_schema['nodeType']
                    key_type = type_node['leafType']
                    query_items += self.rest_xpath_canonicalize_query(key_type, n, v)
                    del forward[n]
                    del updated_data[original_reference[n]]
                for (n, v) in self.xpath_query_names(items, updated_data).items():
                    # is it possibly to add itesm to forw?
                    if items[n]['nodeType'] == 'LEAF':
                        type_node = items[n]['typeSchemaNode']
                    elif items[n]['nodeType'] == 'LEAF_LIST':
                        type_node = items[n]['leafSchemaNode']['typeSchemaNode']
                    else:
                        print 'NEED TYPE FOR',items[n]['nodeType']
                    key_type = type_node['leafType']
                    query_items += self.rest_xpath_canonicalize_query(key_type, n, v)
                    del updated_data[n]

                query_url += query_items
            else: # remove any last forward elements of forward within updated_data
                for (n,v) in forward.items():
                    del updated_data[original_reference[n]]
                if '' in forward:
                    # this must be the key for this path, so set the name of the
                    # item to the key name.
                    list_elements_schema = schema['listElementSchemaNode']
                    keys = list_elements_schema['keyNodeNames']
                    if len(keys) == 1:
                        key = keys[0]
                        forward[key] = forward['']
                        del forward['']
        else: # remove any last forward elements of forward within updated_data
            # node_types other than LIST
            for (n,v) in forward.items():
                del updated_data[original_reference[n]]

        updated_data.update(forward)

        return (query_url, select_items, updated_data, result_depth)
    

    def data_rest_request(self, path, filter = None, select = None, single = None):
        (xpath, si, forw_data, depth) = self.rest_xpath_url(path, filter if filter else {}, 'query')
        # forw_data isn't part of the search.
        # simply ignore forw data for now, would be better to give some warning
        url = '%s/api/v1/data/controller/%s' % (self.controller_url(), xpath)

        if single and depth == 0:
            # (deoth == 0) only honor single when the query is completly constrained.
            single = 'single=true'
        else:
            single = ''

        def discard_si(select, si):
            # if any of the si' items are more general version of the
            # requested select, then discard those in favor of the 
            # requested select.
            if select.startswith('select='):
                select_parts = select.split('=')[1].split('[')
            else:
                select_parts = select.split('[')
            select_prefix = select_parts[0]
            # each of these will only be a path and matches.
            return [x for x in si
                    if not select_prefix.startswith(x.split('[')[0])]

        # this is horrible, there's not enough consistency for the allowable parameters
        # for select.  Then having to manage addition of `single' further
        # complicates this code.  some audit of the call sites could help, giving
        # clues to how 'select' is used, possibly select as a dictionary could also.
        # some of the complexity comes from '?' separaring query params and '&' joining them
        if select:
            if single != '':
                single = '&' + single
            if string_type(select):
                if select.startswith('select='):
                    # XXX attempt to remove duplicates
                    si = [x for x in si if x != select]
                    url += '?%s%s%s' % (select, select_items(si, add = True), single)
                elif select.startswith('config='):
                    url += '?%s%s' % ('&select='.join([select]+si), single)
                else:
                    # XXX attempt to remove duplicates
                    si = discard_si(select, si)
                    si = [x for x in si if x != select]
                    url += select_items([select] + si) + single
            elif type(select) == list:
                url += '?select=%s%s' % ('|'.join(select) + select_items(si, add = True), single)
            elif type(select) == dict:
                url += '?select=%s%s' % ('|'.join(select.keys()) + select_items(si, add = True), single)
        elif si:
            if single:
                single = '&' + single
            url += select_items(si) + single
        elif single != '':
            url += '&' + single

        self.log('DATA_REST_REQUEST url %s' % url)
        result = url_cache.get_cached_url(url)
        # exceptions may occur here
        if result == None:
            rest_result = self.bigsh.rest_api.rest_simple_request(url)
            if rest_result == '':
                return BigDB_result(0, None)
            result = json.loads(rest_result)
            url_cache.save_url(url, result)

        return BigDB_result(depth, result)


    def exists(self, path, data, max_depth = None, config = None):
        """
        @param path the bigdb path to query
        @param data filtering for the query
        @param max_depth this describes the maximum stack depth from the mode stack.
                         when unset, it defaults to None, which means the complee stack.
                         to not process the any elements from the stack, set max_depth = 0
        """
        filter = data
        if max_depth == None or max_depth: # none means all, 0 means none.
            filter = dict(data)
            self.add_mode_stack_paths(filter, max_depth = max_depth)

        keys = self.search_keys.get(path)
        pick = []
        if config == None or config == True:
            pick.append('config=true')
        if keys and len(keys) == 1:
            pick.append('select=%s' % keys[0])

        try:
            result = self.data_rest_request(path, filter, '&'.join(pick), single = True)

        except urllib2.HTTPError, e:
            if debug.description():
                print 'exists: failed ', e
            return

        if result.get() == None:
            return False
        final = result.expect_single_result()
        if final == None:
            return False

        if keys:
            if debug.description():
                print 'exists:', path, data, keys, final, type(final), result.builder()
            for key in keys:
                if string_type(final):
                    if key != final:
                        return False
                elif not key in final or final[key] == None:
                    return False
            return True
        return True if len(final) else False


    def field_in_result(self, schema, result, field):
        """
        Generator, recursive descent, which takes a field
        operpossibly a field path, for example attachment-point/mac),
        and yields all values for that field within the results.

        The yield-values are tuples.  the second element is the
        associated key-name.
        """

        if result == None:
            return

        if not is_path(field):    # shallow
            if field in result:
                yield (result[field],)
        else:                     # deep
            elements = field.split('/')
            for (index, element) in enumerate(elements, 1):
                if schema == None:
                    return
                schema_type = schema['nodeType']

                if schema_type == 'LIST':
                    list_items = schema.get('listElementSchemaNode')
                    keys = schema.get('keyNodeNames')
                    items = list_items.get('childNodes')
                    next_schema = items.get(element)
                    if element not in result:
                        continue
                    if string_type(result.get(element)):
                        yield (result.get(element), )
                        return
                    for item in result.get(element):
                        remaining_path = '/'.join(elements[index:])
                        node_type = next_schema['nodeType']
                        if node_type == 'LEAF' or node_type == 'LEAF_LIST':
                            yield (item,)
                        else:
                            if debug.description():
                                print 'RECURSE', remaining_path, len(remaining_path), item, type(item), next_schema['nodeType']
                            key_value = None
                            if keys:
                                key_value = ' '.join([str(item[x]) for x in keys])
                            for found in self.field_in_result(next_schema, item,
                                                              remaining_path):
                                if type(found) == tuple:
                                    yield found
                                elif len(found) == 1 and key_value:
                                    yield (found, key_value)
                                else:
                                    yield (found,)
                    return
                # LEAF-LISTS would return all values from the list
                elif schema_type == 'CONTAINER':
                    items = schema.get('childNodes')
                    schema = items.get(element)
                    result = result.get(element)
                    if result == None:
                        return
                elif schema_type == 'LEAF':
                    if string_type(result):
                        yield (result, )
                    else:
                        print self.oops(), schema_type, type(result)
                else:
                    print self.oops(), schema_type


    def completions(self, completions, path, field, prefix, filter,
                    is_no_command = False, mode_switch = None):
        """
        Return field values from the path starting with 'prefix'.
        Used to generate completion results.

        If the alias parameter is included, if the field is one of
        the search keys for the path, then the named alias field
        is replaced for the item when it in the result

        Needs improvement, too simplistic.
        """

        # once 'select' arrives for the querires, this specific 
        # field can be chosen to return.

        try:
            (schema, results) = self.schema_and_result(path, filter)
            if debug.description():
                print 'bigdb.completions:', path, filter, field, prefix, results.result, mode_switch
        except Exception, e:
            # 403: permissin denied is possible here, if it happens,
            # other completions may still be valuable.
            if debug.description():
                print 'completion: error:', e
            return
            
        if (schema == None) or (results.get == None):
            return
        
        if mode_switch == None:
            mode_switch = ''

        node_type = schema['nodeType']
        keys = self.search_keys.get(path, [])

        # if field is a suffix for path, remove the prefix
        field_name = field
        if is_path(field):
            if field.startswith(path + '/'):
                new_field = str(field).replace(path + '/', '')
                if not is_path(new_field):
                    field_name = new_field

        col_header = self.column_header.get(path)
        if col_header == None:
            col_header = self.column_header.get(path_adder(path, field_name),
                                                field_name.capitalize())
        alias = self.alias_field.get(path)
        if alias == None:
            alias = self.alias_field.get(path_adder(path, field_name))

        for result in results.iter():
            if field_name in keys:
                result_key = ' '.join([str(result[x]) for x in keys]) 
                if alias and alias in result and result[alias].startswith(prefix):
                    alias_value = result[alias]
                    completions[alias_value + ' '] = '%s alias selection for %s%s' %\
                                                     (col_header, result_key, mode_switch)
                elif result_key.startswith(prefix):
                    # use the field's key values for completion
                    completions[result_key + ' '] = '%s selection%s' % (col_header, mode_switch)
            elif node_type == 'LEAF_LIST' :
                # a scoped query will return the existing choices.
                if type(result) == list:
                    # try to find the column header?
                    for item in result:
                        if item.startswith(prefix):
                            completions[item + ' '] = '%s selection%s' % (col_header, mode_switch)
                elif string_type(result):
                    if result.startswith(prefix):
                        completions[result + ' '] = '%s selection%s' % (col_header, mode_switch)
            elif node_type == 'LIST' and type(result.get(field_name)) == list:
                for field_result in result[field]:
                    # likely need to include alias replacement details. 
                    # alias management?  likely need some indication this is a path-reference
                    # and then use that path-reference for alias replacement.
                    if field_result.startswith(prefix):
                        completions[field_result + ' '] = '%s selection%s' % (col_header, mode_switch)
            else:
                # need 'column header' for path.
                alias_id = col_header
                if alias:
                    for fr in self.field_in_result(schema, result, alias):
                        if len(fr) == 1:
                            found = fr[0]
                        else:
                            (found, alias_id) = fr
                        if found.startswith(prefix):
                            if found != '' : # BSC-3059
                                if alias_id == None or field_name == alias_id:
                                    completions[found + ' '] = '%s alias selection%s' % \
                                                                (field_name, mode_switch)
                                else:
                                    completions[found + ' '] = '%s alias selection: %s%s' % \
                                                                (field_name, alias_id, mode_switch)
                                if debug.description():
                                    print 'BIGDB COMPLETIONS FOUND alias', alias, found
                for fr in self.field_in_result(schema, result, field):
                    if len(fr) == 1:
                        found = fr[0]
                    else:
                        (found, alias_id) = fr
                    found_str = str(found)
                    if found_str.startswith(prefix): # str(): type may be integer
                        if found_str != '' : # BSC-3059
                            if alias_id == None or field_name == alias_id:
                                completions[found_str + ' '] = '%s selection%s' % \
                                                               (field_name, mode_switch)
                            else:
                                completions[found_str + ' '] = '%s selection of %s%s' % \
                                                               (field_name, alias_id, mode_switch)


    def alias_key_value(self, path, field, filter):
        #
        # remove any fields which don't pass validation for the path.
        # these values are borrowed from possibly unrelated data.
        filter = dict(filter)
        for (n,v) in filter.items():
            try:
                self.validate_fields_of_path(path, { n: v })
            except Exception, e:
                del filter[n]

        (schema, results) = self.schema_and_result(path, filter, select = 'alias')
        if schema == None:
            return 
        if results == None:
            return # not iterable

        keys = self.search_keys.get(path)
        if keys == None:
            # print 'AKV NoKey', keys, path
            return
        for result in results.iter():
            for key in keys:
                if key not in result:
                    continue
            keys_id = ' '.join([str(result[x]) for x in keys])
            # BSC-2292
            af = self.alias_field.get(path)
            if af and af in result:
                yield (keys_id, result[af])


    def select_is_valid(self, schema, select):
        """
        Predicate, validates the select for the schema.
        schema is required to point at the first matching item from the select,
        If, for example, select does not contains '/', then select
        would typically be in schema (schema[select] doesn't fault)

        (possibly change from a predicate to return something more valuable?)

        Very similar to xpath_query_names, but validates the path
        in the select is part of the schema.
        """
        if schema == None or select == None:
            return False 

        def deep_names(prefix, schema, name):
            elements = name.split('/')
            if elements[0] != prefix:
                print self.oops(), elements, prefix, 
                return
            for element in elements[1:]:
                schema_type = schema['nodeType']
                if schema_type == 'LIST':
                    list_items = schema.get('listElementSchemaNode')
                    items = list_items.get('childNodes')
                    schema = items.get(element)
                    if schema == None:
                        return False
                elif schema_type == 'CONTAINER':
                    items = schema.get('childNodes')
                    schema = items.get(element)
                    if schema == None:
                        return False
                elif schema_type == 'LEAF':
                    return element in schema
                else:
                    print self.oops(), ':need more schema_type tests here.', schema_type
            else:
                return True

        # segregate data items into two parts: deep data items
        # which have a path element in them ('/') and the shallow items
        
        collection = {}
        schema_keys = schema.keys()

        # convert 'query matching components' into a path name check.
        if select.find('[') > -1:
            # XXX assumes this looks like: '<path>[<field>="value"]'
            # XXX doesn't manage multiple items.
            # XXX select=<query-path>, including [] predicates
            select_parts = select.split('[')
            test_parts = select_parts[1].split('=')
            select = path_adder(select_parts[0], test_parts[0])

        if is_path(select):
            base = select.split('/')[0]
            schema_of_base = schema.get(base)
            if schema_of_base:
                return deep_names(base, schema_of_base, select)
        elif select in schema_keys:
            return True
        return False

 
    def schema_and_result(self, path, filter, select = None):
        """
        Return matching schema and result for passed in path,
        withL the filter parameters.
        """

        (schema, items_matched) = self.schema_of_path(path, filter)
        if schema == None:
            if debug.description():
                print 'schema_and_result: No schema for', path
            return (None, None)

        # validate requested select is for a field within the schema.
        if select:
            if select.startswith('config='):
                # allow select to also say 'config=true'
                pass
            else:
                schema_type = schema.get('nodeType')
                if schema_type == 'LIST':
                    list_node = schema.get('listElementSchemaNode')
                    child_nodes = list_node.get('childNodes')
                    if not self.select_is_valid(child_nodes, select):
                        if debug.description():
                            print 'SELECT NOT FOUND', select
                        return (None, None)
                else:
                    print 'schema_and_result: need more node-type validation'

        result = self.data_rest_request(path, filter, select)
        if result == None:
            return (schema, None)

        #if items_matched and result:
            #result = [ result ] 

        if 0 and items_matched:    # envelope the result
            key = ''.join([filter[x] for x in items_matched])
            # oddly enough, yang LISTS are dictionaries.
            result = { key : result }

        self.log('schema_and_result: path %s' % path)

        return (schema, result)


    def put(self, path, data, query = None, oper = None):
        """
        Put is intended to create objects, because of that the search
        path shouldn't include the "last" query object -- it doesn't
        exist
        """
        if query == None:
            query = data
            apply_data = self.remove_key_node(path, data)
            use_forw_data = True
        else:
            apply_data = data
            use_forw_data = False

        if oper == None:
            oper = 'create'

        (xpath, si, forw_data, depth) = self.rest_xpath_url(path, query, oper)
        if use_forw_data and forw_data:
            apply_data.update(forw_data)
            if debug.cli():
                print 'bigdb.put: add forw_data %s for %s' % (forw_data, apply_data)

        url = ('%s/api/v1/data/controller/%s%s' % (
                        self.controller_url(), xpath, select_items(si)))

        if debug.cli():
            print 'PUT', url, apply_data
        rest_result = self.bigsh.rest_api.rest_post_request(url, apply_data)


    def patch(self, path, data, query = None):
        """
        """
        if query == None:
            query = data
            apply_data = self.remove_key_node(path, data)
        else:
            apply_data = data
            

        (xpath, si, forw_data, depth) = self.rest_xpath_url(path, query, 'update')
        #(xpath, forw_data, depth) = self.rest_xpath_url(path, query, 'create')
        if type(apply_data) == list:
            if forw_data and len(apply_data) > 1:
                print 'bigdb:patch: data-list %s with forw_data %s' % (apply_data, forw_data)
            else:
                if type(apply_data[0]) == dict:
                    apply_data[0].update(forw_data)
        else: # assume this is a dictionry
            apply_data.update(forw_data)

        if debug.cli():
            print 'bigdb.patch: add forw_data %s for %s' % (forw_data, apply_data)

        url = ('%s/api/v1/data/controller/%s%s' % (
                    self.controller_url(), xpath, select_items(si)))

        if debug.cli():
            print 'PATCH', url, apply_data
        rest_result = self.bigsh.rest_api.rest_post_request(url, apply_data, verb='PATCH')


    def post(self, path, data, query = None, cookieAuth=True, appAuthFile=None):
        """
        """
        if query == None:
            query = data
            apply_data = self.remove_key_node(path, data, create = True)
            use_forw_data = True
            oper = 'create'
        else:
            apply_data = data
            use_forw_data = False
            oper = 'query'

        (xpath, si, forw_data, depth) = self.rest_xpath_url(path, query, oper)
        if use_forw_data:
            if debug.description():
                print 'BIGDB POST: set apply_data to %s from %s' % (forw_data, apply_data)
            apply_data = forw_data
            # is this needed for other queries?
            self.canonicalize_values(path, apply_data)

        url = ('%s/api/v1/data/controller/%s%s' % (
                    self.controller_url(), xpath, select_items(si)))
        rest_result = self.bigsh.rest_api.rest_post_request(url,
                                                         apply_data,
                                                         verb='POST',
                                                         cookieAuth=cookieAuth,
                                                         appAuthFile=appAuthFile)


    def delete(self, path, data, query = None):
        """
        """
        if query == None:
            query = data
            apply_data = self.remove_key_node(path, data)
            use_forw_data = True
        else:
            apply_data = data
            use_forw_data = False

        (xpath, si, forw_data, depth) = self.rest_xpath_url(path, query, 'delete')
        if use_forw_data and forw_data:
            apply_data.update(forw_data)
            if debug.cli():
                print 'bigdb.delete: add forw_data %s for %s' % (forw_data, apply_data)

        url = ('%s/api/v1/data/controller/%s%s' % (
                    self.controller_url(), xpath, select_items(si)))

        if debug.cli():
            print 'DELETE', url, apply_data
        rest_result = self.bigsh.rest_api.rest_post_request(url, apply_data, verb='DELETE')


    def validate_value(self, validations, value):
        # determine whether all the entries in the validation's are lists
        for validation in validations:
            if type(validation) != list:
                break
        else:
            # these are alternataions from union types, one of these
            # must match.  if they all fail, the validation fails.
            collected_failures = []
            for validation in validations:
                try:
                    self.validate_value(validation, value)
                    break

                except Exception, e:
                    # only collect the detail between the first and second ':'
                    e_str = str(e)
                    collected_failures.append(''.join(e_str.split(':')[1:-1]))
            else:
                raise error.ArgumentValidationError('Every validation failed:%s'
                                                    % ','.join(collected_failures))
            return
                

        for validation in validations:
            validation_type = validation.get('type')
            if validation_type == 'LENGTH_VALIDATOR':
                ranges = validation.get('ranges')
                for range in ranges:
                    if len(value) < range['start'] or len(value) > range['end']:
                        raise error.ArgumentValidationError('field length:'
                                   ' expected [%s..%s] got: ' % 
                                   (range['start'], range['end'], len(value)))
            elif validation_type == 'RANGE_VALIDATOR':
                ranges = validation.get('ranges')
                # could be factored out, won't save much code.
                for range in ranges:
                    if int(value) < range['start'] or int(value) > range['end']:
                        raise error.ArgumentValidationError('range:'
                                   ' expected [%s..%s] got ' % 
                                   (range['start'], range['end'], len(value)))
            elif validation_type == 'PATTERN_VALIDATOR':
                pattern = validation.get('pattern')
                if pattern:
                    if pattern[-1] != '$': # need to match complete item
                        pattern += '$'
                    if not re.match(pattern, value):
                        if 'name' in validation:
                            raise error.ArgumentValidationError('syntax of %s: %s'
                                       % (validation['name'], value))
                        else:
                            raise error.ArgumentValidationError('syntax:'
                                       ' expected %s for: %s ' % (pattern, value))
            elif validation_type == 'ENUMERATION_VALIDATOR':
                names = validation.get('names')
                if names:
                    lower_names = [x.lower() for x in names.keys()]
                    if not value.lower() in lower_names:
                        raise error.ArgumentValidationError('value: choices:'
                                   ' %s for %s ' % (', '.join(lower_names), value))
            else:
                # XXX internal error
                print ('validate_value: unknown validation type %s' % (validation_type,))


    def validate_fields_of_path(self, path, fields):
        """
        Validate the (dict) fields for the path, by
        appending dictionary names to the path, identifying
        the validation function, then applying that
        validation to the value.
        """

        if debug.description():
            print 'validate_fields_of_path:', path, fields

        for (field_name, field_value) in fields.items():
            if field_value == None:
                if debug.description():
                    print 'Validation error: %s %s %s' % (path, field_name, field_value)
                raise error.ArgumentValidationError('Expecting value for %s' % field_name)
            field_path = path_adder(path, field_name)
            allow_empty_string = self.allow_empty_string.get(field_path)
            if allow_empty_string and field_value == '':
                continue
            validation = self.path_validator.get(field_path)
            if not validation:
                validation = self.path_validator.get(field_name)
            if validation:
                self.validate_value(validation, field_value)

        # if fields contains a partial path for 'path', rest_xpath_url will 
        # identify tese.
        # can't hurt to validate a field twice
        (x_path, x_si, x_data, x_depth) = self.rest_xpath_url(path, fields, 'create')
        if x_data:
            for (field_name, field_value) in x_data.items():
                if field_value == None:
                    if debug.description():
                        print 'Validation error: %s %s %s' % (path, field_name, field_value)
                    raise error.ArgumentValidationError('Expecting value for %s' % field_name)
                field_path = path_adder(path, field_name)
                allow_empty_string = self.allow_empty_string.get(field_path)
                if allow_empty_string and field_value == '':
                    continue
                validation = self.path_validator.get(field_path)
                if validation:
                    self.validate_value(validation, field_value)
            


    def canonicalize_values_of_path(self, path, fields):
        """
        True/False and integers need to be type-changed from strings into
        their values to match the types of leaf's
        """
        (schema, items_matched) = self.schema_of_path(path, {})
        if schema == None:
            print 'canonicalize_values_of_path: no schema for', path
            return
        node_type = schema['nodeType']
        if node_type == 'LIST':
            list_node = schema.get('listElementSchemaNode')
            child_nodes = list_node.get('childNodes')
        elif node_type == 'CONTAINER':
            child_nodes = schema.get('childNodes')
        elif node_type == 'LEAF_LIST':
            leaf_node = schema.get('leafSchemaNode')
            # need code here to validate the fields against the leaf list
            if debug.description():
                print 'canonicalize_values_of_path:', fields
            return
        else:
            print 'canonicalize_values_of_path: unknown type', node_type

        # More work here for field name which are path's.
        for (field_name, field_value) in fields.items():
            field_details = child_nodes.get(field_name)
            if field_details and field_details.get('nodeType') == 'LEAF':
                leaf_type = field_details.get('leafType')
                if leaf_type == 'BOOLEAN':
                    if field_value == 'True' or field_value == True:
                        fields[field_name] = True
                    if field_value == 'False' or field_value == False:
                        fields[field_name] = False
                elif leaf_type == 'INTEGER' and type(field_value) != int:
                    try:
                        fields[field_name] = int(field_value)
                    except:
                        pass


    def canonicalize_values(self, path, data, schema = None, boolean_value = True):
        if schema == None:
            (schema, items_matched) = self.schema_of_path(path, {})
            if schema == None:
                print 'canonicalize_values: no schema for', path
                return
            node_type = schema['nodeType']
            if node_type == 'LIST':
                schema = schema['listElementSchemaNode']['childNodes']
            elif node_type == 'CONTAINER':
                schema = schema['childNodes']

        # segregate items into two parts, fields at this level
        # and nested items.

        local_items = [x for x in data.keys() if not is_path(x)]
        # first start with path's (nested items)
        path_dict = {}  # collect together prefix of same 1st element
        complete_path = {}

        for name in [x for x in data.keys() if is_path(x)]:
            name_parts = name.split('/', 1)
            value = data[name]
            del data[name]
            prefix = name_parts[0]

            # if 'name' is a complete path, not relative, deal with these last
            (path_schema, items_matched) = self.schema_of_path(name, {})
            if path_schema and name_parts[0] not in schema:
                # need a better "startswith" operator for paths.
                if name.startswith(path) and name.replace(path, '')[0] == '/':
                    name_parts = name.replace(path, '').split('/', 2)
                    prefix = name_parts[1]
                    # much deeper than path, or local
                    if len(name_parts) > 2:
                        if not prefix in path_dict:
                            path_dict[prefix] = {}
                        path_dict[prefix][name_parts[2]] = value
                    else:   # local to path
                        local_name = prefix
                        local_items.append(prefix)
                        data[prefix] = value
                else:
                    if debug.description():
                        print 'canonicalize_values: trouble with', name, path
                continue

            if not prefix in path_dict:
                path_dict[prefix] = {}
            path_dict[prefix][name_parts[1]] = value


        # if path_dict has elements, iterate over them.
        for (prefix, child_data) in path_dict.items():
            if prefix in schema:
                node_type =  schema[prefix]['nodeType']
                if node_type == 'LEAF' or node_type == 'LEAF_LIST':
                    child_data = {prefix: child_data}
                    child_schema = schema
                elif node_type == 'LIST' or node_type == 'CONTAINER':
                    child_schema = schema[prefix]['childNodes']
                    child_data = dict(child_data)
                else:
                    print 'canonicalize_values: unknown type %s for %s ' % (node_type, path)
                    continue

                self.canonicalize_values(path_adder(path, prefix),
                                         child_data,
                                         child_schema)
                if node_type == 'LEAF' or node_type == 'LEAF_LIST':
                    data[prefix] = child_data[prefix]
                elif node_type == 'LIST' or node_type == 'CONTAINER':
                    data[prefix] = child_data
            else:
                if debug.description():
                    print 'canonicalize_values: missing:', prefix, child_data

        for name in local_items:
            value = data[name]
            if name in schema:
                name_schema = schema[name]
                type_node = name_schema['nodeType']
                if type_node == 'LEAF':
                    leaf_type = name_schema['typeSchemaNode']['leafType']
                    leaf_type_name = name_schema['typeSchemaNode']['name']
                    if leaf_type == 'INTEGER':
                        data[name] = int(value)
                    elif leaf_type == 'BOOLEAN':
                        if data[name] not in [True, False]:
                            data[name] = boolean_value
                elif type_node == 'LEAF_LIST':
                    leaf_schema = name_schema['leafSchemaNode']
                    leaf_type = leaf_schema['typeSchemaNode']['leafType']
                    leaf_type_name = name_schema['typeSchemaNode']['name']
                    # possibly need a predicate for types which represent
                    # comma separated list of integers or ranges.
                    if leaf_type_name == 'vlan-range-string':
                        # comma separated list of integers or ranges
                        pass
                    if type(data[name]) != list:
                        data[name] = [value]
            else:
                print 'canonicalize_values: no schema for ', path, name


    def canonicalize_values_for_delete(self, path, data, select, schema = None):
        """
        Return the operation to perform, post or delete.
        """
        oper = 'DELETE'
        if schema == None:
            (schema, items_matched) = self.schema_of_path(path, {})
            if schema == None:
                print 'canonicalize_values: no schema for', path
                return None

        for (name, value) in data.items():
            if name in schema:
                name_schema = schema[name]
                type_node = name_schema['nodeType']
                if type_node == 'LEAF':
                    type_schema = name_schema.get('typeSchemaNode')
                    leaf_type = type_schema.get('leafType')
                    if leaf_type == 'INTEGER':
                        data[name] = int(data[name])
                        # default value check?
                    elif leaf_type == 'BOOLEAN':
                        default_string = type_schema.get('defaultValueString')
                        if default_string:
                            default_value = convert_schema_boolean(default_string)
                            if default_value == True:
                                # when the default boolean value is true,
                                # then deleting the item will set it back to true.
                                # instead PATCH and set the value to false.
                                data[name] = False
                                oper = 'PATCH'
                elif type_node == 'LEAF_LIST':
                    # If this is a LEAF_LIST, other values may be present.
                    (schema, result) = self.schema_and_result(path, select) # self.data_rest_request?
                    final = result.expect_single_result(failed_result = [])
                    if not name in final:
                        print 'canonicalize_values_for_delete: throw exception', name, final
                        pass

                    data[name] = [x for x in final[name] if x != data[name]]
                    oper = 'POST'
            else:
                if debug.description():
                    print 'canonicalize_values_for_delete: no schema for ', path, name
                # these are intended to be complete path names for xpath,
                # move them from data to select for xpath to use them
                select[name] = value
                del data[name]
        return oper


    def add_mode_stack_paths(self, update, max_depth = None):
        """
        Add the name:value pairs from the mode stack, where the name
        is the path of the associated schema.  This is compatible with
        the xpath generator.
        """
        mode_stack_dict = {}
        self.bigsh.run.finder.mode_stack.mode_stack_to_rest_dict(mode_stack_dict, max_depth = max_depth)
        for (mode_name, mode_value) in mode_stack_dict.items():
            # mode_name is a path for bigdb objects.
            keys = self.search_keys.get(mode_name, [])
            if keys:  # this path is indexed by something
                if len(keys) > 1: # alright if the 'obj' is a dict?
                    if type(mode_value) == dict:
                        # validate all the keys are present.
                        for key in keys:
                            if not key in mode_value:
                                print ('add_mode_stack_paths: missing %s for keys %s from %s' %
                                       key, keys, mode_value)
                    else:
                        print 'add_mode_stack_paths: len()', mode_name, len(keys)
                elif type(mode_value) == dict:
                    if is_path(mode_name): # don't want simple string prefixes
                        mode_value = dict(mode_value)
                        remove_item = True
                        for (n,v) in mode_value.items():
                            use_prefix = n
                            if is_path(n):
                                (prefix, _, name) = n.rpartition('/')
                                prefix_keys = self.search_keys.get(prefix)
                                if prefix_keys and name in prefix_keys:
                                    use_prefix = prefix
                            else: # don't want simple string prefixes
                                remove_item = False
                            
                            if n.startswith(mode_name):
                                update[n] = v
                                del mode_value[n]
                            elif mode_name.startswith(use_prefix):
                                update[n] = v
                                del mode_value[n]
                            else:
                                remove_item = False
                        if remove_item:
                            continue # skip update.
                else: # single named key, simple mode_value
                    # the selection is populated originally from 'data',  if the
                    # key value is already posted in the selection, don't add
                    # another key value from the submode stack.   This allows
                    # 'delete' commands to work within a submode for other named items.
                    if mode_name in update or '%s/%s' % (mode_name, keys[0]) in update:
                        continue
                update[mode_name] = mode_value
        if debug.description():
            print 'add_mode_stack_paths:', update


    def add_mode_stack_keys(self, update):
        """
        Add the name:value pairs, where the name is from the key fields
        of the path's.  This is compatible with data items for a post.
        """
        mode_stack_dict = {}
        self.bigsh.mode_stack_to_rest_dict(mode_stack_dict)
        for entry in self.bigsh.mode_stack:
            path = entry.get('path', '')
            if path != '':
                value = entry.get('obj')
                keys = self.search_keys.get(path, [])
                if len(keys) == 1:
                    name = entry.get('name', keys[0])
                    if name and not name in update:
                        update[name] = value
                else:
                    for key in keys:
                        if not key in update:
                            update[key] = value
        if debug.description():
            print 'add_mode_stack_keys:', update


    def key_values(self, result, item, field):
        """
        Return a list of the keyNodeNames's values
        """
        keys = self.search_keys.get(item)
        if field in keys:
            return [x for x in result.keys()]
        return []


    def read_pickled_config(self, file_name):
        try:
            fd = open(file_name)
            contents = fd.read()
        except Exception, e:
            print 'read_pickled_config: can\'t open file "%s":%s' % (file_name, e)
            return None
        else:
            fd.close()
            return json.loads(contents)


    def write_pickled_config(self, file_name, config):
        contents = json.dumps(config, indent=2, separators=(',', ': '))
        try:
            fd = open(file_name, 'w')
        except Exception, e:
            print 'write_pickled_config: can\'t open file "%s":%s' % (file_name, e)
        else:
            try:
                mode = stat.S_IREAD | stat.S_IWRITE | stat.S_IRGRP | stat.S_IWGRP | stat.S_IROTH
                os.fchmod(fd.fileno(), mode)
            except Exception, e:
                print 'write_pickled_config: can\'t set file "%s" permissions: %s' % (file_name, e)

            fd.write(contents)
            if debug.description():
                print 'Config file written:', file_name
            fd.close()


    def crack_field(self, path, field, field_desc, configurable):
        this_path = path_adder(path, field)
        self.log('PATH %s %s %s ' % (this_path, field_desc.keys(), configurable))
        (name, node_type, base_type_name, base_typedef, module) = \
            (None, None, None, None, None)
        (attributes, child_nodes, data_sources, description) = \
            (None, None, None, None)
        (key_node_nanmes, validator, default_value_string, defaul_value) = (None, None, None, None)
        (leaf_type, leaf_schema_node, type_schema_node) = (None, None, None)
        (list_schema_node, mandatory) = (None, None)
        # three fields seem to identify type:
        # 'nodeType', 'baseTypeName', 'baseTypedef'

        for (attr, attr_value) in field_desc.items():   
            if attr == 'name':
                if attr_value != field:
                    print 'Warning: schema %s "name" %s ' \
                            'doesn\'t match field name %s' % \
                            (path, attr_value, field)
                name = attr_value
            elif attr == 'nodeType':
                node_type = attr_value
                if node_type not in self.known_types:
                    print 'Warning: schema: %s:%s unknown type %s' % \
                            (this_path, field, node_type)
                else:
                    self.log('SCAN %s %s %s' % (this_path, field, node_type))
            elif attr == 'dataSources':
                data_sources = attr_value
                for source in data_sources:
                    if source not in self.known_data_sources:
                        print 'Warning: schema: %s:%s unknown data source %s' % \
                                (path, field, source)
                self.path_config_sources[this_path] = data_sources
                if 'config' in data_sources and len(data_sources) == 1:
                    self.path_config_only[this_path] = True

            elif attr == 'mandatory':
                # mandatory is only announced for specific fields
                self.path_mandatory[this_path] = attr_value
            elif attr == 'childNodes':
                child_nodes = attr_value
            elif attr == 'leafType':
                leaf_type = attr_value
            elif attr == 'typeSchemaNode':
                type_schema_node = attr_value
                type_schema_name = type_schema_node.get('name')
                if type_schema_name:
                    self.path_type[this_path] = type_schema_name
                validator = type_schema_node.get('typeValidator')
                if validator:
                    self.path_validator[this_path] = validator
                elif type_schema_node.get('leafType') == 'UNION':
                    def collect_all_nested_validators(type_schema_node):
                        collection = []
                        for type_node in type_schema_node.get('typeSchemaNodes'):
                            if 'typeValidator' in type_node:
                                collection.append(type_node['typeValidator'])
                            if 'typeSchemaNodes' in type_node:
                                collection += collect_all_nested_validators(type_node)
                        return collection

                    # in this case, a list of lists is constructed,
                    # which allows the field validator to recognize
                    # these are alternations, one of which must match.
                    
                    # add a name to each of the range items, which provides
                    # for a better error message.
                    collection = collect_all_nested_validators(type_schema_node)
                        
                    if collection:
                        self.path_validator[this_path] = collection
            elif attr == 'keyNodeNames':
                key_node_names = attr_value
            elif attr == 'listElementSchemaNode':
                list_schema_node = attr_value
            elif attr == 'leafSchemaNode':
                leaf_schema_node = attr_value
            elif attr == 'typeValidator':
                validator = attr_value
                self.log("%s %s VALIDATOR %s" % (path, field, validator))
            elif attr == 'defaultValue':
                default_value = attr_value
                if this_path in self.path_default:
                    if self.path_default[this_path] != default_value:
                        print 'Multiple default values for ', this_path, \
                                self.path_default[this_path], this_value
                self.path_default[this_path] = default_value
            elif attr == 'defaultValueString':
                default_value_string = attr_value
            elif attr == 'baseTypeName':
                base_type_name = attr_value
            elif attr == 'baseTypedef':
                base_typedef = attr_value
            elif attr == 'attributes':
                attributes = attr_value
                self.log("%s %s ATTRIBUTES %s" % (path, field, attributes))
                if attributes:
                    # config attribute.
                    attr_config = attributes.get('Config')
                    if attr_config:
                        configurable = convert_schema_boolean(attr_config)
                    # column-header attribute.
                    column_header = attributes.get('column-header')
                    if column_header:
                        self.column_header[this_path] = column_header
                    # case-sensitive attribute
                    case_sensitive = attributes.get('case-sensitive')
                    if case_sensitive:
                        this_value = convert_schema_boolean(case_sensitive)
                        self.case_sensitive[this_path] = this_value
                    # allow-empty-string attribute
                    allow_empty_string = attributes.get('allow-empty-string')
                    if allow_empty_string:
                        this_value = convert_schema_boolean(allow_empty_string)
                        self.allow_empty_string[this_path] = this_value
                    # alias attribute
                    alias = attributes.get('alias')
                    if alias:
                        this_value = alias
                        if this_value != 'True':
                            print 'ALIAS', this_path, 'value not True', alias
                        # At this point, the search_keys associated with the path
                        # may not be known, record the existance of the alias,
                        # and later assocaite the key
                        if configurable == True:
                            self.alias_field[path] = name
                        else:
                            if debug.description():
                                print 'ALIAS NOT REPLACEABLE', path, name
                    # cascade
                    cascade = attributes.get('cascade')
                    if cascade:
                        this_value = convert_schema_boolean(cascade)
                        self.path_cascade[this_path] = this_value
            elif attr == 'description':
                description = attr_value
            elif attr == 'module':
                module = attr_value
            else:
                print 'Warning: schema: %s+%s unknown attribute %s' % \
                        (this_path, field, attr)
                self.log("   -- %s %s" % (attr, attr_value))

        # save paths of the configurable items
        self.path_configurable[this_path] = configurable

        if node_type == 'LEAF_LIST':
            self.log('LEAF_LIST %s %s' % (this_path, leaf_schema_node))
            field = field_desc['name']
            self.crack_field(this_path, field, leaf_schema_node, configurable)
        elif node_type == 'LIST':
            keys = list_schema_node.get('keyNodeNames')
            self.log('LIST %s' % keys)
            if keys:
                self.search_keys[this_path] = keys
            self.crack_aggregate(this_path, list_schema_node['childNodes'], configurable)
        elif node_type == 'CONTAINER':
            self.crack_aggregate(this_path, child_nodes, configurable)
        elif node_type == 'LEAF':
            keys = self.search_keys.get(path)
            if keys and name in keys:
                if not path in self.search_keys_type:
                    self.search_keys_type[path] = {}
                self.search_keys_type[path][name] = type_schema_node['name']
        else:
            self.log('OOPS TYPE %s' % type)


    def crack_aggregate(self, path_prefix, container, configurable):
        if container == None:
            print 'EMPTY', path_prefix
            return

        for (path, path_details) in container.items():
            big_path = '%s/%s' % (path_prefix, path)
            self.log('PATH %s (%s) %s' % (big_path, path_prefix, path_details.keys()))

            type        = path_details['nodeType']
            name        = path_details['name']
            module      = path_details['module']

            self.crack_field(path_prefix, path, path_details, configurable)

            if type == 'LIST':
                child_nodes = path_details['listElementSchemaNode']
                keys = child_nodes.get('keyNodeNames')
                if keys:
                    self.search_keys[big_path] = keys
                    keys_id = ' '.join(keys)
                    if not keys_id in self.search_keys_xref:
                        self.search_keys_xref[keys_id] = []
                    self.search_keys_xref[keys_id].append(path)
                    
                child_nodes = child_nodes['childNodes']
                self.log('-- %s %s %s %s %s %s' %
                         (big_path, name, type, module, child_nodes.keys(), keys))

                #for (field, field_value) in child_nodes.items():
                    #self.crack_field(big_path, field, field_value, configurable)
                    #print field, field_value.items()
                # alias type association BSC-2992
                if big_path in self.alias_field:
                    # self.alias_field gets set via child node recursive descent
                    self.path_alias[big_path] = keys
                    # assume type mapping
                    if not keys:
                        print 'OOPS: alias without keys', big_path
                    elif len(keys) > 1:
                        print 'OOPS: alias for multiple keys', big_path, keys
                    else:
                        key_name = keys[0]
                        alias_type = self.search_keys_type[big_path][key_name]
                        # traceback.print_stack()
                        keys_id = ' '.join(keys)
                        if not keys_id in self.alias_xref:
                            self.alias_xref[keys_id] = []
                        self.alias_xref[keys_id].append(big_path)
                        if alias_type in self.alias_type_xref:
                            if self.alias_type_xref[alias_type] != big_path:
                                print 'OOPS: alias_type_xref with', \
                                        alias_type, big_path, \
                                        self.alias_type_xref[alias_type]
                        self.alias_type_xref[alias_type] = big_path
            elif type == 'CONTAINER':
                child_nodes = path_details.get('childNodes', {})

                #for (field, field_value) in child_nodes.items():
                    #self.crack_field(big_path, field, field_value, configurable)
                    #print field, field_value.items()
            elif type == 'LEAF':
                pass
            else:
                self.log('AGGREGATE TYPE %s %s %s' % (type, big_path, path_details))


    def crack_schema(self, item):
        node_type = item.get('nodeType')
        if node_type == None:
            self.log('CRACK_SCHEMA: no schema')
            return

        if node_type == 'CONTAINER':
            # REST API envelope.
            container = item.get('childNodes')
            self.log('CRACK_SCHEMA %s' % container.keys())
            self.log('ENVELOPE %s' % container.keys())
            for (envelope_name, envelope_value) in container.items():
                envelope_type = envelope_value.get('nodeType')
                if envelope_type != 'CONTAINER':
                    self.log('UNKNOWN ENVELOPE %s' % envelope_type)
                self.crack_field('', envelope_name, envelope_value, True)
        else:
            self.log('CRACK_SCHEMA, type ' % type)

        self.log('SEARCH KEYS')
        left_length = max([len(x) for x in self.search_keys.keys()])
        for (search_path, search_keys) in sorted(self.search_keys.items()):
            self.log('%-*s %s' % (left_length, search_path, search_keys))
        self.log('SEARCH KEYS TYPE')
        left_length = max([len(x) for x in self.search_keys_type.keys()])
        for (search_path, search_keys) in sorted(self.search_keys_type.items()):
            self.log('%-*s %s' % (left_length, search_path, search_keys))
        self.log('CONFIGURABLE')
        left_length = max([len(x) for x in self.path_configurable.keys()])
        for (config_path, config_value) in sorted(self.path_configurable.items()):
            self.log('%-*s %s' % (left_length, config_path, config_value))
        self.log('MANDATORY')
        left_length = max([len(x) for x in self.path_mandatory.keys()])
        for (mandatory_path, mandatory_value) in sorted(self.path_mandatory.items()):
            self.log('%-*s %s' % (left_length, mandatory_path, mandatory_value))
        self.log('VALIDATOR')
        left_length = max([len(x) for x in self.path_validator.keys()])
        for (validator_path, validator_value) in sorted(self.path_validator.items()):
            self.log('%-*s %s' % (left_length, validator_path, validator_value))
        self.log('ALIAS')
        left_length = max([len(x) for x in self.path_alias.keys()])
        for (alias_path, alias_value) in sorted(self.path_alias.items()):
            self.log('%-*s %s' % (left_length, alias_path, alias_value))
        self.log('ALIAS-XREF')
        left_length = max([len(x) for x in self.alias_xref.keys()])
        for (alias_path, alias_value) in sorted(self.alias_xref.items()):
            self.log('%-*s %s' % (left_length, alias_path, alias_value))
        self.log('ALIAS-TYPE-XREF')
        left_length = max([len(x) for x in self.alias_type_xref.keys()])
        for (alias_path, alias_value) in sorted(self.alias_type_xref.items()):
            self.log('%-*s %s' % (left_length, alias_path, alias_value))

        self.path_config_only['bigtap/policy'] = True       # FIX
        self.path_config_only['bigtap/service'] = True      # FIX
        for path in self.path_config_only.keys():
            if not path in self.path_configurable:
                if debug.description():
                    print 'CONFIG ONLY NOT CONFIGURABLE', path
        if debug.description():
            print 'DONE TESTING CONFIG-ONLY'



    def validate_schema(self, schema):
        """
        Perform specific validations on the schema, and modify its value
        to prevent problems.
        """

        def purge_key_defaults(path, schema, error_count):
            """
            Walk the schema looking for any items with keys.
            Then review the key fields in the children looking for
            default values.  If they exist, complain, then purge the default value.
            """
            # note: error_count could be a dictionary, with keys describing
            # the type of error.   This would be helpful when various
            # distinct schema validations would be performed.
            if schema == None:
                return error_count
            node_type = schema.get('nodeType')
            if node_type == 'CONTAINER':
                if schema.get('childNodes') == None:
                    print 'validate_schema: failure: no children for container object'
                    return

                for (child, child_schema) in schema.get('childNodes').items():
                    error_count = purge_key_defaults(path_adder(path, child),
                                                     child_schema,
                                                     error_count)
            elif node_type == 'LIST':
                list_nodes = schema['listElementSchemaNode']
                keys = list_nodes.get('keyNodeNames')
                child_nodes = list_nodes.get('childNodes')
                if keys:
                    for key in keys:
                        if key in child_nodes:
                            default_value = child_nodes[key].get('defaultValue')
                            if default_value:
                                print 'FILE PR: schema %s: key: "%s" deleting default value: %s' % (
                                    path, key, default_value)
                                del child_nodes[key]['defaultValue']
                                error_count += 1
                        else:
                            print 'validate_schema: missing key in schema:', key
                for (child, child_schema) in child_nodes.items():
                    # only recurse on these types.
                    if child_schema['nodeType'] in ['LIST', 'CONTAINER']:
                        error_count = purge_key_defaults(path_adder(path, child),
                                                         child_schema,
                                                         error_count)
            return error_count

        return purge_key_defaults('', schema, 0)


    def bigdb_format_value(self, result, schema, detail = None):
        """
        Convenience interface to allow use of the type
        converters in non show_print situations.
        """
        type_schema = schema.get('typeSchemaNode')
        if type_schema == None:
            return result
        type_name = type_schema.get('name')
        if type_schema == None:
            return result
        if type_name not in bigdb_type_formatter:
            return result
        return bigdb_type_formatter[type_name](result, schema, detail, self)


    def post_leaf_node_to_row(self, path, schema, results, row_dict,
                                    name = None,
                                    type_formatter = None,
                                    detail = None):
        leaf_type = schema.get('leafType')
        if name == None:
            name = schema.get('name')
        if type_formatter == None:
            type_formatter = bigdb_type_formatter

        if leaf_type == 'ENUMERATION':
            type_node = schema.get('typeSchemaNode')
            enum_result = results
            if type_node:
                if type_node.get('leafType'):
                    enum_values = type_node.get('enumerationSpecifications')
                    if enum_values:
                        for name in enum_values:
                            if name['value'] == enum_result:
                                enum_result = name
            row_dict[name] = str(enum_result)
        elif leaf_type == 'UNION':
            type_node = schema.get('typeSchemaNode')
            type_name = type_node.get('name') if type_node else None
            if type_name and type_name in type_formatter:
                row_dict[name] = type_formatter[type_name](results, schema, detail, self)
            else:
                row_dict[name] = str(results)
        elif atomic_type(results) or \
             (type(results) == list and len(results) == 1 and atomic_type(results[0])):
            if type(results) == list:
                results = results[0]
            self.log('%s LEAF %s <- %s' % (path, name, results))
            type_node = schema.get('typeSchemaNode')
            type_name = type_node.get('name') if type_node else None
            if type_name and type_name in type_formatter:
                value = type_formatter[type_name](results, schema, detail, self)
            else:
                value = str(results)
            # if name in row_dict and row_dict[name] != value:
            if name in row_dict:
                path_items = path.split('/')
                # Likely need mutliple path elements to make this unique
                name = '%s-%s' % (path_items[-1], name)
            row_dict[name] = value
        else:
            print 'post_leaf_node_to_row: %s LEAF MORE DETAILS %s %s %s' % (path, schema, type(results), results)


    def map_schema_to_results(self, path,
                                    schema,
                                    results,
                                    row_dict = None,
                                    indices = None,
                                    collect_column_headers = None,
                                    list_labels = None,
                                    caller_collects = None,
                                    type_formatter = None,
                                    detail = None,
                                    ):
        """
        Generator (iterator) for items in the results, associated with the
        schema passed in.

        'index' is a list of dictionary of items which are intended to be columns in the
        table which must appear for every interior table.

        caller_collects is an indication of whether the caller intends to
        use CONTAINER items.  if so, they get included into the callers
        row_dict, otherwise they get called out to be consumed as
        CONTAINER-BEGIN/CONTAINER-END
        """

        if list_labels == None:
            list_labels = {}

        node_type = schema.get('nodeType')
        name = schema.get('name')
        self.log('%s %s TYPE %s' % (path, name, node_type))

        if row_dict == None:
            row_dict = dict()
        if indices == None:
            indices = list()

        if node_type in ['LEAF']:
            self.post_leaf_node_to_row(path, schema, results, row_dict,
                                       type_formatter = type_formatter,
                                       detail = detail)
        elif node_type == 'LIST':
            row = {} if row_dict == None else dict(row_dict)

            daughter = schema.get('listElementSchemaNode')
            index = daughter.get('keyNodeNames')
            # verify index in list_fields
            list_items = daughter.get('childNodes')
            self.log('LIST %s %s %s %s' % (path, name, index, list_items.keys()))
            yield ('LIST-BEGIN', name, path, indices, row)
            # spath = '%s/%s/%s' % (path, name, index_value)
            # add_fields(depth+1, list_fields)
            # index ('keyNodeNames') means to expect a dictionary.

            if collect_column_headers == None:
                collect_column_headers = {}

            if results == None:
                yield ('LIST-END', name, path, indices, row, {})
            elif index:
                row_names = {}
                # results for index's items are still lists
                # for (index_value, result) in results.items():
                for result in results:
                    index_value = ' '.join([str(result.get(x, '<>')) for x in index])
                    self.log('[] %s:%s' % (index, index_value))
                    # new_row = dict(row)
                    new_row = dict()
                    prevent_reapply = None # should this be indicies
                    if '|'.join(index) in result:
                        # since result's names don't have '|', this must be a single
                        # value, the value may need alias conversion.
                        index_name = index[0]
                        if len(index) != 1:
                            # simple key, replace result
                            print 'map_schema_to_results: bad length %s' % index
                        self.post_leaf_node_to_row(path_adder(path, index[0]),
                                                   list_items[index[0]],
                                                   index_value,
                                                   new_row,
                                                   type_formatter = type_formatter,
                                                   detail = detail)
                        index_value = new_row[index_name]
                        # check to see if the value was replaced via type_formatter
                        if index_value != result[index_name] and self.path_alias.get(path) == index:
                            # restore the original value, retain index_value for interior tables
                            new_row[index_name] = result[index_name]
                        # Name is used quite alot, if name has already
                        # appeared in the index, try to build a different name.
                        for index_item in indices:
                            if index_name in index_item:
                                index_name = name + '-' + index_name
                        # since the key overalpps with result values, don't reapply the
                        # result value since the second application will result in additional
                        # fields becomming added.
                        prevent_reapply = index[0]
                        new_indices = list(indices) + [{index_name : index_value}]
                        if list_items[index[0]].get('attributes'):
                            attributes = list_items[index[0]].get('attributes')
                            column_header = attributes.get('column-header')
                            if column_header:
                                collect_column_headers[index_name] = column_header
                        if not index_name in collect_column_headers:
                            collect_column_headers[index_name] = name.capitalize()
                        spath = '%s/%s[%s=%s]' % (path, name, index_name, index_value)
                    else:
                        new_row['|'.join(index)] = index_value
                        new_indices = list(indices) + [{name : index_value}]
                        spath = '%s/%s' % (path_adder(path, name), index_value) # XXX '%s/%s'

                    for (item_name, item_value) in list_items.items():
                        if item_name in result:
                            if item_name == prevent_reapply:
                                continue
                            for item in self.map_schema_to_results(spath,
                                                                   item_value,
                                                                   result[item_name],
                                                                   new_row,
                                                                   new_indices,
                                                                   collect_column_headers,
                                                                   list_labels,
                                                                   caller_collects = True,
                                                                   type_formatter = type_formatter,
                                                                   detail = detail):
                                yield item

                    # check for index names overlappign with data names
                    #if prevent_reapply and indices:
                        #for (n, v) in indices[-1].items():
                            #if n in new_row:
                                ## rename the index
                                #indices[-1]['view-' + n] = v
                                #del indices[-1][n]
                                #print 'XX', path, n, spath, new_indices

                    yield ('LIST-ITEM', name, path, indices, row, new_row)

                for (column, details)  in list_items.items():
                    attributes = details.get('attributes')
                    if attributes == None:
                        continue
                    column_header = attributes.get('column-header')
                    if column_header:
                        collect_column_headers[column] = column_header

                yield ('LIST-END', name, path,
                                   indices + [{'|'.join(index) : None}], row, 
                                   collect_column_headers)
            else: # expect a true list

                count = list_labels.get(name, 0)
                for (count, result) in enumerate(results, count):
                    self.log('LIST [] %s' % result)
                    new_indices = list(indices) + [{'+' + name : str(count)}]
                    # spath = path_adder(path, name)
                    # new_row = dict(row)
                    new_row = dict()
                    new_row['+' + name] = str(count)
                    for (item_name, item_value) in list_items.items():
                        if item_name in result:
                            for item in self.map_schema_to_results(path,
                                                               item_value,
                                                               result[item_name],
                                                               new_row,
                                                               new_indices,
                                                               collect_column_headers,
                                                               list_labels,
                                                               caller_collects = True,
                                                               type_formatter = type_formatter,
                                                               detail = detail):
                                yield item
                    yield ('LIST-ITEM', name, path, indices, row, new_row)

                list_labels[name] = count + 1

                for (column, details) in list_items.items():
                    attributes = details.get('attributes')
                    if attributes == None:
                        continue
                    column_header = attributes.get('column-header')
                    if column_header:
                        if type(column_header) == dict:
                            collect_column_headers[column] = column_header.get('value')
                        else: # assume its a string
                            collect_column_headers[column] = column_header

                yield ('LIST-END', name, path,
                                   indices + [{'+' + name : None}], row, 
                                   collect_column_headers)
            return
        elif node_type == 'LEAF_LIST':
            #row = {} if row_dict == None else dict(row_dict)
            row = {}
            # verify index in list_fields
            daughter = schema.get('leafSchemaNode')
            last_index = indices[-1]
            if len(last_index.keys()) == 1:
                parent_name = last_index[last_index.keys()[0]]
            self.log('LEAF-LIST %s %s %s %s %s' %
                     (path, parent_name, indices, daughter.keys(), last_index))
            yield ('LIST-BEGIN', parent_name, path, indices, row)
            # spath = '%s/%s/%s' % (path, name, index_value)
            # add_fields(depth+1, list_fields)
            new_row = dict(row)
            item_schema = daughter.get('typeSchemaNode')
            leaf_node_type = item_schema.get('nodeType')
            if leaf_node_type != 'TYPE':
                self.log('LEAF-LIST without interior TYPE node: %s' % leaf_node_type)
            else:
                leaf_type = item_schema.get('leafType')
                for item in results:
                    new_indices = list(indices) + [{name : item}]
                    self.post_leaf_node_to_row(path, item_schema, item, row, name, type_formatter)
                    yield ('LIST-ITEM', parent_name, path, new_indices, row, new_row)

            new_indices = list(indices) + [{name : None}]
            yield ('LIST-END', parent_name, path, new_indices, row, {})
            return
            
        elif node_type == 'CONTAINER':
            # should abstract name types be added?
            child_nodes = schema.get('childNodes')
            self.log('%s CONTAINER %s %s' % (path, name, child_nodes.keys()))
            base_dict = dict(row_dict)
            if type(results) == list:
                if len(results) > 1:
                    print 'map_schema_to_results: container with more than one element'
                if len(results) == 0:
                    return
                results = results[0]

            # cascade enabled?
            cascade = schema.get('attributes')
            if cascade:
                cascade = cascade.get('cascade')
                if cascade:
                    # the value ought to never be true, since it starts as true
                    # and gets inherited until its value changes.  but once it
                    # goes false, it can't go back to true.
                    if cascade == 'false': 
                        cascade = False
                        
            # use cascade as a predicate to determine whether the
            # value of the index fields be updated to include the alias value
            if cascade == False:
                # for nested values, index type may have alias replacements.
                for index_item in indices:
                    for (index_name, index_value) in index_item.items():
                        if index_name in row_dict:
                            row_dict[index_name] = index_value

            # peek at the node type of all the children to see whetehr
            # there's anything other than leaf/leaf-lists.  IF there's
            # no deeper entries, then the data here is the property of
            # this container

            deeper_nodes = True
            if not caller_collects: # 
                for (child_name, child_value) in child_nodes.items():
                    node_type = child_value.get('nodeType')
                    # check for any complex types
                    if (node_type != 'LEAF' and 
                        node_type != 'LEAF_LIST' and
                        node_type != 'CONTAINER'):
                            break
                else:
                    deeper_nodes = False

            if not deeper_nodes:
                collect_column_headers = {}
                yield ('CONTAINER-BEGIN', name, path, indices, row_dict)

            for (child_name, child_value) in child_nodes.items():
                self.log('%s CONTAINER PART %s %s' %
                          (path, child_name, child_name in results))
                if child_name in results:
                    for item in self.map_schema_to_results(path_adder(path, child_name),
                                                           child_value,
                                                           results[child_name],
                                                           row_dict,
                                                           indices,
                                                           collect_column_headers,
                                                           list_labels = list_labels,
                                                           caller_collects = caller_collects,
                                                           type_formatter = type_formatter,
                                                           detail = detail):
                        yield item

            if collect_column_headers != None:
                for (column, details) in child_nodes.items():
                    attributes = details.get('attributes')
                    if attributes == None:
                        continue
                    column_header = attributes.get('column-header')
                    if column_header:
                        if type(column_header) == dict:
                            collect_column_headers[column] = column_header.get('value')
                        else:
                            collect_column_headers[column] = column_header
 
            self.log('%s CONTAINER DONE %s %s' % (path, name, row_dict))
            if not deeper_nodes:
                yield ('CONTAINER-END', name, path, indices, base_dict, row_dict, collect_column_headers)
        else:
            self.log('TYPE %s NEEDS HELP %s %s' % (node_type, schema, results))


    def schema_of_path(self, path, filter, refresh_schema = True):
        """
        Return a tuple of  (child tree, item_index) based on a requested path.
        Filter must be included, since the schema is traversed
        as the path is walked.
        """
        def all_keys_included(keys, filter):
            for key in keys:
                if not key in filter:
                    return False
            return True
        
        curr = self.schema
        schema_path = ''
        item_index = None
        list_children = None

        if string_type(path):
            path = path.split('/')

        for element in path:
            if item_index:  # last item was LIST, move forward
                if element in list_children:
                    curr = list_children.get(element)
                    if curr == None:
                        break
                    item_index = None
                    list_children = None
                    continue

            item_index = None
            schema_path = path_adder(schema_path, element)
            node_type = curr.get('nodeType')

            next = None
            if node_type == 'CONTAINER':
                child_nodes = curr.get('childNodes')
                next = child_nodes.get(element)
            elif node_type == 'LIST':
                list_nodes = curr.get('listElementSchemaNode')
                child_nodes = list_nodes.get('childNodes')
                next = child_nodes.get(element)
            
            if filter:
                keys = self.search_keys.get(schema_path)
                if keys:
                    if all_keys_included(keys, filter):
                        # consume the next element, it must be LIST
                        self.log('KEY MOVE %s' % next.get('nodeType'))
                        if next.get('nodeType') == 'LIST':
                            list_schema = next.get('listElementSchemaNode')
                            list_children = list_schema.get('childNodes')
                            if list_children == None:
                                self.log('BIGDB TROUBLE')
                            item_index = keys

            if next == None:
                if refresh_schema:
                    # Transitions from slave to master, or feature enablement
                    # may cause the current schema to be out-of-date.   Before
                    # deciding on a mismatch here, attempt to re-read the schema,
                    # and give it another shot.
                    if debug.description():
                        print 'REFRESH_SCHEMA', path, filter
                    self.schema_request()
                    return self.schema_of_path(path, filter, refresh_schema = False)
                return (None, None)
            curr = next

        # last element may have a filter item.
        if curr.get('nodeType') == 'LIST':
            list_nodes = curr.get('listElementSchemaNode')
            keys = list_nodes.get('keyNodeNames')
            if keys and all_keys_included(keys, filter):
                item_index = keys

        return (curr, item_index)


    def schema_detailer_validators(self, type_schema_node):
        """
        Result is a dictionary of validator_name:...

        To display these, use somethng like:
        ' '.join(['%s:%s' % (n,v) for (n,v) in v_dict]
        """
        v_dict = {}

        for validator in type_schema_node.get('typeValidator', []):
            kind = validator.get('type')
            if kind == 'RANGE_VALIDATOR':
                kind = 'range'
            elif kind == 'LENGTH_VALIDATOR':
                kind = 'length'
            elif kind == 'ENUMERATION_VALIDATOR':
                kind = 'enum'
            elif kind == 'PATTERN_VALIDATOR':
                kind = 'pattern'
            else:
                self.log('Validator Kind unknown: %s' % kind)
                continue

            if not kind in v_dict:
                v_dict[kind] = []

            if kind == 'range' or kind == 'length':
                for range in validator.get('ranges', []):
                    v_dict[kind].append(self.range(range))
            elif kind == 'pattern':
                v_dict[kind].append(validator.get('pattern'))
            elif kind == 'enum':
                name_dict = validator.get('names')
                v_dict[kind].append(','.join(['[%s:%s]' % (n,v) for (n,v) in name_dict.items()]))
        return v_dict
  

    def schema_detailer(self, schema, config, depth = None):
        if depth == None:
            depth = 0
        indent = '  ' * depth

        def attr_value(item):
            if type(item) == dict:
                return item['value']
            else: # assume its a string.
                return item

        name = schema.get('name')
        node_type = schema.get('nodeType')
        attributes = schema.get('attributes')
        if attributes and 'Config' in attributes:
            if attr_value(attributes['Config']) == 'false': # yep, in the schema 'false' not False
                config = False
        configurable = ' (mutable)' if config else ''

        case_sensitive = True
        if attributes and 'case-sensitive' in attributes:
            if attr_value(attributes['case-sensitive']) == 'false': # yep, in the schema 'false' not False
                case_sensitive = False
        case = '' if case_sensitive else ' (~case)'

        allow_empty_string = False
        if attributes and 'allow-empty-string' in attributes:
            if attr_value(attributes['allow-empty-string']) == 'true': # yep, in the schema 'true' not True
                allow_empty_string = True
        allow_empty = '(empty ok)' if allow_empty_string else ''

        if node_type == 'LEAF':
            if debug.description():
                self.log('%s LEAF %s' % (indent, schema))
            leaf_type = schema.get('leafType')
            mandatory = schema.get('mandatory')
            type_schema_node = schema.get('typeSchemaNode', {})
            leaf_type_detail = leaf_type
            if 'name' in type_schema_node:
                leaf_type_detail = '%s/%s' % (type_schema_node['name'], leaf_type)
            default_value = 'default:%s'  % schema.get('defaultValue') if 'defaultValue' in schema else ''
            v_dict = self.schema_detailer_validators(type_schema_node)
            yield ('%s%s%s%s%s LEAF type: %s %smandatory %s %s\n' %
                   (indent, name, configurable, case, allow_empty, leaf_type_detail, '' if mandatory == True else 'not-',
                    ' '.join(["%s:%s" % (n,','.join(v)) for (n,v) in v_dict.items()]), default_value))

            if leaf_type == 'UNION':
                nodes = type_schema_node.get('typeSchemaNodes')
                for node in nodes:
                    v_dict = self.schema_detailer_validators(node)
                    yield ('  %s%s%s%s%s TYPE %s\n' % (indent, node.get('name'), configurable, case, allow_empty,
                           ' '.join(["%s:%s" % (n,','.join(v)) for (n,v) in v_dict.items()])))

        elif node_type == 'LEAF_LIST':
            leaf_node = schema.get('leafSchemaNode')
            mandatory = schema.get('mandatory')
            base_type = leaf_node.get('leafType')
            type_schema_node = leaf_node.get('typeSchemaNode', {})
            v_dict = self.schema_detailer_validators(type_schema_node)

            yield ('%s%s:%s%s%s LEAF-LIST mandatory %s LIST of %s %s\n' %
                    (indent, name, configurable, case, allow_empty, mandatory, base_type,
                     ' '.join(["%s:%s" % (n,','.join(v)) for (n,v) in v_dict.items()])))
            if base_type == 'UNION':
                nodes = type_schema_node.get('typeSchemaNodes')
                for node in nodes:
                    v_dict = self.schema_detailer_validators(node)
                    yield ('  %s%s%s%s%s TYPE %s\n' % (indent, node.get('name'), configurable, case, allow_empty,
                           ' '.join(["%s:%s" % (n,','.join(v)) for (n,v) in v_dict.items()])))
        elif node_type == 'LIST':
            node = schema.get('listElementSchemaNode')
            elements_key = ''
            if node:
                key = node.get('keyNodeNames')
                if key:
                    elements_key = ' of %s' % ', '.join(key)

            child_nodes = node.get('childNodes', [])
            yield '%s%s:%s%s%s LIST%s ITEMS <%s>\n' % (indent, name, configurable, case, allow_empty,
                                                   elements_key, ', '.join(child_nodes))
            # do the key node fields first
            if key:
                for child in key:
                    if not child in child_nodes:
                        yield '%s  Missing Key Description: %s' % (indent, child)
                        continue
                    value = child_nodes[child]
                    for item in self.schema_detailer(value, config, depth + 1):
                        yield item

            for (child, value) in child_nodes.items():
                if key and child in key:
                    continue # already done (first)
                for item in self.schema_detailer(value, config, depth + 1):
                    yield item
        elif node_type == 'CONTAINER':
            child_nodes = schema.get('childNodes', {})
            yield '%s%s: CONTAINER ITEMS <%s>\n' % (indent, name,
                                                  ', '.join(child_nodes.keys()))
            for (child, value) in child_nodes.items():
                for item in self.schema_detailer(value, config, depth + 1):
                    yield item
        else:
            yield 'unknown type\n', node_type


    def schema_detail_path(self, path):
        """
        Return the child tree based on a requested path, traverses
        the schema without any key values
        """
        if string_type(path):
            path = path.split('/')

        curr = self.schema
        for element in path:
            item_index = None
            node_type = curr.get('nodeType')
            if node_type == 'CONTAINER':
                child_nodes = curr.get('childNodes')
                next = child_nodes.get(element)
            elif node_type == 'LIST':
                list_nodes = curr.get('listElementSchemaNode')
                next = list_nodes.get('childNodes').get(element)
            else:
                next = None

            if next == None:
                return None
            curr = next
        return curr


    def schema_detail(self, path):
        yield 'schema_detail: %s' % path
        schema = self.schema_detail_path(path)
        if schema == None:
            yield 'No schema found for path %s' % path
            return
        
        for item in self.schema_detailer(schema, self.path_configurable[path]):
            yield item
        
        return 



#
#

class BigDB_show():
    
    def __init__(self, bigdb):
        self.tables_names = []   # table names in order.
        self.tables = {}         # dictionary of tables, indexed by name
        self.titles = {}         # dictionary of titles, indexed by name
        self.columns = {}        # dictionary of columns, indexed by name
        self.column_headers = {} # dictionary of columns, indexed by name
        self.refer = {}          # dictionary, table name refers to ...

        self.alias = {}          # dictionary, index is path,
                                 #  2nd index is field, 3rd is value
        self.bigdb = bigdb       # schema access

        self.debug = False       # debug show command processing


    def log(self, s):
        if self.debug:
            print s


    def table_body_sorter(self, table, sort_columns):
        def sort_cmp(x,y):
            for f in sort_columns:
                if f[0] == '-':
                    f = f[1:]
                    c = utif.trailing_integer_cmp(y.get(f, ''), x.get(f, ''))
                else:
                    c = utif.trailing_integer_cmp(x.get(f, ''), y.get(f, ''))
                if c:
                    return c
            return 0
        return sorted(table, cmp=sort_cmp)


    def table_columns_width(self, table, columns, column_headers):
        """
        Table is a list of dictionaries.

        Columns is a list of column header names.
        """
        cols_width = {}
        for column in columns:
            cols_width[column] = len(column_headers.get(column, column))
            # a first character of '+' means a synthetic added column
            # which some other table needs to refer to rows.
            if column[0] == '+':
                cols_width[column] -= 1
        for row in table:
            for (item, value) in row.items():
                value = str(value)
                if item not in cols_width:
                    cols_width[item] = len(value)
                elif len(value) > cols_width[item]:
                    cols_width[item] = len(value)
        return cols_width


    def more_formal(self, text):
        return text.replace('-', ' ') # ? .capitalize()


    def table_header_title(self, title, len_line):
        # when a title has new-lines, they are table breaks, this hapens for forced_titles
        if title[0] == '\n':
            while title[0] == '\n':
                yield '' # table break
                title = title[1:]

        len_dash_left = (len_line - len(title) - 2)
        left_dashes = len_dash_left / 2
        slop = ''
        if len_dash_left & 1:
            slop = ' '
        remainder = len_line - left_dashes - len(title) - 2 - len(slop) 
        
        if left_dashes <= 0:
            yield title
            return
        yield  '~' * left_dashes + ' %s%s ' % (title, slop) + '~' * remainder + '\n'


    def table_header(self, cols_width,
                           title = None, columns = None, column_headers = None):
        """
        Print the table headers.
        """
        # column header
        line = ''
        for column in columns:
            if name_is_compound_key(column):
                continue
            if column in cols_width:
                col_text = column_headers.get(column, column)
                if column[0] == '+':
                    col_text = column[1:]
                line += '%-*s ' % (cols_width[column], self.more_formal(col_text))

        # table title
        if title:
            for item in self.table_header_title(title, len(line)):
                yield item

        # finally print the column header
        if line == '':
            yield '--cols empty--\n'
        else:
            yield line + '\n'

        line = ''
        for column in columns:
            if name_is_compound_key(column):
                continue
            if column in cols_width:
                line += '%s|' % ('-' * cols_width[column],)
        yield line + '\n'


    def all_columns_except(self, table,
                                 except_columns = None):
        all_columns = []
        if except_columns == None:
            except_columns = []
        # now ensure all columns are represented
        for row in table:
            for field in row.keys():
                if name_is_compound_key(field):
                    continue
                if field not in except_columns and field not in all_columns:
                    all_columns.append(field)
        return sorted(all_columns)


    def add_idx(self, table):
        """
        Add the row counter.
        """
        for (row_number, row) in enumerate(table, 1):
            row['#'] = str(row_number)


    def table_body(self, table,
                         title = None,
                         columns = None, column_headers = None,
                         exclude_columns = None, requested_columns = None,
                         sort_columns = None):
        """
        The input table is a list of dictionaries.  From the
        name:value pairs, build a simple output formatter.

        """

        if columns == None:
            columns = []
        else: # use the columns passed in as a basis for sorting
            if sort_columns == None:
                sort_columns = columns

        if sort_columns:
            table = self.table_body_sorter(table, sort_columns)

        column_headers = dict(column_headers) # dont disturb original

        if requested_columns:
            columns = [x[0] if type(x) == tuple else x
                            for x in requested_columns]
            for item in requested_columns:
                if type(item) == tuple:
                    (col, header) = item # should be only two
                    column_headers[col] = header
        else:
            # include all columns, 'columns' identifies left side columns
            columns += self.all_columns_except(table, columns)
            # now forcibly exclude any specifically named columns
            if exclude_columns:
                columns = [c for c in columns if c not in exclude_columns]

        if '#' in columns:
            self.add_idx(table)

        cols_width = self.table_columns_width(table, columns, column_headers)

        # newer releases of pyhon: 'yield from'
        # yield from table_header(cols_width, title, columns)
        for item in self.table_header(cols_width, title, columns, column_headers):
            yield item

        for row in table:
            line = ''
            for column in columns:
                if not name_is_compound_key(column):
                    line += '%-*s ' % (cols_width[column], row.get(column, ''))
            yield line + '\n'

        return


    def table_title_builder(self, name, indices_list):
        """
        Build a title, based on the table name, then
        added to that are any name:value paris in the
        indices_list, in order, whose value isn't None
        (None currently means the index is from the name of
        a 'CONTAINER', which doesn't require an index)
        """
        title = [utif.pluralize(name.capitalize())]
        if indices_list:
            for index_dict in indices_list:
                # not using a comprehension here to 
                # keep the text width small.
                for (n,v) in index_dict.items():
                    if v != None:
                        if n[0] == '+': # columns added as table links
                            title.append(utif.pluralize(n[1:]))
                        else:
                            title.append(utif.pluralize(self.column_headers[name].get(n,n)))
                        #title.append('%s:%s' % (n,v)) tables are globbed together
        return ' of '.join(title)


    def table_index_columns(self, name, indices_list, style):
        """
        The 'index columns' are the columns which have been
        used as 'keyNodeNames' for each of the 'LIST's.  These
        are best to move towards the 'left' side of the table
        """
        columns = []
        if style == 'table':
            columns = ['#']         # should this index column be configurable?

        if indices_list:
            for index_dict in indices_list:
                # schema LISTs without keyNodeNames don't need a printed column
                #columns += [n for (n,v) in index_dict.items() if v != None]
                for new_column in index_dict.keys():
                    if new_column not in columns:
                        columns.append(new_column)
                #columns += index_dict.keys()
        return columns


    def alias_replace(self, path, field, value, detail=None):
        # since each show command uses a new instance of this class,
        # the aliases are populated once during the generation of 
        # each show composition.

        if not path in self.alias:
            # compute name/value pairs
            self.alias[path] = {}
            self.alias[path][field] = {}
            af = self.bigdb.alias_field.get(path) # af <- a_lias f_ield
            try:
                (schema, results) = self.bigdb.schema_and_result(path, {},
                                                                 select = af)
            except Exception, e:
                # since 'path' has already been added to self.alias,
                # there's no need to cache negative results.
                # possibly alert the user about missing aliases?
                if debug.description():
                    print 'alias_replace: %s failed: %s' % (path, e)
                return value

            if schema == None:
                print 'alias_replace: no schema for "%s"' % path
            if results == None:
                return value
                
            for result in results.iter():
                # BSC-2992
                if af and af in result:
                    self.alias[path][field][result[field]] = result[af]
        if field in self.alias[path] and value in self.alias[path][field]:
            if detail == 'details':
                return '%s (%s)' % (value, self.alias[path][field][value])
            return self.alias[path][field][value]

        return value


    def replace_field_reference_with_idx(self, table, column,
                                               ref_table, ref_column):
        """
        References to other tables are build when no apparent
        keyNodeNames are present (no indices).
        """

        indexed = dict([[x[ref_column], '#%s' % x['#']] for x in ref_table])

        for row in table:
            if column in row:
                row[column] = indexed[row[column]]
 

    def compose_show(self, path, schema, result, style, detail = None):
        """
        From a result, populate this instance's variabes
        in preparation for display.

        Since this "flattens" the original result into a collection
        of entries, other actions can be performed after the tables
        are created to change the final output.

        """

        if detail == 'raw':
            # detail:raw is used when the data is not converted.
            # one case where this is used is when the fields are inverted
            # back to original commands for display.
            type_formatter = dict()
        else:
            type_formatter = dict(bigdb_type_formatter)
            for (n,v) in self.bigdb.alias_type_xref.items():
                if n in type_formatter:
                    continue
                keys = self.bigdb.search_keys[v]
                type_formatter[n] = lambda value, schema, detail, path=v, field=keys[0] : \
                                           self.alias_replace(path, field, value, detail)


        # Apply the result to the schema.
        # 'map_schema_to_results' is an iterator (generator), which
        # returns tuples.
        for row in self.bigdb.map_schema_to_results(path, schema, result,
                                                    type_formatter = type_formatter,
                                                    detail = detail):
            self.log('^ %s' % str(row))
            # return tuple:
            # (action, name, path, indices, row, new_row)
            # (action, name, path, indices, row, colunn_headers))
            #    0      1     2      3       4     5
            action = row[0]
            name = row[1]
            path = row[2]
            if action == 'LIST-BEGIN':
                if name not in self.tables_names:
                    self.tables_names.append(name)
                    self.tables[name] = []
                # ensure table is empty
                # if name in tables:
                    # tables[name] = []
            elif action == 'LIST-ITEM':
                # add the items to the table.

                # first the index's (left side of table)
                table_row = dict()
                for item in row[3]:
                    for (field, value) in item.items():
                        table_row[field] = value
                    if not name in self.refer:
                        self.refer[name] = []
                    if not field in self.refer[name]:
                        self.refer[name].append(field)
                # nww the related data.
                for (field, value) in row[5].items():
                    field_name = field
                    if field_name in table_row:
                        field_name = name + '-' + field
                    table_row[field_name] = value

                self.log("[%s]%s" % (name, table_row))

                # this is unplesant. identify alias fields to replace
                # XXX should this go away now that type_formatters exist?
                #for (field, value) in table_row.items():
                    #if field in self.bigdb.alias_xref:
                        #alias_xref = self.bigdb.alias_xref[field]
                        # silly: 'alias' check in current row: BSC-2992
                        #if len(alias_xref) == 1 and not 'alias' in table_row:
                            #nv = self.alias_replace(alias_xref[0], field, value)
                            #if nv != value:
                                #table_row[field] = nv

                if name in self.tables:
                    self.tables[name].append(table_row)
                else:
                    self.tables[name] = [table_row]
            elif action == 'LIST-END':
                if name in self.tables:
                    self.column_headers[name] = row[5]
                    self.titles[name] = self.table_title_builder(name, row[3])
                    self.columns[name] = self.table_index_columns(name, row[3], style)
                    self.log('COLUMNs %s %s %s' % (name, row[3], self.columns[name]))
            elif action == 'CONTAINER-BEGIN':
                if name not in self.tables_names:
                    self.titles[name] = name
                    self.tables_names.append(name)
                    self.tables[name] = []
                    self.columns[name] = []
            elif action == 'CONTAINER-END':
                table_row = dict(row[5])
                self.tables[name].append(table_row)
                self.column_headers[name] = row[6]
                self.titles[name] = self.table_title_builder(name, row[3])

            else:
                print 'compose-show:', action, row


    def remove_tables(self, table_names):
        """
        Remove tables (or single table) after the table was added...
        """
        if string_type(table_names):
            table_names = [table_names]
        for name in table_names:
            if name in self.tables:
                del self.tables[name]
        #
        self.tables_names = [x for x in self.tables_names if x not in table_names]

    
    def move_rows(self, to_table, from_table, row_limit):
        """
        many of the show proc's move tables around.
        """
        pass


    def add_tables_column_header(self, table, field, header):
        if table:
            if not table in self.column_headers:
                self.column_headers[table] = dict()
            self.column_headers[table][field] = header
        else:
            self.column_headers[field] = header


    def show_print(self, style = None,
                         select = None,
                         format = None,
                         detail = None,
                         force_title = None,
                         sort = None):
        """
        Generator to display output for this instance

        """

        # validation --
        for table in self.tables_names:
            if not table in self.tables.keys():
                self.log('List of tables doesn\'t match tables keys')
                self.log("%s %s" % (self.tables_names, len(self.tables_names)))
                self.log("%s %s" % (self.tables.keys(), len(self.tables.keys())))

        # help for the format writer
        if debug.description():
            print 'show_print: tables:', ', '.join(self.tables.keys())

        separator = None
        # select style.
        if style == 'list':
            prefix = '    '
            for (table_name, table_details) in self.tables.items():
                cols = 79
                first_columns = self.columns[table_name]
                last_columns = self.all_columns_except(table_details,
                                                       first_columns)
                if separator != None:
                    yield separator
                for row in table_details:
                    row_lines = 0
                    line = table_name + ' \n'
                    for item_name in first_columns + last_columns:
                        item_value = row.get(item_name)
                        if item_value == None:
                            continue
                        next_item = '%s: %s\n' % (item_name, item_value)
                        if len(line) + len(next_item) > cols:
                            yield line
                            line = prefix
                            row_lines += 1
                        line += next_item
                    if line != prefix:
                        yield line 
                    if row_lines:
                        self.log('')
                separator = ''

        elif style == 'detail': # intended to display a small number (1) of items
            table_keys = self.tables.keys
            if select:
                table_keys = [x for x in table_keys if x in select]
            prefix_needed = True if len(table_keys) > 1 else False
            for table_name in table_keys:
                table_details = self.tables[table_name]
                prefix = table_name + ' ' if prefix_needed else ''

                for row in table_details:
                    if len(row) == 0: # don't bother with emptys rows
                        continue

                    # only use final format as a column header override.
                    final_format = None
                    final_column_headers = {}
                    if format != None:
                        if detail in format[table_name]:
                            final_format = format[table_name][detail]
                        elif 'default' in format[table_name]:
                            final_format = format[table_name]['default']
                    if final_format:
                        for item in final_format:
                            if type(item) == tuple:
                                (name, value) = item
                                final_column_headers[name] = value
                    
                    col_headers = self.column_headers[table_name]

                    # rows may have different values
                    remaining_columns = [n for n in row if
                                         n not in self.columns[table_name]]
                    def col_length(n):
                        name = col_headers.get(n, n)
                        name = final_column_headers.get(name, name)
                        return len(name)
                    left_width = max([col_length(n) for n in row]) + 1

                    def format_order(columns):
                        if final_format == None:
                            return columns
                        order = []
                        for ff in final_format:
                            if type(ff) == tuple:
                                ff = ff[0]
                            if ff in columns:
                                order.append(ff)
                        for forgotten in [x for x in columns if not x in final_format]:
                            if forgotten[0] != '+': # no table references
                                order.append(forgotten)
                        return order

                    for n in format_order(self.columns[table_name] + remaining_columns):
                        name = col_headers.get(n, n)
                        name = final_column_headers.get(name, name)
                        yield '%s%-*s: %s\n' % (prefix, left_width, name, row[n])

        elif style == 'table':
            # now print the tables.
            if len(self.tables_names) == 0:
                yield 'None.'
                return

            if select and string_type(select):
                select = [select]

            selected_tables = select if select else self.tables_names
            for table_name in selected_tables:
                if separator != None:
                    yield separator

                if not table_name in self.tables or \
                   len(self.tables[table_name]) == 0:
                    if len(selected_tables) > 1:
                        yield '%s\nNone.' % table_name
                    else:
                        if force_title:
                            this_title = force_title
                            if type(force_title) != str:
                                this_title = self.titles[table_name]
                            # Should there be a test for a '' this_title?
                            for item in self.table_header_title(this_title, 0):
                                yield item
                        yield 'None.\n'
                else:
                    title = None
                    if force_title:
                        if string_type(force_title):
                            title = force_title
                        else:
                            title = self.titles[table_name]
                    if len(selected_tables) > 1:
                        title = self.titles[table_name]

                    requested_columns = None
                    if format and format.get(table_name):
                        if detail in format[table_name]:
                            requested_columns = format[table_name][detail]
                        elif 'default' in format[table_name]:
                            requested_columns = format[table_name]['default']

                    # for columns which have been added ('+' leading character)
                    # if this column is references by other tables, then retain it,
                    # otherwise exclude it.
                    exclude = []
                    for column in self.columns[table_name]:
                        if column[0] == '+':
                            for (refer_table_name, refer_values) in self.refer.items():
                                if column in refer_values:
                                    other_name = column[1:]
                                    if table_name != other_name and other_name in selected_tables:
                                        this_table = self.tables[table_name]
                                        other_table = self.tables[other_name]
                                        self.replace_field_reference_with_idx(this_table,
                                                                              column,
                                                                              other_table,
                                                                              column)
                                        break
                                    else:
                                        exclude.append(column)
                            else:
                                exclude.append(column)

                    sort_columns = sort
                    if type(sort) == dict:
                        sort_columns = sort.get(table_name, sort)
                    if sort_columns:
                        if string_type(sort_columns):
                            sort_columns = [sort_columns]

                    for item in self.table_body(self.tables[table_name],
                                                title,
                                                self.columns[table_name],
                                                self.column_headers[table_name],
                                                exclude,
                                                requested_columns,
                                                sort_columns):
                        yield item
                separator = ''
        else:
            yield 'bigdb:display unknown style %s' % style


    def show(self, path, schema, result,
             style = None, select = None, sort = None, format = None, detail = None, force_title = None):
        """
        Generator to display output for some path.

        """

        if style == None:
            style = 'table'

        if schema == None:
            yield 'No schema'
            return

        if result == None:
            self.log('No result')
            return

        self.compose_show(path, schema, result, style, detail)

        for item in self.show_print(style, select, format, detail, force_title, sort):
            yield item


class BigDB_RC_scoreboard():
    # The scoreboard is intended to collect commands associated with
    # submodes, where each submode command is a complete command with
    # all prameters intended to completely decribe where the final
    # command ought to be painted.
    #
    # The problem this is trying to solve -- 
    # imagine an application, for example bigtap, which decorates
    # commands into other existing modes.   Bigtap provides a 
    # 'bigtap role <x> bigtap-name <y>' command for config-switch-interface.
    # If our goal is to generate the complete running config, then
    # bigtap will deliver this config via applications/bigtap/interface-config,
    # but the running config generated will be: 
    #  switch <dpid>
    #    interface <some-interface>
    #      bigtap role <x> bigtap-name <y>
    #
    # The idea is to have all the configuration appear for this 
    # specific switch only once, for bigtap configuration, and also for
    # any other application specific configuraion.
    #

    # Then we provide:
    #  generate( (tuple of submode commands), command_string)
    #    push a command_string for the tuple.
    #  report()

    # Scheduling.
    #  One of the issues with the running config is ordering of the
    #  final commands.  'address-space' config needs to appear before
    #  any config item which needs 'address-space'
    #
    #  If the schema provided rich support for references, these could
    #  be used to also improve the ordering of the output,  with items
    #  which are "referred-to" required to appear before the items
    #  which refer to them.

    # Just kept as a reference, this is the root rc-order of the
    # core commands:
    #
    #    'no feature'      13000000
    #    'feature',        12000000
    #    'ntp',            11000000
    #    'syslog',         10000000
    #    'snmp-server',     9000000
    #    'tacacs',          8000000
    #    'user',            7000000
    #    'group',           6000000
    #    'controller-node', 5000000
    #    'switch',          4000000
    #    'address-space',   3000000
    #    'host',            2000000
    #    'rbac',            1000000

    def __init__(self, with_errors = None):
        self.node = {}                      # nested submode 
        self.commands = []                  # command for this submode
        self.collected_errors = []
        self.with_errors = with_errors


    def add_error(self, new_error):
        self.collected_errors.append(new_error)


    def put(self, submode, config):
        #
        # Each of the parameters is intended to be a tuple, which
        # encodes not just the text of the command but also other
        # values.   The value of most significance here is the
        # rc-order, the 'priority' of the command.   This priority
        # is intended to be described on the command description, and
        # then associated with the commands as they're generated.
        # The command description attribute is 'rc-order', which 
        # in intended to be associated at the base portion of the
        # description.
        #
        # For rc-order, higher values mean earlier in the ordering.
        #

        def add_spaces_to_string_match(rc_order):
            return tuple([x if type(x) == int else
                            x + ' ' if type(x) == str and x[-1] != ' '
                                else x
                          for x in rc_order])

        def validate_rc_order_tuple(rc_order):
            # return True if some string needs a space, but also
            # verify that the rc_order only has strings and a single integer
            any_need_spaces = False
            for order in rc_order:
                if type(order) == int:
                    if found_integer:
                        print 'rc-scoreboard: put: Invalid constriant', \
                              'multiple integers' , rc_order
                elif type(order) == str:
                    if order[-1] != ' ':
                        any_need_spaces = True
                else:
                    print 'rc-scoreboard: put: Invalid constriant', \
                          'not integer nor string' , rc_order
            return any_need_spaces

        node = self
        for s in submode:
            rc_order = None
            if type(s) == tuple:

                # see submode_enter_commands; last line builds this tuple
                (s, command_mode, command_name, submodes_fields, rc_order) = s

                # if s is None, then submode_fields will be populated
                # this is used to associatee submode_fiels with ambiguous
                # submode commands.
                if s == None:
                    continue
                
                if type(rc_order) == int:
                    # notice that rc_order is inverted here, since higher
                    # order-priorities indicate to appear first.
                    rc_order = -rc_order
                elif type(rc_order) == tuple:
                    # each item must be either an integer or a string,
                    # only one integer may appear in the tuple.
                    found_integer = False
                    if validate_rc_order_tuple(rc_order):
                        rc_order = add_spaces_to_string_match(rc_order)
            if rc_order == None:
                rc_order = 0
            if not (rc_order, s) in node.node:
                node.node[(rc_order, s)] = BigDB_RC_scoreboard()
            node = node.node[(rc_order, s)] # follow
        if type(config) == list:
            for single_config in config:
                rc_order = None
                if type(single_config) == tuple:
                    (single_config, rc_order) = single_config
                    if type(rc_order) == int:
                        # notice that rc_order is inverted here, since higher
                        # order-priorities indicate to appear first.
                        rc_order = -rc_order
                    elif type(rc_order) == tuple:
                        if validate_rc_order_tuple(rc_order):
                            rc_order = add_spaces_to_string_match(rc_order)
                # when its not a tuple, just use rc_order <- 0
                if rc_order == None:
                    rc_order = 0
                if not (rc_order, single_config) in node.commands:
                    node.commands.append((rc_order, single_config))
        elif config:
            rc_order = None
            if type(config) == tuple:
                (config, rc_order) = config
                if type(rc_order) == int:
                    # notice that rc_order is inverted here, since higher
                    # order-priorities indicate to appear first.
                    rc_order = -rc_order
                elif type(rc_order) == tuple:
                    if validate_rc_order_tuple(rc_order):
                        rc_order = add_spaces_to_string_match(rc_order)
            # when its not a tuple, just use rc_order <- 0
            if rc_order == None:
                rc_order = 0
            if not (rc_order, config) in node.commands:
                node.commands.append((rc_order, config))


    def generate_errors(self):
        if self.collected_errors:
            collapsed_errors = set(self.collected_errors)
            if 403 in collapsed_errors:
                yield 'Running config incomplete due to insufficient privilege'
            elif 401 in collapsed_errors:
                yield 'Running config incomplete due to expired authentication token'
            # possible only
 

    def generate(self, context = None):

        if self.with_errors:
            for item in self.generate_errors():
                print 'Warning: \n' + item
                yield '!\n'
                yield '! Warning: \n' + item

        if context == None:
            context = []
        depth = len(context)

        def sort_items(items):
            def compare(x,y):
                # All items should be tuples, with the first value
                # representing a priority, and the second the command.
                #
                # The priority can be either an integer, or a tuple,
                # if its a tuple, it's composed of only integers and 
                # strings.
                #
                # When the priority is a tuple is a string, then
                # these are prefix's of other commands which must
                # appear before this command.
                #
                prio_x = x[0]       # x[1] is x's command
                prio_y = y[0]       # y[1] is y's command
                type_prio_x = type(prio_x)
                type_prio_y = type(prio_y)
                
                if type_prio_x == tuple:
                    if type_prio_y == tuple:
                        # tuple and tuple comparison
                        for order in prio_x:
                            if type(order) == int:
                                prio_x = order
                                break
                        else:
                            prio_x = 0
                        for order in prio_y:
                            if type(order) == int:
                                prio_y = order
                                break
                        else:
                            prio_y = 0
                        return cmp((prio_x, x[1]), (prio_y, y[1]))
                    else:
                        # tuple and integer comparison
                        for order in prio_x:
                            if type(order) == str:
                                if y[1].startswith(order):
                                    return 1
                            elif type(order) == int:
                                prio_x = order
                        # if an integer appeared, prio_x is now a different type
                        if type(prio_x) != int:
                            return cmp((0, x[1]), y)
                        return cmp((prio_y, x[1]), y)
                elif type_prio_y == tuple:
                    for order in prio_y:
                        if type(order) == str:
                            if x[1].startswith(order):
                                return -1
                            elif type(order) == int:
                                prio_y = order
                    # if an integer appeared, prio_x is now a different type
                    if type(prio_y) != int:
                        return cmp(x, (0, y[1]))
                    return cmp(x, (prio_y, y[1]))
                return cmp(x,y)

            return sorted(items, cmp = compare)

        # nodes.
        last_first_word = None
        for (priority, command) in sort_items(self.commands) + sort_items(self.node.keys()):
            if (priority, command) in self.commands:
                first_word = command.split()[0]
                if first_word == 'no':
                    first_word = command.split()[1]
                if len(context) == 0:
                    yield '\n'
                    if last_first_word != first_word:
                        yield '! %s\n' % first_word # between top level commands
                last_first_word = first_word
                yield '  ' * depth + command + '\n'
            else:
                node = self.node[(priority, command)]

                if depth == 0:
                    first_word = None
                    if command[0] != ' ':
                        first_word =  command.split()[0]
                        if first_word == 'no':
                            first_word = command.split()[1]
                        yield '\n'
                        if last_first_word != first_word:
                            yield '! %s\n' % first_word
                        yield '  ' * depth + command + '\n'
                    if first_word:
                        last_first_word = first_word
                else:
                    yield '  ' * depth + command + '\n'

                for item in node.generate(context + [command]):
                    yield item

    def report(self):
        print '-----'
        for item in self.generate():
            print item
        print '-----'


class BigDB_run_config():

    def __init__(self, bigsh, bigdb, command_registry = command.command_registry):
        #
        # depends on both a schema, and a command registry.
        #
        # the schema identifes traverals of the resulting configuration
        # data, and the command registry describes the relationship of
        # the various configured fields to the command's syntax.
        #
        #
        self.bigsh = bigsh
        self.bigdb = bigdb

        self.command_ref = {}

        self.target_modes = {}
        self.target_mode_command = {}
        self.mode_graph = {}
        self.submode = {}
        self.mode_path = { 'config' : None, 'login' : None, 'enable' : None }

        # mode to object mapping for submode commands without path's or obj-type's
        self.submode_without_object = {} # index is mode
        self.submode_without_object_path = {} # index is path

        self.debug = False
        if debug.cli():
            self.debug = True

        # Determine the submodes of all bigdb related commands,
        # then find all the command which work in that submode

        for command in command_registry: 
            command_self = command['self']
            self.command_ref[command_self] = command
            cmd_type = command.get('command-type')
            path = command.get('path')
            # print 'RC', command['self'], cmd_type
            if cmd_type == 'config-submode':
                from_mode = command.get('mode')
                to_mode = command.get('submode-name')

                self.mode_graph[from_mode] = to_mode

                if path:
                    self.log('RC', command_self, cmd_type, path,
                                 self.bigdb.path_configurable.get(path))
                    #self.submode[path] = command
                    if to_mode in self.target_modes and self.target_modes[to_mode] != path:
                        if debug.description():
                            # needs work to allow multiple commands to enter each submode.
                            print 'SUBMODE: MULTIPLE ENTRY INTO MODE', command['self'], to_mode, self.target_modes[to_mode], path
                    self.target_modes[to_mode] = path

                    # The current code depends on only one config-submode
                    # command to transition to any specific submode.  This
                    # could be relaxed to manage multiple descriptions which
                    # are only specific variations.
                    if to_mode in self.target_mode_command:
                        print 'MULTIPLE COMMANDS TRANSIT TO SAME MODE:', \
                            command_self, self.target_mode_command[to_mode]
                    self.target_mode_command[to_mode] = command_self

                    if not path in self.submode:
                        self.submode[path] = []
                    self.submode[path].append(command_self)
                    self.mode_path[to_mode] = path
                else:
                    obj_type = command.get('obj-type')
                    if obj_type == None:
                        # pure submode transition command
                        if debug.description():
                            print 'PURE SUBMODE TRAMS', command['self']
                        if not to_mode in self.submode_without_object:
                            self.submode_without_object[to_mode] = []
                        self.submode_without_object[to_mode].append(command['self'])

        # see if the submode transfer for this 
        # command can be located, and add the transition
        # note: doen't yet describe multiple submode command for some config command,
        for command in command_registry: 
            cmd_type = command.get('command-type')
            path = command.get('path')
            if cmd_type == 'config-object' and path:
                mode = command.get('mode')
                if mode[-1] == '*': # XXX canonicalize mode?
                    mode = mode[:-1]
                if mode in self.submode_without_object:
                    if debug.description():
                        print 'SUBMODE_WITHOUT_OBJECT', command['self'], mode
                    if mode != 'config':
                        if not path in self.submode_without_object_path:
                            self.submode_without_object_path[path] = []
                        self.submode_without_object_path[path] += self.submode_without_object[mode]
                elif not path in self.submode:
                    if debug.description():
                        print 'MISSING TRANS', path
                    (s_path, _, _) = path.rpartition('/')
                    while is_path(s_path):
                        if s_path in self.submode:
                            if debug.description():
                                print 'MATCH AT     ', s_path
                            break
                        (s_path, _, _) = s_path.rpartition('/')
                    else:
                        if debug.description():
                            print 'FIXING', mode
                        # look for a command which transitions to this mode
                        if mode in self.target_modes:
                            # hook up this path to this submode command
                            if debug.description():
                                print 'TRYING', self.target_modes[mode]
                            self.target_modes[mode] = path
                            if not mode in self.target_mode_command:
                                print 'run_config: __init__: missing mode', node
                                for x in self.target_mode_command:
                                    print '--', x,  self.target_mode_command[x]
                            command_name = self.target_mode_command[mode]

                            if not path in self.submode:
                                self.submode[path] = []

                            # submode_enter_command decorates any higher
                            # commands, only add the associated command
                            #
                            upper_command_name = self.target_mode_command[mode]
                            self.submode[path].append(upper_command_name)

                        else:
                            # check to see if there's a submode entry
                            # command for this path
                            self.target_modes[mode] = path
                            if mode in self.submode_without_object:
                                command_names = self.submode_without_object[mode]
                                if not path in self.submode:
                                    self.submode[path] = []
                                self.submode[path] += command_names

        # compute the top path elements from the target nodes.
        # when generating the running-config, these are the queries
        # which will get issued.
        self.top_paths = {}
        for (from_mode, from_path) in self.target_modes.items():
            if from_path in self.top_paths:
                continue

            # see if there's a prefix in the top modes which this
            # is part of
            for item in self.top_paths:
                if from_path.startswith(item):
                    break
            else:
                # this ought to be added, first see if there's
                # items which are childen of this node.
                prune = []
                for item in self.top_paths:
                    if not item.startswith(from_path):
                        prune.append(item)
                self.top_paths = prune
                self.top_paths.append(from_path)
                self.log('COLLAPSE', from_path, self.top_paths)
        
        if debug.description():
            print 'TOP PATH ', self.top_paths

        def match_mode(modes):
            if string_type(modes):
                modes = [modes]
            for mode in modes:
                if mode in self.target_modes:
                    return True
                if mode in self.submode_without_object:
                    return True
                if mode.endswith('*'):
                    find_mode = mode[:-1]
                    if find_mode in self.target_modes:
                        return True
            return False


        # indexed first by command_self, then field
        self.command_fields_types = {}


        def attach_command_fields_type(command_self, field, field_type):
            if field_type == None:
                return

            if not command_self in self.command_fields_types:
                self.command_fields_types[command_self] = {}
            if not field in  self.command_fields_types[command_self]:
                self.command_fields_types[command_self][field] = []
            self.command_fields_types[command_self][field].append(field_type)


        def fields_associated(command, command_self):
            fields = []

            def args_recurse(args, fields):
                if type(args) == tuple:
                    for arg in args:
                        args_recurse(arg, fields)
                elif type(args) == dict:
                    if 'choices' in args:
                        for arg in args['choices']:
                            args_recurse(arg, fields)
                    elif 'args' in args:
                        args_recurse(args['args'], fields)
                        attach_command_fields_type(command_self,
                                                   args['field'],
                                                   args.get('type'))
                    # 
                    if 'field' in args:
                        fields.append(args['field'])
                    if 'data' in args:
                        fields += args['data'].keys()
                elif string_type(args):
                    pass
                else:
                    print 'bigdb_run_config: cmd-field type', type(args)
                
            if 'data' in command:
                fields += command['data'].keys()

            if 'name' in command and type(command['name']) == dict:
                if 'field' in command['name']:
                    fields.append(command['name']['field'])

            if 'submode-data' in command:
                if type(command['submode-data']) == dict:
                    for (n,v) in command['submode-data'].items():
                        # for 'submode-data' the dictionary maps from
                        # the submode-name to the field to populate,
                        # here the field name 'v' is what's needed.
                        fields.append(v)

            # look for data items associated with actions.
            if 'action' in command:
                action = command['action']
                if type(action) == dict:
                    if 'data' in action and type(action['data']) == dict:
                        fields += action['data'].keys()
                elif type(action) == tuple:
                    for an_action in action:
                        if type(an_action) == dict:
                            if 'data' in an_action and type(an_action['data']) == dict:
                                fields += an_action['data'].keys()
            args_recurse(command.get('args'), fields)

            return fields

            
        self.command_fields = {}
        self.path_commands = {}

        self.log('TARGET MODES', self.target_modes)
        self.log('MODE_PATH', self.mode_path)

        for command in command_registry: 
            command_self = command.get('self')
            mode = command.get('mode')
            cmd_type = command.get('command-type')

            # XXX not all command are marked with type at the highest level
            if not cmd_type in ['config', 'config-object', 'config-submode']:
                continue

            # XXX predicate?
            if match_mode(mode) or cmd_type == 'config-submode' or \
               mode == 'config' or mode == 'config*':
                if 'path' in command:
                    path = command['path']
                else:
                    mode_index = mode[:-1] if mode.endswith('*') else mode
                    if not mode_index in self.mode_path:
                        if debug.description():
                            print 'MISSING PATH', mode_index, command_self
                        continue
                    path = self.mode_path[mode_index]
                    
                fields = fields_associated(command, command_self)
                self.command_fields[command['self']] = fields
                if not path in self.path_commands: 
                    self.path_commands[path] = []
                for field in fields:
                    if not is_path(field):
                        if not command['self'] in self.path_commands[path]:
                            self.path_commands[path].append(command_self)
                    else: # fields like: output-data/max-length
                        parts = field.rpartition('/')
                        total_path = path_adder(path, parts[0])
                        # improve: uses a poor technique to determine whether
                        #  the concatanation for total_path makes sense.
                        if is_path_prefix_of(path, parts[0]):
                            total_path = field
                        if not total_path in self.path_commands:
                            self.path_commands[total_path] = []
                        if not command['self'] in self.path_commands[total_path]:
                            self.path_commands[total_path].append(command_self)

                self.log( '**', command['self'], mode, cmd_type, path, fields)
                #if cmd_type == 'config-object':
                    # Some commands populate a path in the tree, but don't
                    # have an associated submode command entry.
                    # print 'NO SUBMODE?', path, self.submode.get(path)
                    # self.submode[path] = None what's this for?


    def log(self, *args):
        if self.debug == False:
            return
        for arg in args:
            print arg,


    def generate(self, tops = None, scoreboard = None, detail = None, with_errors = None):

        if scoreboard == None:
            scoreboard = BigDB_RC_scoreboard(with_errors = with_errors)
        self.score = scoreboard

        # detail manages whether the default values are excluded.
        # it may have a deeper meaning later.
        self.detail = detail

        def container_children(base, field_base, schema, result):
            self.log('CONTAINERC', schema, result)
            for (field, field_details) in schema.items():
                if not field in result:
                    continue
                self.log('2FUD', field, result.get(field))
                base_path = path_adder(base, field)
                if self.bigdb.path_configurable.get(base_path, False) == False:
                    self.log( 'SKIP not config', field)
                    continue
                node_type = field_details.get('nodeType')
                field_path = path_adder(field_base, field)
                if node_type == 'LIST':
                    pass
                if node_type == 'LEAF':
                    yield (field_path, result[field])
                elif node_type == 'LEAF_LIST':
                    yield (field_path, result[field])
                elif node_type == 'CONTAINER':
                    container_children(base_path,
                                       field_path,
                                       field_details['childNodes'],
                                       result[field])
            return


        def commands_for_leaf_list(self, path, collect_leaf_list,
                                         submode_commands, field_group):

            self.log('PC LEAF-LIST', path, self.path_commands.get(path),
                     collect_leaf_list)

            command_count = {}
            for command_self in self.path_commands.get(path):
                # collect candiates.
                self.log( 'FIELDS', command_self, self.command_fields[command_self])
                for leaf in collect_leaf_list:
                    if leaf in self.command_fields[command_self]:
                        if not command_self in command_count:
                            command_count[command_self] = 0
                        command_count[command_self] += 1
            if debug.description():
                print 'COUNTS leaf_list', command_count, path, self.path_commands.get(path)
            
            for field in collect_leaf_list:
                field_path = path_adder(path, field)
                for command_self in self.path_commands.get(field_path, []):
                    if field in self.command_fields[command_self]:
                        if not command_self in command_count:
                            command_count[command_self] = 0
                        command_count[command_self] += 1
                        continue
                    field_path = path_adder(path, field)
                    if field_path in self.command_fields[command_self]:
                        if not command_self in command_count:
                            command_count[command_self] = 0
                        command_count[command_self] += 1
                        continue
            if debug.description():
                print 'COUNTS leaf_list+elem', command_count, path, self.path_commands.get(path)

            # for now, assume each leaf list is separate, and uses
            # no other related field from collect_leaf.   This means
            # we iterate over each name, then try to find the command
            # to configure each element separately

            # walk from the highest to the lowest counts

            for command_self in sorted(command_count, key=command_count.get):
                command_desc = self.command_ref[command_self]
                rc_order = command_desc.get('rc-order')

                # collect fields which need to be configured
                fields = {}
                for (n,v) in collect_leaf_list.items():
                    if n in self.command_fields[command_self]:
                        fields[n] = v
                    field_path = path_adder(path, n)
                    if field_path in self.command_fields[command_self]:
                        fields[field_path] = v

                #
                if len(fields) == 1:
                    for (field_name, leaf_list_field_values) in fields.items():
                        self.log('FNLF', field_name, leaf_list_field_values)
                        for field_value in leaf_list_field_values:
                            leaf_list_dict = { field_name: field_value }
                            self.log( 'LEAF DO GENERATE', command_self, leaf_list_dict)

                            permute = command.CommandPermutor(field_values = leaf_list_dict)
                            # permute_command returns newline joined list of generated commands
                            permute.permute_command(command_desc, un_config = False)
                            choices = permute.collect

                            self.log( 'LEAF GENERATED', choices)
                            # only config commands, submode-fields and config-field perhaps
                            # ought to be two different variables, but the commands are generated
                            # and the associated fields which were used in the command are 
                            # check-ed off in the schedule
                            if command_desc.get('command-type') == 'config-submode':
                                self.log( 'SKIP SUBMODE ENTRY', command_self, path, self.bigdb.path_config_only.get(path, False))
                                continue

                            #
                            shortest = choices[0]
                            for choice in choices:
                                if len(choice.strip()) < len(shortest.strip()):
                                    shortest = choice

                            #
                            field_group_index = tuple((','.join(fields.keys()) + \
                                                      ':' + str(field_value),))

                            if not field_group_index in field_group:
                                field_group[field_group_index] = []

                            field_group[field_group_index].append(
                                (
                                    shortest,
                                    command_desc['mode'],
                                    command_self,
                                    rc_order,
                                ))
                else:
                    print 'LEAF LIST COMMAND LEN NOT ONE', command_self, fields


        def commands_for_leaf(self, path, collect_leaf,
                                    submode_commands, field_group):

            # Find associated commands which modify these fields
            #
            # There is a naive notion here that if a command uses a field,
            # then there is a command variation which can be generated
            # to populate all those fields.
            #
            # An additional issue would be commands which require
            # repeated syntactic elements to populate all the fields,
            # since the permutors can't yet accommodate that.

            self.log( 'PC', path, self.path_commands.get(path), collect_leaf)
            commands_path = path

            if not self.path_commands.get(path): # None or zero length
                keys = self.bigdb.search_keys.get(path)
                if keys and len(keys) == 1:
                    key = keys[0]
                    if key in collect_leaf:
                        key_path = path_adder(path, key)
                        if key_path in self.path_commands:
                            commands_path = key_path
                if commands_path == path:
                    if debug.description():
                        print 'No commands associated with path:', path
                    return

            if debug.description():
                print 'commands_for_leaf:', path, commands_path, collect_leaf, field_group, self.path_commands.get(commands_path)
            command_count = {}
            for command_self in self.path_commands.get(commands_path):
                # collect candiates.
                self.log( 'FIELDS', command_self, self.command_fields[command_self])
                for leaf in collect_leaf:
                    if leaf in self.command_fields[command_self]:
                        if not command_self in command_count:
                            command_count[command_self] = 0
                        command_count[command_self] += 1
                    else:
                        leaf_path = path_adder(path, leaf)
                        if leaf_path in self.command_fields[command_self]:
                            if not command_self in command_count:
                                command_count[command_self] = 0
                            command_count[command_self] += 1
            if debug.description():
                print 'commands_for_leaf: COUNTS leaf', command_count, field_group

            # Need a better scheduler here
            # -- order the commands based on the number of fields.
            # -- as each command is selected, remove the fields which are generated
            # -- the next commands must have an associated, un-configured
            #    field to be included

            # walk from the highest to the lowest counts
            for command_self in sorted(command_count, key=command_count.get):
                # collect fields which need to be configured
                fields = {}
                for (n,v) in collect_leaf.items():
                    if n in self.command_fields[command_self]:
                        fields[n] = v
                    field_path = path_adder(path, n)
                    if field_path in self.command_fields[command_self]:
                        fields[field_path] = v

                if debug.description():
                    print 'DO GENERATE', command_self, fields, self.command_fields[command_self]

                command_desc = self.command_ref[command_self]
                rc_order = command_desc.get('rc-order')

                permute = command.CommandPermutor(field_values = dict(fields))
                # permute_command returns the newline joined list of generated commands
                permute.permute_command(command_desc, un_config = False)
                choices = permute.collect
                if debug.description():
                    print 'GENERATED', choices, fields

                # only config commands, submode-fields and config-field perhaps
                # ought to be two different variables, but the commands are generated
                # and the associated fields which were used in the command are 
                # check-ed off in the schedule
                if self.command_ref[command_self].get('command-type') == 'config-submode':
                    self.log( 'SKIP SUBMODE ENTRY', command_self, path, self.bigdb.path_config_only.get(path, False))
                    continue

                if len(choices) == 0:
                    # The first pass only generated command varations where every field
                    # was required to be consume to generate a result.   If two fields
                    # are configured, and each single field is generated by a single
                    # command varation, that strategy won't work.  Instead, groupings of
                    # the fields associated with the commands needs to be generated.
                    #
                    # Permutations of the fields wrt the commands may generate multiple commands
                    # from this single description.  The issue here is the order O(2^n) of
                    # computation to visit all the field permutations.  One partial solution
                    # to pick some upper boundary, for example 8 fields, and complain
                    # about the computational cost when this limit is reached.

                    # iterate through all the possible choices of different field
                    # values assocated with 'fields'.   Let 'schedule' simply count
                    # the choices, and use the binary value of each bit to select
                    # the removal of a single field.
                    field_list = fields.keys() # used to apply the schedule to the fields.

                    if len(fields) > 8:
                        if command_desc.get('command-type') == 'config-object':
                            # For config-object's which update more than eight
                            # fields, skip the binomial search of matches of
                            # the associated fields to the command.
                            continue
                        print 'Command:', command_self, ' extensive variations', len(fields)

                    def schedule_remove(schedule):
                        # based on a schedule, which is a collection of 1 bits,
                        # remove fields identified by the 1 bits.
                        result_fields = dict(fields)
                        index = 0
                        while schedule:
                            if schedule & 1:
                                del result_fields[field_list[index]]
                            schedule >>= 1
                            index += 1
                        return result_fields
                        
                    for schedule in range(1,(2**len(fields)) - 1):
                        smaller_fields = schedule_remove(schedule)

                        permute = command.CommandPermutor(field_values = dict(smaller_fields))
                        # permute_command returns the newline joined list of generated commands
                        permute.permute_command(command_desc, un_config = False)
                        choices = permute.collect
                        if debug.description():
                            print 'GENERATED-%s' % schedule, choices, fields
                        if len(choices) == 0:
                            continue
                        shortest = choices[0]
                        for choice in choices:
                            if len(choice) < len(shortest):
                                shortest = choice

                        #
                        field_group_index = tuple(sorted(smaller_fields.keys()))
                        if not field_group_index in field_group:
                            field_group[field_group_index] = []

                        field_group[field_group_index].append(
                             (
                                 shortest,                  # text of command
                                 command_desc['mode'],      # mode for the command
                                 command_self,              # command desc name
                                 rc_order,                  # running config priority
                             ))

                    continue
                    
                #
                shortest = choices[0]
                for choice in choices:
                    if len(choice) < len(shortest):
                        shortest = choice
                field_group_index = tuple(sorted(fields.keys()))
                if not field_group_index in field_group:
                    field_group[field_group_index] = []
                depth = len(submode_commands)
                field_group[field_group_index].append(
                    
                        (
                          shortest,                     # text of command
                          command_desc['mode'],         # mode for the command
                          command_self,                 # command desc name
                          rc_order,                     # running config priority
                        )
                    )

            if debug.description():
                # verify that all fields to be configured are covered 
                # within the field group.

                collapsed_fields = []
                for fg in field_group.keys():
                    collapsed_fields += [item for item in fg
                                         if item not in collapsed_fields]
                if sorted(collapsed_fields) != sorted(collect_leaf.keys()):
                    print 'Config: issue: want: %s got: %s' % (
                         sorted(collect_leaf.keys()), sorted(collapsed_fields))


        def collect_integer_comma_ranges(collect_leaf, field, schema, results):
            # Special case code to manage convertion of collections of integers
            # into a comma separated collection of integers and ranges

            if self.bigsh.description:
                print 'collect_integer_comma_ranges:', schema.keys()

            node_type = schema['nodeType']

            # Convert existing results into a list of
            if node_type == 'LIST':
                # find the member name
                list_element_node = schema.get('listElementSchemaNode')
                list_children_nodes = list_element_node.get('childNodes')
                candidates = list_children_nodes.keys()
                if len(candidates) == 1:
                    key = candidates[0]
                    values = sorted([x[key] for x in results[field]])
                else:
                    print 'collect_integer_comma_ranges: ', candidates
                    return

            elif node_type == 'LEAF_LIST':
                values = results[field]


            if len(values) > 0:
                # compute the value, attach it to collect_leaf
                # XXX improve, use schema to figure the data out
                new_value = []
                first_value = values[0]
                last_value = first_value
                for v in values[1:]:
                    if v != last_value + 1:
                        if first_value == last_value:
                            new_value.append(str(first_value))
                        else:
                            new_value.append('%s-%s' %
                                             (first_value,
                                             last_value))
                        first_value = v
                        last_value = v
                    else:
                        last_value = v
                # last element.
                if first_value == last_value:
                    new_value.append(str(first_value))
                else:
                    new_value.append('%s-%s' %
                                     (first_value,
                                     last_value))
                collect_leaf[field] = ','.join(new_value)


        def config(path, children, result, submode_commands):

            # the schema is pointing at "children" of a node,
            # as is the result.  collect items which are 
            # intended to configurecd.

            self.log( 'CI', path, children.keys())
            collect_leaf = {}
            collect_leaf_list = {}
            self.log( 'PAIRS', [x for x in children.keys() if x in result])

            # field_group is indexed by a tuple, which is the sorted names
            # of all the fields consumed by the associated list of commands.
            field_group = {}

            for (field, field_details) in children.items():
                if not field in result: 
                    continue
                self.log( 'FUD', field, result.get(field))
                node_type = field_details.get('nodeType')
                field_path = path_adder(path, field)
                if node_type == 'LEAF':
                    if self.detail == 'details':
                        # if details is set, then don't bother using the
                        # defaultValue to exclude this leaf value..
                        pass
                    elif 'defaultValue' in field_details:
                        default_value = field_details['defaultValue']
                        if result[field] == default_value:
                            # This presumes that if a field has a default value,
                            # and the result matches, the value can be excluded
                            # from the running-config.  Perhaps this should be 
                            # configurable.
                            if debug.description():
                                print 'config: field skipped due to default vaule:', field, default_value
                            continue # skip this field
                    if self.bigdb.path_configurable.get(field_path, False) == True:
                        self.log( 'CI LEAF', field_path, result[field])
                        collect_leaf[field] = result[field]
                elif node_type == 'LEAF_LIST':
                    if self.bigdb.path_configurable.get(field_path, False) == True:
                        self.log( 'CI LEAF_LIST', field_path, result[field])
                        # right thing here may be to generate commands recursivly
                        # based on single values of this field, but i'm not yet
                        # sure what that means for the other fields' vlues, and 
                        # what would happen when multiple LEAF_LISTs appeared
                        # in one submode.
                        # This makes sense since multiple LEAF_LISTS would have
                        # been collected into larger LEAF's subnodes if the 
                        # entries were somehow co-related.
                        collect_leaf_list[field] = result[field]
                elif node_type == 'CONTAINER':
                    # add singleton fields
                    child_schema = field_details['childNodes']
                    for item in container_children(field_path,
                                                   field,
                                                   child_schema,
                                                   result[field]):
                        if type(item) == tuple:
                            collect_leaf[item[0]] = item[1]
                            self.log( 'CONTAINER children', item)
                        # XXX assume if these are found, they'll get used.
                elif node_type == 'LIST':
                    # special case.
                    # rc-interger-comma-range
                    # Look for commands associated with this path, check to see
                    # if any fields associated with this command is 'integer-comma-range'
                    # type.  In this case, see if any of the assciated values exsit,
                    # and backward-convert a collection of int's into a comma
                    # separated list of ranges.
                    #
                    for command_self in  self.path_commands.get(path, []):
                        # lookup the fields of the commands.
                        for (f, t) in self.command_fields_types[command_self].items():
                            if 'integer-comma-ranges' in t:
                                collect_integer_comma_ranges(collect_leaf,
                                                             f,
                                                             field_details,
                                                             result)
                else:
                    self.log( 'CONFIG: type', node_type, field, result)

            if debug.description():
                print 'config: collected:', path, collect_leaf, collect_leaf_list
                
            # collect_leaf contains all the leaf's in the result
            # at this path/depth which need configuration. 
            #
            # Business end of the work is done in these two calls
            # below.  The results are gathered together into th
            # field_group dict, which is indexed by the fields
            # consumed by the commands.
            if len(collect_leaf_list):
                commands_for_leaf_list(self, path, collect_leaf_list,
                                             submode_commands, field_group)

            if len(collect_leaf):
                commands_for_leaf(self, path, collect_leaf,
                                        submode_commands, field_group)
            results = []
            # now the next level down
            for (field, field_details) in children.items():
                name = field_details.get('name')
                node_type = field_details.get('nodeType')
                list_path = path_adder(path, name)
                self.log( 'CCONFIG', list_path, name, node_type, self.bigdb.path_configurable[list_path])
                if not self.bigdb.path_configurable[list_path]:
                    continue
                descend_result = None
                if node_type == 'LIST' and name in result:
                    descend_result = descend(list_path, field_details, result[name], submode_commands)
                    self.log( 'LIST RESULT', result)
                elif node_type == 'CONTAINER' and name in result:
                    descend_result = descend(list_path, field_details, result[name], submode_commands)
                    self.log( 'CONTAINER RESULT', result)
                # more choices?
                if descend_result:
                    results += descend_result

            # knapsack issues.

            # this code is incomplete, what is needed is code to validate that
            # all the items in 'collect_leaf' are consumed by some collection
            # of commands.  field_group has as its index a comma-separated-list
            # of field's associated with the list of command which generate those
            # fields.  we would like the minimum number of commads which collectivly
            # configure all the 'collect_leaf' values.

            # determine if ahy field_groups's indicies are contained in other ones.

            def contained(bigger, smaller):
                if type(bigger) != tuple or type(smaller) != tuple:
                    print bigger, smaller
                    print 'contained: parameters not tuples' # raise exception?
                for t in smaller:
                    if not t in bigger:
                        return False
                return True

            for (n,v) in field_group.items():
                for other in [x for x in field_group.keys() if x != n]:
                    if contained(n, other):
                        del field_group[other]

            config_commands = []
            # select the shortest length command for each of the associated fields
            for (field_group_index, field_group_value) in field_group.items():
                shortest = field_group_value[0]
                for field_command in field_group_value[1:]:
                    if len(field_command[0].strip()) < len(shortest[0].strip()):
                        shortest = field_command
                config_commands.append(shortest)

            if debug.description():
                print 'config: final', submode_commands, config_commands
            self.log( 'ARG', config_commands, submode_commands, results)

            # Ensure each of the commands has the correct submode associated.
            # if its correct, collect these together into a single score.put().
            #
            # The only values preserved from the collection of tuple parameters
            # is the rc-order (the priority of the commands in the final output)
            typical_config_commands = []

            for (c_text, c_submode, c_self, c_rc_order) in config_commands:
                # As for the config commands, the submode commands also
                # keep the associated submode in the 2nd item, [1], in the tuple
                if c_submode == 'config' or c_submode == 'config*':
                    if submode_commands:
                        print 'PROBLEM', c_text, c_submode, submode_commands
                    self.score.put([], c_text)

                elif submode_commands and c_submode != submode_commands[-1][1]:
                    # submode commands need to be manually generated.
                    # new fields may need to be added to the result so that
                    # the various submode commands can be generated.
                    # by passing in submode_commands, the hope is that all
                    # relevant fields necessary can be populated.

                    # this needs to be improved. 
                    #
                    # the submode path between this element and some upper
                    # element needs to be determine.  This presumes some
                    # mis-step took place between here and an ancestor.
                    # It would be better if the complete collection of commands
                    # could be respun
                    submode_commands = list(submode_commands)
                    for ndx in range(len(submode_commands)):
                        lsc = submode_commands[-ndx-1] # last sc
                        if lsc[1] == None:
                            continue
                        if c_submode.startswith(lsc[1]):
                            break
                        submode_commands[-ndx-1] = (None, lsc[1], lsc[2], lsc[3], lsc[4])

                    specific_submode_commands  = submode_enter_command(path,
                                                                       result,
                                                                       submode_commands,
                                                                       c_submode)
                    self.score.put(specific_submode_commands, (c_text, c_rc_order))
                else:
                    typical_config_commands.append((c_text, c_rc_order))

            self.score.put(submode_commands, typical_config_commands)
            return config_commands + [c[0] for c in results]


        def submode_enter_command(path,
                                  results,
                                  previous_submode_commands,
                                  target_mode = None):
            #
            # responsible for generating a single submode command base
            # on the path and the results.   The objective is to add this
            # single command to the list of previous_submode_commands, and
            # return this list.
            #
            # if the mode is known, then target_mode is passed in.
            # the call sites setting target_mode typically have an
            # idea of what submode they want, and want the associated
            # command to manage it.
            #
            # the list of commands is not a simple list of text strings,
            # each item in the list is a tuple composed of the text,
            # the resulting submode (if it were executed), the command's
            # name (command['self']), field's name:values,  and
            # the run-config priority of the command (rc-order).
            #
            # In some situations the command text will be None, this indicates
            # some sort of ambiguous choice.   The goal is to continue
            # descending, but retains all the field name:values for this path
            # so that later, when its clearer what submode is wanted for
            # some nested config command, the field name:values for that submode
            # will be avalable from the 'previous_submode_commands' stack.
            #

            def collect_path_fields():
                # Try to identify any submod 
                # check to see if this path-key field names appers anywhere.
                keys = self.bigdb.search_keys.get(path)
                if keys == None or len(keys) != 1:
                    return previous_submode_commands
                key_field_path = path_adder(path, keys[0])
                fields = {}
                for (sc, sc_fields) in self.command_fields.items():
                    # would like to exclude non config-submode type commands
                    if key_field_path in sc_fields:
                        fields[key_field_path] = results.get(keys[0])
                return fields

            if not path in self.submode:
                if not path in self.submode_without_object_path:
                    # if there are no choices, and there's no target mode,
                    #
                    if target_mode == None:
                        fields = collect_path_fields()
                        if fields:
                            new_commands = [(
                                                None,                      # ambiguous
                                                None,                      # ambiguous
                                                None,                      # ambiguous
                                                fields,                    # associated data
                                                None,                      # (no rc order)
                                           )]
                            return previous_submode_commands + new_commands
                    return previous_submode_commands
                else:
                    submode_commands = self.submode_without_object_path[path]
            else:
                submode_commands = self.submode[path]

            #
            # use the target_mode to limit available choices.
            if target_mode:
                target_commands = [x for x in submode_commands
                                   if self.command_ref[x]['submode-name'] ==
                                      target_mode]
                if len(target_commands) == 0:
                    # this mey mean the path and the target_mode are
                    # inconsistent to each other.   Since the target mode
                    # is known, use the submode command which traverses to
                    # that specific submode.
                    #
                    # currently target_mode_command is a map to a single
                    # command.
                    target_commands = self.target_mode_command.get(mode)
                    if target_commands == None:
                        print 'submode_enter_command: no mode %s within choices %s' % \
                                (target_mode, submode_commands)
                    else:
                        submode_commands = [target_commands]
                else:
                    submode_commands = target_commands

            #
            # execute the request for submode commands in order of the list.
            # save the collection of results into a list

            # the "previsou" commands may be updatedd when the
            # current collection of commands doesn't represent the context for
            # the current command.

            new_commands = []

            for submode_command in submode_commands:
                submode_results = list(previous_submode_commands)

                command_desc = self.command_ref[submode_command]
                rc_order = command_desc.get('rc-order')

                # command can provide some clues to help generate the running-config,
                # here we look for a backwards dictionary to map command fields to
                # result fields.
                desc_running_config = command_desc.get('running-config')
                if desc_running_config:
                    if 'data-map' in desc_running_config:
                        # print 'FOUND DATA-MAP in RC', desc_running_config
                        pass
                    # Use the command descriptions results mapping to deal
                    # with converting the results to something the command can consume
                    if 'id-to-fields' in desc_running_config:
                        (desc_running_config['id-to-fields'])(self, path, results)


                # 'item-name'  when item-name appears, then this means the submode
                # attaches a new name to the field once its in a submode.  This
                # can be then used to invert the value back for the command permutation

                item_name = command_desc.get('item-name')
                if item_name and len(self.command_fields[submode_command]) == 1:
                    command_field_name = self.command_fields[submode_command][0]
                    if (item_name != command_field_name) and item_name in results:
                        results = dict(results)
                        results[command_field_name] = results[item_name]

                # XXX running-config-map needs some help; item-name inversion
                # directly above should handle most of the cases.

                fields_map = command_desc.get('running-config-map')
                if fields_map:
                    # inverse_fields_map = dict([[v,n] for (n,v) in fields_map.items()])
                    results_map = dict([[fields_map.get(n, n), v]
                                         for (n, v) in results.items()])
                else:
                    results_map = results

                # compute the name:values for this particular submode command.
                fields = {}
                need_deeper_data = False

                for field in self.command_fields[submode_command]:
                    # look for this field in the submode stack.
                    for (s_c, s_submode, s_self, s_field, s_order) in previous_submode_commands:
                        if field in s_field:
                            fields[field] = s_field[field]
                            break
                        # submode commands use field names which exclude the
                        # key value as the last element.  if the field appears
                        # to be a path, see if there is a single search key
                        # and try to find that field.
                        if is_path(field): 
                            keys = self.bigdb.search_keys.get(field)
                            if keys and len(keys) == 1:
                                single_key = keys[0]
                                field_path = path_adder(field, single_key)
                                if field_path in s_field:
                                    fields[field] = s_field[field_path]
                                    break
                    else:
                        if field in results_map:
                            fields[field] = results_map[field]
                            continue
                        elif is_path(field): # is this a path, wrt last element?
                            # needs improvement:  convert a path to the tail value
                            path_with_slash = path + '/'
                            if field.startswith(path_with_slash):
                                tail = field.replace(path_with_slash, '')
                                if tail in results_map:
                                    fields[field] = results_map[tail]
                                    continue
                                elif is_path(tail):
                                    # in this situaion, interior field entries will
                                    # likely be needed for this command.  If this 
                                    # is the case, first collect all the field values
                                    # known for this depth, then update the submode_commands
                                    # with the 'ambiguous' style, and try descending.
                                    need_deeper_data = True

                if need_deeper_data:
                    # take the values which are currently known, and go a bit deeper
                    new_commands += [ (
                                        None,                      # ambiguous
                                        None,                      # ambiguous
                                        None,                      # ambiguous
                                        fields,                    # associated data
                                        None,                      # (no rc order)
                                        previous_submode_commands, # previous commands 
                                   ) ]
                    continue

                permute = command.CommandPermutor(field_values = dict(fields))
                permute.permute_command(command_desc, un_config = False)
                choices = permute.collect
                if len(choices) > 1:
                    print 'SUBMODE COMMAND SELECTION TROUBLE', choices
                if len(choices) == 0:
                    if debug.description():
                        print 'NO GENERATED COMMANDS', submode_command, fields, self.command_fields[submode_command], results_map, path
                    continue

                # pick one of the choices.
                #  complain when more than choice exists?
                #  pick the shortest?  associate with the related fields?
                choice = choices[0]

                # Verify the current submode appears within the collection
                # of submodes associated with previous_submode_commands.

                submode_mode = command_desc['mode']

                # verify the submode command is in an expected context.
                # config and config* is equivalent.
                if submode_mode != 'config*' and submode_mode != 'config':
                    # tuples in the previous_submode_commands contains
                    #  ('command', 'submode-name', command['self'], fields)
                    # to collect all the modes, then use x[1].
                    all_nested_modes = [x[1] for x in previous_submode_commands
                                        if type(x) == tuple and x[0] != None]
                    if submode_mode[-1] == '*':
                        submode_mode = submode_mode[:-1]
                    if (not submode_mode in all_nested_modes) or \
                      (len(all_nested_modes) and submode_mode != all_nested_modes[-1]):

                        # the current submode commands list is incomplete.
                        # try a recursive call to collect the previous modes.
                        path = self.mode_path.get(submode_mode)
                        if path:
                            prepend = submode_enter_command(path, results,
                                                            previous_submode_commands,
                                                            submode_mode)
                            if not prepend:
                                if debug.description():
                                    print 'submode_enter_command: missing command ', \
                                           submode_command, submode_mode, all_nested_modes
                                continue

                            # the prepended value is the collection of all commands
                            # needed to get to this submode.  the submode commands
                            # for this choice is replace wiehter or not it emptys
                            submode_results = prepend
                            if submode_results and submode_results[-1][1] != submode_mode:
                                print 'NOT NESTED CORRECTLY', submode_command, submode_mode, all_nested_modes

                        else:
                            # can't generate this variation.
                            if debug.description():
                                print 'submode_enter_command: missing config ', \
                                        submode_command, submode_mode, all_nested_modes
                            continue
                        # if self.submode_without_object.get(submode_mode) contains this
                        # submode, there's more work to determine the path

                    
                # For the generated submode commands, build a tuple, where
                # the submode command is associated with the commands mode (submode),
                # and the command's 'self' (dictionary name)

                # new copy needed here, can't just add the new command to the existing list
                new_commands += [
                                  (
                                   choice,                        # 0 command text
                                   command_desc['submode-name'],  # 1 submode
                                   submode_command,               # 2 ommand name
                                   fields,                        # 3 ssociated fields
                                   rc_order,                      # 4 order (priority)
                                   submode_results                # 5 temporary
                                  )
                                ]
                #
                # the default value of 'create' for submodes is True,
                # so if its missing or if its True, add the submode command to
                # the scoreboard
                submode_create = command_desc.get('create')
                if submode_create == None or submode_create == True:
                    create_item = [ (
                                   choice,                        # 0 command text
                                   command_desc['submode-name'],  # 1 submode
                                   submode_command,               # 2ommand name
                                   fields,                        # 3 ssociated fields
                                   rc_order,                      # 4 order (priority)
                                  ) ]
                    self.score.put(submode_results + create_item, None)

            # path's may lead to multiple choices for submode commands,
            # however based on the values of the results, only one may
            # get constructed.
            #
            # bug if there are multiple commands, then a further descnt
            # will be necessary to disambiguate the specific choice.
            # if nested command variations for the interior results are
            # constructed, the command will be able to announce the mode
            # which it is associated with.  That mode can then select
            # the specific submode command which is current ambiguous.
            #
            # however when the interior command selects the upper submode
            # command, the parameters for the command must be available.
            # These field's value are collected by using the field's
            # association computed with the specific submode command,
            # They're attached to a Null text command, which is ignored
            # by the scoreboard.  

            if len(new_commands) > 1:
                # If there are multiple choices, and a target mode was requested,
                # then the ambigous command's then the current strategy of managing
                # this ambiguety won't work.  Record the even here to provide 
                # details for further analysis.
                #
                if target_mode:
                    print 'submode_enter_command: multiple submode commandds:'
                if debug.description():
                    print 'submode_enter_command: multi mode commands', path, collect_target_modes, submode_commands
                # collect together all the fields used to build
                # these command variations, and add them as a null
                # command, used later to populate the specific selection
                # based on specific configuration commands.
                new_commands_fields = {}
                for (x_text, x_mode, x_desc, fields, x_sc, x_order) in new_commands:
                    new_commands_fields.update(fields)
                new_details = [ (
                                    None,                   # ambiguous
                                    None,                   # ambiguous
                                    None,                   # ambiguous
                                    new_commands_fields,    # associated data
                                    None,                   # (no rc order)
                               ) ]
                return previous_submode_commands + new_details

            if len(new_commands) == 0:
                return previous_submode_commands

            # the <4 temporaray> is used to save the specific submode commands
            # assocaited with the selected command.  [5] isn't intended to
            # external consumption, it hold the upper submode commands.
            sc = new_commands[0] # only a single submode command selected

            # sc[4] is the associated commands parent commands, while
            # the <0,1,2,3> are the details for this new submode command.
            return sc[5] + [ ( sc[0], sc[1], sc[2], sc[3], sc[4] ) ]


        def descend(path, schema, results, submode_commands = None):
            if submode_commands == None:
                submode_commands = []

            if not self.bigdb.path_configurable[path]:
                self.log( 'descend: path not configurable', path)
                return

            schema_type = schema.get('nodeType')
            self.log( 'NODETYPE', path, schema_type)
            config_result = []
            if schema_type == 'LIST':
                self.log( 'PATH:LIST', path)
                list_elements = schema.get('listElementSchemaNode')
                keys = list_elements.get('keyNodeNames')
                children = list_elements.get('childNodes')
                if not results:
                    self.log( 'NO WORK FOR', path)
                for result in results:
                    self.log( 'RESULT', result)
                    result_key = None
                    if keys:
                        result_key = ' '.join([str(result[x]) for x in keys])
                        self.log( 'DO', result_key)
                    submodes = submode_enter_command(path, result, submode_commands)
                    single_result = config(path, children, result, submodes)
                    if single_result or self.bigdb.path_config_only.get(path, False):
                        config_result += submodes + single_result
                        config_result += submodes + single_result
            elif schema_type == 'CONTAINER':
                self.log( 'PATH:CONTAINER', path, results)
                children = schema.get('childNodes')
                if type(results) == list and len(results) == 1:
                    results = results[0]
                submode = submode_enter_command(path, results, submode_commands)
                single_result = config(path, children, results, submode)
                if single_result:
                    self.log( 'SO FAR', single_result)
            else:
                print 'bigdb_run_config: generate: unknown type', schema_type

            if len(config_result) == 0:
                return []

            self.log( 'FINAL', submode_commands, config_result)


        if tops == None:
            tops = self.top_paths

        if type(tops) != list:
            tops = [tops]

        run_config = []
        for path in tops:
            filter = {}
            if type(path) == tuple:
                (path, filter) = path
            if debug.description():
                print 'QUERY TOP PATH', path
            try:
                (schema, result) = self.bigdb.schema_and_result(path,
                                                                filter,
                                                                'config=true')
            except urllib2.HTTPError, e:
                if debug.description():
                    print 'REST API REQUEST ERROR', path, e.code
                scoreboard.add_error(e.code)
                continue
                    
            except Exception, e:
                print 'running-config: query failed', path, filter, e
                if debug.description():
                    traceback.print_exc()
                continue

            if schema == None:
                if debug.description():
                    print 'NO SCHEMA FOR TOP PATH', path
                continue
            if result.get() == None:
                if debug.description():
                    print 'NO RESULT FOR TOP PATH', path
                continue

            final_result = result.builder()
            if debug.description():
                 print 'RESULT TOP PATH', path, result.depth, final_result
            self.log( 'TOP PATH', path, final_result)

            descend(path, schema, final_result)

        if debug.description():
            print 'BIDDB-AUTO-RUNNING-CONFIG:'


    def commands_for_path(self, path, data):
        """
        Return generated commands where the schema is passed in, and the data for
        the commands is known.

        Used to generate the shortest text for some "row" of configuration data
        for various show commands, for example 'show bigtap policy XXX', where
        the match rules are more desirable than the actual fields from bidb.
        """

        candidates = []
        for command_self in self.path_commands.get(path):
            candidates.append(self.command_ref[command_self])

        choices = []
        for candidate in candidates:
            permute = command.CommandPermutor(field_values = data)
            # permute_command returns the newline joined list of generated commands
            permute.permute_command(candidate, un_config = False)
            choices += permute.collect

        return choices
