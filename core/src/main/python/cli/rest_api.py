#
# Copyright (c) 2010,2011,2012 Big Switch Networks, Inc.
# All rights reserved.
#

#
#  module: storeclient.py
#
# This module manages communication with the console, i.e. the REST interface
# of a Big Switch Controller node.

import urllib
import urllib2
import ftplib
import json
import datetime
import base64

import time
import traceback
import url_cache
import os
import debug

class StringReader():
    # used for ftp, as a replacement for read from an existing file
    def __init__(self, value):
        """
        Value can be a string, or a generator.
        """
        self.value = value
        self.offset = 0
        self.last = None
        self.len = None
        if type(value) == str or type(value) == unicode:
            self.len = len(value)

    def read(self, size = None):
        if self.len: # string? (ie: not generator)
            if size == None:
                value = self.value
                self.value = ''
                return value
            else: # size has a number attached
                if size > self.len - self.offset:
                    size = self.len - self.offset
                result = self.value[self.offset:self.offset+size]
                self.offset += size
                return result
        # generator
        if self.last:   # use remainder
            if self.offset + size >= len(self.last):
                size = len(self.last) - self.offset
            result = self.last[self.offset:self.offset+size]
            self.offset += size
            if self.offset == self.len:
                self.last = None
            return result
        item = self.value.next()
        len_item = len(item)
        if len_item <= size:
            return item
        # set up remainder
        result = item[:size]
        self.last = item[size:]
        self.offset = 0
        return result


