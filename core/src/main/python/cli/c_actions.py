#
# Copyright (c) 2011,2012 Big Switch Networks, Inc.
# All rights reserved.
#

import re
import numbers
import collections
import traceback
import types
import json
import time
import sys
import datetime
import os
import select
import subprocess
import socket
import urllib2 # exception, dump_log()
import getpass

import utif
import debug
import error
import command
import run_config
import url_cache
import bigdb

#
# ACTION PROCS
#

def check_rest_result(result, message=None):
    if isinstance(result, collections.Mapping):
        error_type = result.get('error_type')
        if error_type:
            raise error.CommandRestError(result, message)

def write_fields(data, obj_id, path = None, desc_path = None):

    """
    Typical action to update fields of a row in the model

    @param data a dict, the name:value pairs of data to update in the table
    @param path a string, a bigdb path to the schema
    @param desc_path a bigdb schema path, from the schema
    """
    if debug.description():   # description debugging
        print "write_fields:", path, desc_path, data

    if not (path or desc_path):
        raise error.CommandSemannticError("Missing path and desc_path")

    # allow the command to overwrite the submode stack path
    if desc_path:
        path = desc_path
    bigdb = bigsh.bigdb
    data = dict(data) # protect caller against changes to data

    bigdb.validate_fields_of_path(path, data)
    bigdb.canonicalize_values_of_path(path, data)

    # if the node type under configuration is a LIST
    # (or LEAF_LIST), this likely wants to add a new
    # item to the list.
    (schema, items_matched) = bigdb.schema_of_path(path, {} )
    if schema == None:
        raise error.CommandDescriptionError("Missing scheam for: %s" % path)
        return
    node_type = schema['nodeType']
    if debug.description():   # description debugging
        print 'write_fields: node_type', node_type
    if node_type == 'LIST':
        list_nodes = schema['listElementSchemaNode']
        selection = {}
        bigdb.add_mode_stack_paths(selection)
        if debug.description():   # description debugging
            print 'write_fields: mode_stack_paths:', selection, data
        for key in list_nodes.get('keyNodeNames', []):
            if key in data:
                selection[key] = data[key]
                data = dict(data) # possibly do only once
                del data[key]
            else:
                if obj_id != None:
                    if type(obj_id) == dict:
                        for (n,v) in obj_id.items():
                            if n not in selection:
                                print 'write_fields: consider', n, v
                    else:
                        # 'path' may be added by add_mode_stack_paths()
                        if path not in selection:
                            selection[key] = obj_id

        # Since the goal is to modify fields in an object,
        # the prefered operation is patch, but if the object
        # doesn't exist, the patch query xpath doesn't match,
        # and the associated data doesn't get updated.
        #
        # If the obect doesn't exist, the method of creation is
        # quite different: a put for the prefix list path with the
        # key in the data body.
        if not bigdb.exists(path, selection):
            if debug.description():   # description debugging
                print 'write_field: create DATA', data, 'SELECTION', selection
            selection.update(data)
            bigdb.put(path, selection, oper = 'create')
            return

        # if it doest exist, patch to gently update
        (x_path, si, data, depth) =  bigdb.rest_xpath_url(path,
                                                          data,
                                                          'create')

        bigdb.canonicalize_values(path, data, list_nodes['childNodes'])
        if debug.description():   # description debugging
            print 'write_field: patch DATA', data, 'SELECTION', selection
        bigdb.patch(path, data, selection)
        return
    elif node_type == 'LEAF_LIST':
        # possibly consider just calling config-object after complaining
        print 'write_fields: config-object? LEAF_LIST', data, path
        return
    elif node_type == 'CONTAINER':
        selection = {}
        bigdb.add_mode_stack_paths(selection)
        # update the data based on matches in the path, values also ought to 
        # be canonicalized.
        (x_path, si, data, depth) =  bigdb.rest_xpath_url(path,
                                                          data,
                                                          'create')
        bigdb.canonicalize_values(path, data, schema['childNodes'])

        # if a put is issued here, then the data for the item gets replaced,
        # so if there's additional data nearby, it gets crushed BSC-3293
        bigdb.patch(path, data, selection)
        return
    elif node_type != 'LEAF':
        raise error.CommandDescriptionError("Unknown node-type %s scheam for: %s" %
                                            (node_type, path))

    bigdb.add_mode_stack_paths(data)
    bigdb.patch(path, data)

    return