class RestApi():

    controller = None
    display_rest = False
    display_rest_reply = False
    
    table_read_url = "http://%s/rest/v1/model/%s/"
    entry_post_url = "http://%s/rest/v1/model/%s/"
    user_data_url = "http://%s/rest/v1/data/"

    def __init__(self, controller = None):
        self.cache_session_cookie()
        self.cache_session_address()
        if controller:
            self.set_controller(controller)

    def set_controller(self, controller):
        self.controller = controller

    def display_mode(self, mode):
        self.display_rest = mode

    def display_reply_mode(self, mode):
        self.display_rest_reply = mode

    def cache_session_cookie(self):
        """
        Since the session cookies is stable for the duration
        of the session, saving it here makes sense
        """
        self.session_cookie = os.environ.get('BSC_SESSION_COOKIE')

    def cache_session_address(self):
        """
        Since the session cookies is stable for the duration
        of the session, saving it here makes sense
        """
        if 'SSH_CONNECTION' in os.environ:
            self.session_address = os.environ['SSH_CONNECTION'].split()[0]
        else:
            self.session_address = None

    @staticmethod
    def get_application_key(keyfile):

        try:
            buf = open(keyfile).read()
        except IOError:
            buf = "{}"

        try:
            data = json.loads(buf)
        except ValueError:
            data = {}

        name = data.get('name', None)
        secret = data.get('secret', None)

        if name is None: return None
        if secret is None: return None

        decoded = name + ":" + secret
        return base64.b64encode(decoded)

    def url_cookie_applies(self, url):
        if url.startswith('http://%s' % self.controller):
            return True
        parts = self.controller.split(':')
        if len(parts) > 1:
            if url.startswith('http://%s:8082' % parts[0]):
                # perhaps parts[1] needs to also be checked?
                return True
            # :80 can be stripped, sicne its not really needed
            # XXX this can be better, test for parts[1] == '80'
            if url.startswith('http://%s' % parts[0]):
                return True
        return False
            
    def json_headers(self, url, cookieAuth=True, appAuthFile=None):
        headers = {'Content-Type':'application/json'}
        if cookieAuth and self.session_cookie is not None:
            if self.url_cookie_applies(url):
                headers['Cookie'] = 'session_cookie=%s' % self.session_cookie
        if self.session_address is not None:
            headers['X-Forwarded-For'] = self.session_address

        if appAuthFile is not None:
            key = self.get_application_key(appAuthFile)
            if key is not None:
                headers['Authorization'] = "Basic " + key

        return headers

    def text_headers(self, cookieAuth=True, appAuthFile=None):
        headers = {'Content-Type':'text/plain'}
        if cookieAuth and self.session_cookie is not None:
            headers['Cookie'] = 'session_cookie=%s' % self.session_cookie
        if self.session_address is not None:
            headers['X-Forwarded-For'] = self.session_address

        if appAuthFile is not None:
            key = get_application_key(appAuthFile)
            if key is not None:
                headers['Authorization'] = "Basic " + key

        return headers

    def rest_simple_request(self, url, use_cache = None, timeout = None):
        # include a trivial retry mechanism ... other specific
        #  urllib2 exception types may need to be included
        retry_count = 0
        if use_cache == None or use_cache:
            result = url_cache.get_cached_url(url)
            if result != None:
                return result
        while retry_count > 0:
            try:
                request = urllib2.Request(url, headers = self.json_headers(url))
                return urllib2.urlopen(request, timeout = timeout).read()
            except urllib2.URLError:
                retry_count -= 1
                time.sleep(1)
        # try again without the try...
        if self.display_rest:
            print "REST-SIMPLE:", 'GET', url

        request = urllib2.Request(url, headers = self.json_headers(url))
        result = urllib2.urlopen(request, timeout = timeout).read()

        if self.display_rest_reply:
            print 'REST-SIMPLE: %s reply "%s"' % (url, result)
        url_cache.save_url(url, result)
        return result


    def rest_url_open(self, url):
        if self.display_rest:
            print 'REST-URL-OPEN (NO REPLY): GET %s' % url
        request = urllib2.urlopen(url)
        return request


    def rest_json_request(self, url, timeout = None):
        entries =  url_cache.get_cached_url(url)
        if entries != None:
            return entries

        result = self.rest_simple_request(url, timeout = timeout)
        # XXX check result
        entries = json.loads(result)
        url_cache.save_url(url, entries)

        return entries

    def rest_post_request(self, url, obj, verb='PUT',
                          cookieAuth=True, appAuthFile=None):
        post_data = json.dumps(obj)
        if self.display_rest:
            print "REST-POST:", verb, url, post_data
        headers = self.json_headers(url, cookieAuth=cookieAuth, appAuthFile=appAuthFile)
        request = urllib2.Request(url, post_data, headers)
        request.get_method = lambda: verb
        response = urllib2.urlopen(request)
        result = response.read()
        if self.display_rest_reply:
            print 'REST-POST: %s reply: "%s"' % (url, result)
        return result
    
    
    def get_table_from_store(self, table_name, key=None, val=None, match=None, timeout=None):
        if not self.controller:
            print "No controller specified. Set using 'controller <server:port>'."
            return
        url = self.table_read_url % (self.controller, table_name)
        if not match:
            match = "startswith"
        if key and val:
            url = "%s?%s__%s=%s" % (url, key, match, urllib.quote_plus(val))
        result = url_cache.get_cached_url(url)
        if result != None:
            return result
        data = self.rest_simple_request(url, timeout=timeout)
        entries = json.loads(data)
        url_cache.save_url(url, entries)
        return entries


    def get_object_from_store(self, table_name, pk_value):
        if not self.controller:
            print "No controller specified. Set using 'controller <server:port>'."
            return
        url = self.table_read_url % (self.controller, table_name)
        url += (pk_value + '/')
        result = url_cache.get_cached_url(url)
        if result != None:
            return result
        if self.display_rest:
            print "REST-MODEL:", url
        request = urllib2.Request(url, headers = self.json_headers(url))
        response = urllib2.urlopen(request)
        if response.code != 200:
            # LOOK! Should probably raise exception here instead.
            # In general we need to rethink the store interface and how
            # we should use exceptions.
            return None
        data = response.read()
        result = json.loads(data)
        if self.display_rest_reply:
            print 'REST-MODEL: %s reply: "%s"' % (url, result)
        url_cache.save_url(url, result)
        return result

    def set_user_data_file(self, name, text):
        url = self.user_data_url % (self.controller)
        version = 1 # default
        # find the latest version for a name
        existing_data = self.get_user_data_table(name, "latest") 
        if len(existing_data) > 0: # should be at most 1, but just in case...
            version = max([int(f['version']) for f in existing_data]) + 1 # LOOK! race?
        length = len(text)
        # LOOK! what to do about time in a distributed system!
        timestamp = datetime.datetime.utcnow().strftime("%Y-%m-%d.%H:%M:%S")
        url += "%s/timestamp=%s/version=%s/length=%s/" % (name, timestamp, version, length)
        return self.copy_text_to_url(url, text)

    def get_user_data_file(self, name):
        url = self.user_data_url % (self.controller)
        url += name + "/"
        return self.rest_simple_request(url)

    def delete_user_data_file(self, name):
        url = self.user_data_url % (self.controller)
        url += name + "/"
        data = self.rest_post_request(url, {}, 'DELETE')
        if data != "deleted":
            result = json.loads(data)
            return result

    def get_user_data_table(self, name=None, show_version="latest"):
        if not self.controller:
            print "No controller specified. Set using 'controller <server:port>'."
            return None
        url = self.user_data_url % self.controller
        if name:
            url += "?name__startswith=%s" % name
        data = self.rest_simple_request(url)
        new_data = []
        data = json.loads(data)
        latest_versions = {}  # dict of latest version per name
        for d in data:  # list of dicts
            l = d['name'].split('/') # ex: startup/timestamp=2010-11-03.05:51:27/version=1/length=2038
            nd = dict([item.split('=') for item in l[1:]])
            nd['name'] = l[0]
            nd['full_name'] = d['name']
            new_data.append(nd)
            if not nd['name'] in latest_versions or int(nd['version']) > int(latest_versions[nd['name']]):
                latest_versions[nd['name']] = nd['version'] # initialize first time

        # prune if needed to a name or a particular version
        if name:
            new_data = [ nd for nd in new_data if nd['name'].startswith(name) ]
        if show_version == "latest":
            new_data = [ nd for nd in new_data if not int(nd['version']) < int(latest_versions[nd['name']]) ]
        elif show_version != "all":
            new_data = [ nd for nd in new_data if nd['version'] == show_version ]
        return new_data


    # LOOK! looks a lot like a rest_post_request except we don't jsonify and we handle
    # errors differently... refactor?  Same with get_text and rest_simple_request
    def copy_text_to_url(self, url, src_text, message = None):
        post_data = src_text
        if url.startswith('ftp://'):
            url_suffix = url[6:]
            user = 'anonymous'
            password = ''
            if url_suffix.find('@') != -1:
                url_parts = url_suffix.split('@')
                url_user_and_password = url_parts[0]
                url_suffix = url_parts[1]
                if url_user_and_password.find(':') != -1:
                    user_and_password = url_user_and_password.split(':')
                    user = user_and_password[0]
                    password = user_and_password[1]
                else:
                    user = url_user_and_password

            host = url_suffix
            path = None
            if url_suffix.find('/'):
                url_suffix_parts = url_suffix.split('/')
                host = url_suffix_parts[0]
                path = url_suffix_parts[1]
            ftp_target = ftplib.FTP(host, user, password)

            ftp_target.storbinary('STOR %s' % path, StringReader(post_data))
            # apparently, storbinary doesn't provide a return value
            result = { "result" : "success" } # don't display any other error messages
        else:
            request = urllib2.Request(url, post_data, {'Content-Type':'text/plain'})
            request.get_method = lambda: 'PUT'
            if self.display_rest:
                print "REST-TEXT-TO:", request
            response = urllib2.urlopen(request)
            result = response.read()
            if self.display_rest_reply:
                print 'REST-TEXT-TO: %s reply "%s"' % (request, result)
        return result

    def get_text_from_url(self, url):
        if self.display_rest:
            print "REST-TEXT-FROM:", url
        result = urllib2.urlopen(url).read()
        if self.display_rest_reply:
            print 'REST-TEXT-FROM: %s result:"%s"' % (url, result)
        return result