def reset_fields(arg_data, path = None, desc_path = None,
                 fields = None, match_for_no = None):
    """
    Revert fields back to their default value.
    This is the typical action for 'no' commands.

    When verify is set, this is a string or list of fields who's values
    must match in the table for the primary key associated with the reset.
    This allows command descriptions to identify any fields which need to
    be checked against, when they are explicidly named in the 'no' command,
    so that 'no XXX value' will verify that 'value' matches the current 
    row's value before allowing the reset to continue

    @param arg_data a dict, collection of name:value pairs from the description
    @param path schema path
    @param desc_path schema path from the command description
    @param fields a list, collection of fields to update in the table
    @param match_for_no a string or list, list of fields to check for matched values in arg_data
    """

    if path == None and desc_path != None:
        path = desc_path
    elif path and desc_path and desc_path.startswith(path):
        # support (for example BIG_TAP_RBAC_PERMISSION_ALLOW_ALL_COMMAND_DESCRIPTION)
        # descriptions for config is a more specific version of the submode path
        path = desc_path

    if path == None:
        raise error.CommandDescriptionError("No object or path to reset (missing obj-type)")

    bigdb = bigsh.bigdb
    data = dict(arg_data) # don't modify original

    if debug.description():   # description debugging
        print 'reset_fields: bigdb', path, obj_id, fields, match_for_no, arg_data

    # if the node type under configuration is a LIST
    # (or LEAF_LIST), this likely wants to add a new
    # item to the list.
    (schema, items_matched) = bigsh.bigdb.schema_of_path(path, {} )
    if schema == None:
        raise error.CommandDescriptionError("Missing scheam for: %s" % path)
        return
    node_type = schema['nodeType']
    if debug.description():   # description debugging
        print 'reset_fields: type', node_type
    filter = {}
    if node_type == 'LIST':
        list_nodes = schema['listElementSchemaNode']
        selection = {}
        for key in list_nodes.get('keyNodeNames', []):
            if key in data:
                filter[key] = data[key]
                del data[key]
            else:
                if obj_id == None:
                    raise error.CommandDescriptionError("No obj_id or data for: %s"
                                                        % key)
                else:
                    if type(obj_id) == dict:
                        pass
                        # bigdb.add_mode_stack_paths() will add
                        # the obj_id types to filter
                    else:
                        filter[key] = obj_id
    # determine the base path, and selection items.
    bigdb.add_mode_stack_paths(filter)
    (x_path, x_si, x_data, x_depth) = bigdb.rest_xpath_url(path,
                                                           filter,
                                                           'delete')
    if debug.description():   # description debugging
        print 'reset_fields:', x_path, x_data, x_depth

    if fields == None:
        fields = []
    elif type(fields) == str or type(fields) == unicode:
        fields = [fields]

    if debug.description():   # description debugging
        print 'reset_fields: ', data, fields
    # add items to fields from data if they're in the schema at path...
    # these item names may include '/'
    for (n,v) in data.items():
        n_path = '%s/%s' % (path, n)
        (schema, items_matched) = bigsh.bigdb.schema_of_path(n_path, {})
        if schema and not n in fields:
            fields.append(n)

    if debug.description():   # description debugging
        print 'reset_fields: fields', fields

    # need bidb support for match-for-no.

    # need more here ... 
    if len(fields) == 0:
        bigdb.delete(path, {}, filter)
    else:
        for field in fields:
            # shouldn't direct concatenate these fields.
            if debug.description():   # description debugging
                print 'reset_fields: %s/%s' % (path, field)
            bigdb.delete('%s/%s' % (path, field), {}, filter)


def delete_objects(data,
                   path = None,
                   submode_adjustor = None):
    """
    Delete the indicated path of the schema

    @param data a dictionary, name:value pairs to describe the delete
    @param path the bigdb schema path
    """
    if debug.description():   # description debugging
        print 'delete_objects', data, path

    if not path:
        raise error.CommandDescriptionError("Need path to delete an object")

    data = dict(data)
    bigdb = bigsh.bigdb
    bigdb.canonicalize_values_of_path(path, data)

    # if the node type under configuration is a LIST
    # (or LEAF_LIST), this likely wants to add a new
    # item to the list.
    (schema, items_matched) = bigdb.schema_of_path(path, {} )
    if schema == None:
        print 'Missing Schema for', path
        return
    node_type = schema['nodeType']
    if debug.description():   # description debugging
        print 'delete_objects:', path, node_type

    if node_type == 'LIST':
        list_nodes = schema['listElementSchemaNode']
        selection = {}
        for key in list_nodes.get('keyNodeNames', []):
            if key in data:
                full_path = '%s/%s' % (path, key)
                selection[full_path] = data[key]
                del data[key]
        # populate for fields which are key's
        for key in list_nodes.get('keyNodeNames', []):
            if not key in selection:
                for row in command.bigsh.mode_stack:
                    if 'name' in row and row['name'] == key:
                        if 'obj' in row:
                            selection[key] = row['obj']
        bigdb.add_mode_stack_paths(selection)
        if submode_adjustor:
            command.submode_adjustor_invoke(submode_adjustor,
                                            path,
                                            selection,
                                            data,
                                            'delete')

        oper = bigdb.canonicalize_values_for_delete(path,
                                                    data,
                                                    selection,
                                                    list_nodes['childNodes'])
        if oper == 'POST':
            bigdb.post(path, data, selection)
        else:
            # bigdb.delete(path, data, selection) perhaps date <- {}
            bigdb.delete(path, data, selection)
        return
    if node_type == 'LEAF_LIST':
        if debug.description():   # description debugging
            print 'delete_object: leaf-list needs implementation:LEAF_LISTN'
        selection = {}
        bigdb.add_mode_stack_paths(selection)
        leaf_node = schema['leafSchemaNode']
        type_node = leaf_node['typeSchemaNode']
        split_path = path.split('/')
        item_name = split_path[-1]
        item = None
        if item_name in data:
            item = data[item_name]
        elif type_node['name'] in data:
            item = data[type_node['name']]
            del data[type_node['name']]
        if debug.description():   # description debugging
            print 'DATUM', data, 'SELECTUM', selection, 'ITEM', item
        # Currently, 'add/delete' for specific elements isn't
        # directly support in the BigDB REST API's. 
        split_path = path.split('/')
        base_path = '/'.join(split_path[:-1])
        (schema, result) = bigdb.schema_and_result(base_path, selection)
        collection = result.expect_single_result(failed_result = [])
        item_name = split_path[-1]
        if item_name in collection:
            collection = collection[item_name]
            if debug.description():   # description debugging
                print 'COLLECTION', collection, ' REMOVE ', item
            if item in collection:
                collection = [x for x in collection if x != item]
                bigdb.put(path, collection, selection, 'query')
                return
        raise error.CommandSemanticError('%s "%s" '
                                         'not currently configured' %
                                         (item_name, item))
        return
    if node_type == 'CONTAINER':
        container_nodes = schema.get('childNodes')

        selection = {}
        bigdb.add_mode_stack_paths(selection)

        for (n,v) in data.items():
            oper = bigdb.canonicalize_values_for_delete(path,
                                                        data,
                                                        selection,
                                                        container_nodes)
            if oper == 'PATCH':
                bigdb.patch(path, data, selection)
            else:
                item_path = '%s/%s' % (path, n)
                bigdb.delete(item_path, {}, selection)
        return

    bigsh.bigdb.add_mode_stack_paths(data)
    bigsh.bigdb.delete(path, data)


def set_data(data, key, value):
    """
    Action to associate a new name:value pair with 'data', the dictionary used 
    to pass to REST API's.   Allows the action to describe a value for a field
    which wasn't directly named in the description.

    """
    if debug.description():   # description debugging
        print "set_data:", data, key, value
    data[key] = value


def write_object(data, path = None, scoped = None):
    """
    Add something to the configured portion of the schema

    """
    # If we're pushing a config submode with an object, then we need to extend
    # the argument data that was entered explicitly in the command with the
    # information about the parent object (by default obtained by looking
    # at the obj info on  the mode stack -- see default arguments for
    # this action when it is added).

    if debug.description():   # description debugging
        print 'write_object: params ', path, data
    data = dict(data) # data is overwriten in various situations below

    if not path: # try to collect this from the top of the mode stack
        # change this to use the new procedures.
        top = bigsh.mode_stack_top()
        if top and top.get('path', '') != '':
            path = top['path']
            if debug.description():   # description debugging
                print 'write_object: path %s from mode_stack' % path

    bigdb = bigsh.bigdb
    if debug.description():
        print 'write_object', path, data

    bigdb.validate_fields_of_path(path, data)
    bigdb.canonicalize_values_of_path(path, data)

    # if the node type under configuration is a LIST
    # (or LEAF_LIST), this likely wants to add a new
    # item to the list.
    (schema, items_matched) = bigdb.schema_of_path(path, {} )
    if schema == None:
        print 'Missing Schema for', path
        return
    node_type = schema['nodeType']
    if debug.description():
        print 'write_object: node_type', node_type
    if node_type == 'LIST':
        selection = {}
        bigdb.add_mode_stack_paths(selection)
        list_nodes = schema['listElementSchemaNode']
        #for key in list_nodes.get('keyNodeNames', []):
            #if key in data:
                #selection[key] = data[key]
                #del data[key]
        # populate fields which are key's
        keys = list_nodes.get('keyNodeNames')
        selection_data = dict(data) # saved to build the path selection
        bigdb.canonicalize_values(path, data, list_nodes['childNodes'])
        orig_data = data # saved to re-pack for data values
        if keys:
            keys = list(keys) # make a copy, allow 'key' deletions
            for key in keys:
                full_path = '%s/%s' % (path, key)
                if full_path in selection:
                    keys = [x for x in keys if x != key]
                if not key in selection and key in data:
                    selection[full_path] = data[key]
                    keys = [x for x in keys if x != key]
                    # notice the same key is not removed from data
                # full_path has the key as the suffix
                if not key in selection and full_path in selection_data:
                    selection[full_path] = selection_data[full_path]
                    keys = [x for x in keys if x != key]
        
            if keys:
                # if all the keys are in the data, then try
                # adding the mode stack items into the final
                # element of the path.
                selection = {}
                mode_stack_keys = {}
                bigdb.add_mode_stack_keys(mode_stack_keys)
                if debug.description():
                    print 'write_object: keys and keys left', keys, mode_stack_keys

                for key in keys:
                    if not key in mode_stack_keys:
                        if debug.description():
                            print 'write_object: missing key', key
                        break
                    full_path = '%s/%s' % (path, key)
                    selection[full_path] = mode_stack_keys[key]
                    # possibly only add the key if its missing?
                    data[key] = mode_stack_keys[key]

            # if we issue a put with no data, but a selection,
            # the new item will not not be created.  In this case,
            # move the data item out of the seleciton by using
            # a post to add this new item to the collection.
            # ex: filter-interface h1
            if len(data) == 0 and len(orig_data) != 0:
                data = orig_data
                for (n,v) in data.items():
                    full_path = '%s/%s' % (path, n)
                    if full_path in selection:
                        del selection[full_path]

        bigdb.put(path, data, selection, 'query') # replace
        return
    if node_type == 'LEAF_LIST':
        selection = {}
        bigdb.add_mode_stack_paths(selection)
        leaf_node = schema['leafSchemaNode']
        type_node = leaf_node['typeSchemaNode']
        split_path = path.split('/')
        item_name = split_path[-1]
        if item_name in data:
            item = data[item_name]
        # Currently, 'add/delete' for specific elements of leaf-lists
        # isn't directly support in the BigDB REST API's, instead collect
        # the tree underneath, extract the item, and add it if its
        # not currently configured.
        base_path = '/'.join(split_path[:-1])
        (schema, result) = bigdb.schema_and_result(base_path, selection)
        collection = result.expect_single_result([])
        collection = collection.get(item_name, [])

        if not item in collection:
            bigdb.patch(path, collection + [item], selection)
        return

    if scoped:
        pass # should scoped be set for write object by default?
    bigsh.bigdb.add_mode_stack_paths(data)

    if bigsh.bigdb.exists(path, data, max_depth = 0):
        bigsh.bigdb.patch(path, data)
    else:
        bigsh.bigdb.put(path, data)


def push_mode_stack(mode_name,
                    data,
                    path = None,
                    item_name = None,
                    parent_field = None,
                    show_this = None,
                    submode_adjustor = None,
                    create=True):
    """
    Push a submode on the config stack.
    """
    global bigsh, modi

    # Some few minor validations: enable only in login, config only in enable,
    # and additional config modes must also have the same prefix as the
    # current mode.
    current_mode = bigsh.run.finder.mode_stack.current_mode()

    if debug.description():   # description debugging
        print 'push_mode:', mode_name, path, data, item_name, show_this

    data_param = data

    # See if this is a nested submode, or whether some current modes
    # need to be popped.
    if (mode_name.startswith('config-') and 
      (not mode_name.startswith(current_mode) or (mode_name == current_mode))):

        bigsh.run.finder.mode_stack.pop_mode()
        current_mode = bigsh.run.finder.mode_stack.current_mode()
        # pop until the parent mode is exposed
        while not mode_name.startswith(current_mode) or \
                mode_name == current_mode:
            if len(bigsh.mode_stack) == 0:
                raise error.CommandSemanticError('%s not valid within %s mode' %
                                           (mode_name, current_mode))
            bigsh.run.finder.mode_stack.pop_mode()
            current_mode = bigsh.run.finder.mode_stack.current_mode()

        # if there's a parent id, it is typically the parent, and audit
        # ought to be done to verify this
        if parent_field:
            data = dict(data)
            data[parent_field] = bigsh.run.finder.mode_stack.get_current_mode_obj()

    elif mode_name in ['config', 'enable', 'login']:
        # see if the mode is in the stack
        if mode_name in [x['mode_name'] for x in bigsh.mode_stack]:
            if debug.description():   # description debugging
                print 'push_mode: popping stack for', mode_name
            current_mode = bigsh.run.finder.mode_stack.current_mode()
            while current_mode != mode_name:
                bigsh.run.finder.mode_stack.pop_mode()
                current_mode = bigsh.run.finder.mode_stack.current_mode()
            return


    # If we're pushing a config submode with an object, then we need to extend the
    # argument data that was entered explicitly in the command with the information
    # about the parent object (by default obtained by looking at the obj info on
    # the mode stack -- see default arguments for this action when it is added).
    elif parent_field:
        if not parent_id:
            raise error.CommandDescriptionError('Invalid command description; '
                                          'improperly configured parent info for push-mode-stack')
        data = dict(data)
        data[parent_field] = parent_id
        
    pk_name = '<none>'
    key = None

    bigdb = bigsh.bigdb

    data_bigdb = dict(data_param)

    def can_compute_xpath():
        (xpath, si, fw_data, depth) = bigdb.rest_xpath_url(path,
                                                           data_bigdb,
                                                           'create')
        if not pk_name[0] in fw_data:
            return None

        key = dict(data_bigdb)
        return key

    if path == None:
        if debug.cli():
            print 'push_mode: no path, no schema validation'
    elif path in bigdb.search_keys:
        pk_name = bigdb.search_keys[path]
        if len(pk_name) == 1:
            if pk_name[0] in data:
                key = data[pk_name[0]] # stinky_name
            else:
                key = can_compute_xpath()
                if key == None:
                    raise error.CommandDescriptionError(
                                    'Missing key(s) "%s" for submode path: %s' % 
                                    (', '.join(pk_name), path) )
        elif len(pk_name) > 1:
            # compound keys. populate values for the push.
            (x_path, x_si, x_data, x_depth) = bigdb.rest_xpath_url(path,
                                                                   data,
                                                                   'create')
            key = dict([[x, x_data[x]] for x in pk_name])
    else: # path may be a prefix for items.
        (schema, items_matched) = bigdb.schema_of_path(path, data_bigdb)
        if schema == None:
            raise error.CommandDescriptionError(
                            'No schema for submode path: %s' % path)

        node_type = schema['nodeType']
        if node_type == 'CONTAINER':
            key = None # no keys if the path points to a container.
        else:
            key = can_compute_xpath()
            if key == None:
                raise error.CommandDescriptionError(
                                    'Missing key for submode path: %s' % path)

    if path:
        if debug.description():
            print 'push_mode:', path, data_bigdb, pk_name, key, create

        # need a test here to tell if the path can be constructed.
        bigdb.validate_fields_of_path(path, data_bigdb)
        bigdb.canonicalize_values_of_path(path, data_bigdb)

        bigdb.add_mode_stack_paths(data_bigdb)

        # see if item exists, if so, don't purturb it.
        if debug.description():
            print 'push_mode:', path, data_bigdb
        if not bigdb.exists(path, data_bigdb, max_depth = 0):
            if create:
                # XXX: Look for items used in the path selection which don't
                # appear in the mode stack.  These need to be verified and
                # possibly created.
                if debug.description():
                    print 'push_mode: create', path, data_bigdb, data_param, path, key
                # 'submode-adjustor' in the command description identifies a
                # callout which can improve the data associated with this
                # submode.   In situations where the command requires some
                # additional parameterization, but the CLI may be able to
                # add that from the conext, or from some permissted object,
                # the adjustor can do that work (see: bigtap policy <p>)
                if submode_adjustor:
                    command.submode_adjustor_invoke(submode_adjustor,
                                                    path,
                                                    data_bigdb,
                                                    data,
                                                    'create')
                    key = data_bigdb

                # bigsh.bigdb.put(path, data_param, data_bigdb, oper = 'query'))
                (x_path, x_si, x_data, x_depth) = bigdb.rest_xpath_url(path,
                                                                      data_bigdb,
                                                                      'create')
                if x_depth == 0:
                    # note: x_si will be [] from oper = 'create'
                    # oper = 'query' here seems odd since the put() is
                    # intended to create the item, but for PUT the complete
                    # query path must be included so the data will apply
                    # to a single item.
                    bigdb.put(path, x_data, data_bigdb, oper = 'query')
                else:
                    # this message provides no details about what is broken.
                    # what to do about this?  maybe the command can help.
                    raise error.CommandSemanticError('missing key for create')
            else:
                if type(key) == dict:
                    # need an improved way to deliver the name.
                    key_values = ', '.join(['%s:%s' % (n.split('/')[-1], v)
                                            for (n,v) in key.items()])
                header = bigdb.column_header.get(path)
                if header == None:
                    keys = bigdb.search_keys.get(path)
                    if len(keys) == 1:
                        key_path = '%s/%s' % (path, keys[0])
                        header = bigdb.column_header.get(key_path)
                if header == None:
                    header = 'Object'
                raise error.CommandSemanticError('%s not found: %s' %
                                                 (header, key_values))
        else:
            if submode_adjustor:
                command.submode_adjustor_invoke(submode_adjustor,
                                                path,
                                                key,
                                                data,
                                                'associate')

    if debug.description():
        print 'push_mode: key', key
    bigsh.run.finder.mode_stack.push_mode(mode_name, path = path, obj = key,
                                          item_name = item_name, show = show_this)

    
def pop_mode_stack():
    global bigsh

    if debug.description():   # description debugging
        print "pop_mode: "
    bigsh.run.finder.mode_stack.pop_mode()

def confirm_request(prompt):
    global bigsh

    if bigsh.batch:
        return
    result = raw_input(prompt)
    if result.lower() == 'y' or result.lower() == 'yes':
        return
    raise error.NotConfirmed("Command Aborted based on user response ")


def command_version(data):
    """
    The version command will later manage changing the syntax to match
    the requested version.
    """
    new_version = data.get('version')
    if new_version == None:
        return

    version = new_version # save for error message
    new_version = bigsh.desc_version_to_path_elem(new_version)

    # skip version change is this is the current version.
    if bigsh.desc_version == new_version:
        return
    
    # temporary.
    if bigsh.desc_version == 'bigdb' and new_version == 'version200':
        return

    # see if the requested version exists
    if not bigsh.command_packages_exists(new_version):
        print 'No command description group for version %s' % version
        return

    # run 'env [envriron_vars] ... bigcli.py'
    command = ['env']
    command.append('BIGCLI_COMMAND_VERSION=%s' % version)
    command.append('BIGCLI_STARTING_MODE=config')
    # (other env variables persist)
    if os.path.exists('/opt/bigswitch/cli/bin/bigcli'):
        # controller VM
        command.append('/opt/bigswitch/cli/bin/bigcli')
    else:
        # developer setup
        base = os.path.dirname(__file__)
        command.append(os.path.join(base, 'bigcli.py'))

    # bigsh.options.init ?
    if bigsh.options.init:
        command.append('--init')

    # dump the command descriptions, and read a new set.
    # open a subshell with a new command version
    subprocess.call(command, cwd=os.environ.get("HOME"))

    return


def command_clearterm():
    """
    Print reset characters to the screen to clear the console
    """
    subprocess.call("reset")

def command_display_cli(data):
    """
    Display various cli details
    (this may need to be re-factored into some general "internal" state show
    """

    command.actino_invoke('show-init')
    bigdb = command.bigsh.bigdb
    bigdb_show = command.bigdb_show

    modes = bigsh.command_dict.keys() + bigsh.command_nested_dict.keys()

    entry = {
               'version' : ', '.join(command.command_syntax_version.keys()),
               'desc'    : ', '.join(sorted(command.command_added_modules.keys())),
               'modes'   : ', '.join(sorted(utif.unique_list_from_list(modes))),
            }
    basic = bigsh.pp.format_entry(entry, 'cli')

    table = 'display-cli'
    bigdb_show.tables[table].append(entry)

    bigdb_show.columns[table] = [
                                    'version',
                                    'desc',
                                    'modes',
                                ]
    bigdb_show.column_headers[table] = {}

    command.actino_invoke('show-print')

    return


def command_rest_post_data(path, data=None, verb='PUT', meta=None):
    """
    """
    if type(path) == list:
        # select the first which matches with the parameters
        for p in path:
            try:
                path = (p % data)
                break
            except:
                pass

    url = 'http://%s/rest/v1/%s' % (
                    data.get('controller', bigsh.controller),
                    path)
    result = bigsh.rest_post_request(url, data, verb)
    check_rest_result(result)
    return None


def command_cli_variables_set(variable, value, data):
    global bigsh

    if variable == 'debug':
        print '***** %s cli debug *****' % \
                ('Enabled' if value else 'Disabled')
        debug.cli_set(value)
    elif variable == 'cli-batch':
        print '***** %s cli batch mode *****' % \
                ('Enabled' if value else 'Disabled')
        bigsh.batch = value
    elif variable == 'description':
        print '***** %s command description mode *****' % \
                ('Enabled' if value else 'Disabled')
        debug.description_set(value)
    elif variable == 'rest':
        if 'record' in data and value:
            print '***** Eanbled rest record mode %s *****' % \
                (data['record'])
            url_cache.record(data['record'])
            return
        print '***** %s display rest mode *****' % \
                ('Enabled' if value else 'Disabled')
        if 'detail' in data and data['detail'] == 'details':
            if value == True:
                debug.rest_set('details')
                bigsh.rest_api.display_reply_mode(value)
        debug.rest_set(value)
        bigsh.rest_api.display_mode(value)
        if value == False:
            bigsh.rest_api.display_reply_mode(value)
            url_cache.record(None)
    elif variable == 'set':
        if 'length' in data:
            bigsh.length = utif.try_int(data['length'])


def command_cli_set(variable, data):
    command_cli_variables_set(variable, True, data)

def command_cli_unset(variable, data):
    command_cli_variables_set(variable, False, data)


def command_shell_command(script):

    def shell(args):
        subprocess.call(["env", "SHELL=/bin/bash", "/bin/bash"] + list(args),
                        cwd=os.environ.get("HOME"))
        print

    print "\n***** Warning: this is a debug command - use caution! *****"
    if script == 'bash':
        print '***** Type "exit" or Ctrl-D to return to the CLI *****\n'
        shell(["-l", "-i"])
    elif script == 'python':
        print '***** Type "exit()" or Ctrl-D to return to the CLI *****\n'
        shell(["-l", "-c", "python"])
    elif script == 'cassandra-cli':
        print '***** Type "exit" or Ctrl-D to return to the CLI *****\n'
        shell(["-l", "-c", "/opt/bigswitch/db/bin/cassandra-cli --host localhost"])
    elif script == 'netconfig':
        if not re.match("/dev/ttyS?[\d]+$", os.ttyname(0)):
            print '***** You seem to be connected via SSH or another remote protocol;'
            print '***** reconfiguring the network interface may disrupt the connection!'
        print '\n(Press Control-C now to leave the network configuration unchanged)\n'
        subprocess.call(["sudo",
                         "env",
                         "SHELL=/bin/bash",
                         "/opt/bigswitch/sys/bin/bscnetconfig",
                         "eth0"],
                         cwd=os.environ.get("HOME"))
    else:
        # XXX possibly run the script directly?
        print "Unknown debug choice %s" % script


def command_controller_decommission(data):
    """
    Decommission the controller using the REST API
    """
    id = data.get('id')
    confirm_request("Decommission controller '%s'?\n(yes to continue) " % id)

    while True:
        url = 'http://%s/rest/v1/system/ha/decommission' % (bigsh.controller)
        result = bigsh.rest_post_request(url, {"id": id}, 'PUT')
        status = json.loads(result)

        if (status['status'] == 'OK') and status['description'].endswith('is already decommissioned') == True:
            print 'Decommission finished'
            print
            break
        else:
            print 'Decommission in progress'
            
        time.sleep(10)


def command_slave_warning(current_role = None, master = None):
    if current_role == None:
        current_role = bigsh.current_role()

    if current_role != 'SLAVE':
        return

    if master == None:
        master = bigsh.find_master()

    if master:
        ip = master['master']
        master = bigsh.controller_name_for_ip(ip)
        if master == None:
            master = ''
    else:
        master = '(UNKNOWN)'
        ip = ''

    details = { 'master' : master,
                'ip' : ip,
                'node-name' : bigsh.controller_name_for_ip('127.0.0.1'),
              }

    slave_mode_text = \
      '\n======================== WARNING: SLAVE MODE CONTROLLER ========================\n' \
      'This controller node: %(node-name)s, is in slave mode.\n' \
      'This session should only be used for updating the cluster\n' \
      'and for troubleshooting.\n\n' \
      'Log in to the MASTER %(master)s %(ip)s to make configuration changes\n'\
      'and to access up-to-date operational data.\n' \
      '================================================================================\n'
    print slave_mode_text % details


def command_bigdb_show_init():
    command.bigdb_show = bigdb.BigDB_show(bigsh.bigdb)

def command_bigdb_show_compose(path, data, style,
                               scoped = None, label = None, detail = None):
    if debug.description():   # description debugging
        print "command_bigdb_show_compose: ", path, data, style, label, detail

    select_parts = path.split('?')
    select_path = None
    if len(select_parts) > 1:
        path = select_parts[0]
        select_path = select_parts[1]
        if not select_path.startswith('select='):
            raise error.CommandDescriptionError("select requires "
                                                "'select=': %s" % path)
            
        select_path = select_path.replace('select=', '')
        
    filter = dict(data)
    if scoped:
        if type(scoped) == int:
            bigsh.bigdb.add_mode_stack_paths(data, max_depth = scoped)
        else:
            bigsh.bigdb.add_mode_stack_paths(data)

    (schema, result) = bigsh.bigdb.schema_and_result(path, filter, select_path)
    if schema == None:
        raise error.CommandDescriptionError("No schema found for: %s" % path)

    if not hasattr(command, 'bigdb_show') or command.bigdb_show == None:
        command.bigdb_show = bigdb.BigDB_show(bigsh.bigdb)

    command.bigdb_show = command.bigdb_show
    command.bigdb_show.compose_show(path,
                                    schema,
                                    result.builder(),
                                    style,
                                    detail = data.get('detail', detail))


def command_bigdb_show_print(data,
                             style,
                             path = None,
                             select = None,
                             format = None,
                             detail = None,
                             force_title = None):

    detail = data.get('detail', detail)
    for item in command.bigdb_show.show_print(style, select, format, detail, force_title):
        yield item
    command.bigdb_show = None


def command_bigdb_show(path, data,
                       style = None,
                       select = None,
                       sort = None,
                       format = None,
                       detail = None,
                       scoped = None,
                       force_title = None):

    if debug.description():   # description debugging
        print "command_bigdb_show: ", path, data, style, select, format, detail, scoped

    if select and type(select) == str:
        select = [select]

    if scoped:
        bigsh.bigdb.add_mode_stack_paths(data)
        if debug.description():   # description debugging
            print "command_bigdb_show: scoped", data

    # let detail parameter override any value in data
    if detail == None:
        detail = data.get('detail', None)
    
    select_parts = path.split('?')
    select_path = None
    if len(select_parts) > 1:
        path = select_parts[0]
        select_path = select_parts[1]
        if not select_path.startswith('select='):
            raise error.CommandDescriptionError("select requires "
                                                "'select=': %s" % path)
            
        select_path = select_path.replace('select=', '')
        
    (schema, result) = bigsh.bigdb.schema_and_result(path, data, select_path)
    if schema == None:
        raise error.CommandDescriptionError("No schema found for: %s" % path)

    show = bigdb.BigDB_show(bigsh.bigdb)
    for item in show.show(path, schema, result.builder(), style, select, sort, format, detail, force_title):
        yield item


def command_bigdb_show_add_interfaces():
    for (table_name, table_value) in command.bigdb_show.tables.items():
        # look for dpid and port in the table.
        pass


def command_device_compute_host_id(data):
    # look for 'mac', 'vlan', 'entity_class_name', 'dpid', 'interface' in data
    if 'id' in data:
        return

    # use the oracle with the user specified fields to
    # compute the device id.
    (schema, result) = bigsh.bigdb.schema_and_result('core/device-oracle', data)
    if schema == None:
        raise error.CommandDescriptionError("No schema found for core/device-oracle")
        return
    final = result.expect_single_result()
    if final == None:
        raise error.CommandSemanticError("No host found")
    data['id'] = final['id']


def command_bigdb_config_transform(data):

    # old to new data.
    if not command.bigsh.bigdb.enabled():
        return
    
    if debug.description():   # description debugging
        print 'command_bigdb_config_transform:', data

    feature = data.get('feature')
    if feature == 'bvs':
        enabled = command.bigsh.bvs_feature_enabled()
    else:
        enabled = command.bigsh.feature_enabled(feature)

    if not enabled:
        transform_dict = {
                #           from                   to          [ list-of-paths ]
                'bvs'    : ('config-bigtap.json', 'config-bvs.json',
                             [ 'applications/bigtap',
                               'core/aaa/rbac-permission/bigtap' ]),
                'bigtap' : ('config-bvs.json',    'config-bigtap.json',
                            ['applications/bvs',
                             'tag-manager',
                             'core/device' ]),
            }

        base_directory = '/opt/bigswitch/bigdb/db/'
        transform = transform_dict[feature]
        from_file = base_directory + transform[0]
        to_file   = base_directory + transform[1]
        items     = transform[2] # list of paths' to delete.
        if type(items) == str:
            items = [items]

        if not os.path.exists(from_file):
            # try an alternative path
            if not os.path.exists('/tmp/' + transform[0]):
                # don't bother writing if the file doesn't exist.
                if debug.description():   # description debugging
                    print 'bigdb_config_transform: file doesn\'t exist:', from_file
                return
            print 'TEMPORARILY USING /tmp AS CONFIG FILE BASE'
            base_directory = '/tmp/'
            from_file = base_directory + transform[0]
            to_file   = base_directory + transform[1]

        # read from.
        config = command.bigsh.bigdb.read_pickled_config(from_file)

        # delete items
        # XXX: should be updated to do a recursive descent.
        # note: list elements can currently only be removed when they're
        #  the last element of the item-path
        for item in items:
            # find and purge the described item.
            curr = config
            path = item.split('/')

            for element in path[:-1]:
                if not element in curr:
                    break
                curr = curr.get(element)
            else:
                last_element = path[-1]
                if type(curr) == list:
                    del_items = []
                    for (ndx, curr_item) in enumerate(curr):
                        # assume a dict here
                        if last_element in curr_item:
                            del_items.append(ndx)
                    for ndx in sorted(del_items, reverse = True):
                        del curr[ndx]
                    
                elif last_element in curr: # assume a dict
                    del curr[last_element]
                    # found one, there may be more.

        # now write.
        command.bigsh.bigdb.write_pickled_config(to_file, config)


def command_dump_log(data):
    controller = data.get('controller-node') # can be None.
    controller_dict = { 'id' : controller } if controller else None
    for (c_id, ip_port) in controller_rest_api_ips(controller_dict).items():
        if controller_dict:
            # print separator for each controller
            yield '*' * 40 + ' Controller: ' + c_id + 'at' + ip_port + '\n'
            
        log_name = data['log-name']
        if log_name == 'all':
            url = log_url(ip_and_port = ip_port)
            log_names = command.bigsh.rest_simple_request_to_dict(url)
            for log in log_names:
                yield '*' * 40 + ip_port + ' ' + log['log'] + '\n'
                for item in command_dump_log({ 'log-name' : log['log'] }):
                    yield item
            return

        # use a streaming method so the complete log is not in memory
        url = log_url(ip_and_port = ip_port, log = log_name)
        request = bigsh.rest_api.rest_url_open(url)
        for line in request:
            yield line
        request.close()


def command_rbac_required(data, groups = None):
    if not command.bigsh.bigdb.enabled():
        # no bigdb means no rbac.
        return
    #
    bigdb = command.bigsh.bigdb
    bigdb.cache_session_details()

    if groups == None:
        groups = ['admin']
    if type(groups) == str:
        groups = [groups]
    
    for group in groups:
        if group in bigdb.cached_user_groups:
            break
    else:
        raise error.CommandUnAuthorized('%s not member of "%s" group(s)' %
                                        (bigdb.cached_user_name,
                                         ', '.join(groups)))


def command_update_flow_cookies():
    update_flow_cookie_hash()


def command_echo(message, batch_only = None):
    if not batch_only or not bigsh.batch:
        yield message + '\n'

def command_validate_switch():
    """
    -- verify that the announced interfaces names are case insensitive
    -- verify the names only appear once
    """

    def duplicate_port(entry, name):
        dpid = entry['dpid']

        print 'Warning: switch %s duplicate interface names: %s' % (dpid, name)
        if bigsh.debug_backtrace:
            for port in entry['ports']:
                if port['name'] == name:
                    print 'SWTICH %s:%s PORT %s' %  (entry, name, port)

    def not_case_sensitive(entry, name):
        dpid = entry['dpid']

        ports = {}
        for port in entry['ports']:
            if port['name'].lower() == name:
                ports[port['name']] = port

        print 'Warning: switch %s case insentive interface names: %s' % \
               (dpid, ' - '.join(ports.keys()))
        if bigsh.debug_backtrace:
            for port in ports:
                print 'SWTICH %s PORT %s' % (dpid, port)

    bigdb = bigsh.bigdb
    try:
        (schema, entries) = bigdb.schema_and_result('core/switch', {})
    except Exception, e:
        print 'command_validate_switch:', e
        traceback.print_exc()
        return

    if entries:
        for entry in entries.iter():
            dpid = entry['dpid']

            # verify that the port names are unique even when case
            # sensitive
            all_names = [p['name'] for p in entry['interface']]
            one_case_names = utif.unique_list_from_list([x.lower() for x in all_names])
            if len(all_names) != len(one_case_names):
                # Something is rotten, find out what.
                for (i, port_name) in enumerate(all_names):
                    # use enumerate to drive upper-triangle comparison
                    for other_name in all_names[i+1:]:
                        if port_name == other_name:
                            duplicate_port(entry, port_name)
                        elif port_name.lower() == other_name.lower():
                            not_case_sensitive(entry, port_name)


def command_running_config(data):
    return run_config.implement_show_running_config(bigsh, data)

#
# Initialize action functions 
#
#

def init_actions(bs):
    global bigsh
    bigsh = bs

    command.add_action('write-fields', write_fields,
                       {'kwargs': {'path'      : '$current-mode-path',
                                   'obj_id'    : '$current-mode-obj-id',
                                   'desc_path' : '$path',
                                   'data'      : '$data', }})

    command.add_action('write-fields-explicit', write_fields,
                       {'kwargs': {'path'     : '$path',
                                   'obj_id'   : '$obj-id',
                                   'data'     : '$data', }})

    command.add_action('reset-fields', reset_fields,
                       {'kwargs': {'path'         : '$current-mode-path',
                                   'desc_path'    : '$path',
                                   'arg_data'     : '$data',
                                   'match_for_no' : '$match-for-no',
                                   'fields'       : '$fields'}})

    command.add_action('reset-fields-explicit', reset_fields,
                       {'kwargs': {'path'         : '$path',
                                   'desc_path'    : '$path',
                                   'arg_data'     : '$data',
                                   'match_for_no' : '$match-for-no',
                                   'fields'       : '$fields'}})

    command.add_action('delete-objects', delete_objects,
                       {'kwargs': {'data'             : '$data',
                                   'path'             : '$path',
                                   'submode_adjustor' : '$submode-adjustor' }})

    command.add_action('write-object', write_object,
                       {'kwargs': {'data'         : '$data',
                                   'path'         : '$path',
                                   'parent_field' : '$parent-field',
                                   'parent_id'    : '$current-mode-obj-id',
                                   'scoped'       : '$scoped'}})
        
    command.add_action('set-data', set_data,
                       {'kwargs': {'data': '$data',
                                   'key': '$key',
                                   'value': '$value'}})

    command.add_action('push-mode-stack', push_mode_stack,
                       {'kwargs': {'mode_name'        : '$submode-name',
                                   'path'             : '$path',
                                   'item_name'        : '$item-name',
                                   'parent_field'     : '$parent-field',
                                   'data'             : '$data',
                                   'submode_adjustor' : '$submode-adjustor',
                                   'show_this'        : '$show-this',
                                   'create'           : '$create'}})

    command.add_action('pop-mode-stack', pop_mode_stack)

    command.add_action('confirm', confirm_request,
                        {'kwargs': {'prompt': '$prompt'}})

    command.add_action('version',  command_version,
                        {'kwargs': {'data' : '$data',
                                   }})

    command.add_action('clearterm',  command_clearterm)

    command.add_action('display-cli',  command_display_cli,
                        {'kwargs': {'data'   : '$data',
                                    'detail' : '$detail',
                                   }})

    command.add_action('cli-set', command_cli_set,
                        {'kwargs': {'variable' : '$variable',
                                    'data'     : '$data',
                                   }})

    command.add_action('cli-unset', command_cli_unset,
                        {'kwargs': {'variable' : '$variable',
                                    'data'     : '$data',
                                   }})

    command.add_action('shell-command', command_shell_command,
                        {'kwargs': {'script' : '$command',
                                   }})
    
    command.add_action('rest-post-data', command_rest_post_data,
                        {'kwargs': {'path': '$path',
                                    'data': '$data',
                                    'verb': '$verb',
                                    'meta': '$meta'
                                    }})

    command.add_action('show-init', command_bigdb_show_init,)
            
    command.add_action('show-compose', command_bigdb_show_compose,
                        {'kwargs': {'path'   : '$path',
                                    'data'   : '$data',
                                    'scoped' : '$scoped',
                                    'style'  : '$style',
                                    'detail' : '$detail',
                                    }})

    command.add_action('show-print', command_bigdb_show_print,
                        {'kwargs': {'data'        : '$data',
                                    'path'        : '$path',
                                    'style'       : '$style',
                                    'select'      : '$select',
                                    'format'      : '$format',
                                    'detail'      : '$detail',
                                    'force_title' : '$force-title',
                                    }})

    command.add_action('show', command_bigdb_show,
                        {'kwargs': {'path'        : '$path',
                                    'data'        : '$data',
                                    'style'       : '$style',
                                    'select'      : '$select',
                                    'sort'        : '$sort',
                                    'format'      : '$format',
                                    'detail'      : '$detail',
                                    'scoped'      : '$scoped',
                                    'force_title' : '$force-title',
                                    }})

    command.add_action('device-compute-device-id', command_device_compute_host_id,
                       {'kwargs': {'data'              : '$data', }})

    command.add_action('bigdb-config-transform', command_bigdb_config_transform,
                       {'kwargs': {'data'              : '$data', } })

    command.add_action('dump-log', command_dump_log,
                        {'kwargs' : { 'data' : '$data', }})

    command.add_action('rbac-required', command_rbac_required,
                        {'kwargs' : { 'data' : '$data',
                                      'group' : '$group', }})

    command.add_action('update-flow-cookies', command_update_flow_cookies,)

    command.add_action('echo', command_echo,
                        {'kwargs' : { 'message'    : '$message',
                                      'batch_only' : '$batch-only',
                        }})

    command.add_action('slave-banner', command_slave_warning, )

    command.add_action('validate-switch', command_validate_switch, )


    command.add_action('running-config', command_running_config,
                                {'kwargs' : { 'data'   : '$data',
                                 }})

