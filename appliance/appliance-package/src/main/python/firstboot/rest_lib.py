#!/usr/bin/env python

import httplib
import sys
import json
import os

cmap = {}
session_map = {}
if 'FL_SESSION_COOKIE' in os.environ:
    session_map['localhost:8082'] = os.environ['FL_SESSION_COOKIE']

def request(url, prefix="/api/v1/data/controller/", method='GET', 
            data='', host="localhost:8082", secure=False):
    headers = {'Content-type': 'application/json' }
    
    if host in session_map:
        headers['Cookie'] = 'session_cookie=%s' % session_map[host]
    
    if host not in cmap:
        if secure:
            cmap[host] = httplib.HTTPSConnection(host)
        else:
            cmap[host] = httplib.HTTPConnection(host)

    connection = cmap[host]
    connection.request(method, prefix + url, data, headers)
    response = connection.getresponse()
    if (response.status == 200):
        return response.read().decode()
    else:
        e = json.loads(response.read().decode())
        raise Exception(e['description'])

# XXX - This style of query results in information disclosure in the
# logs.  Unfortunately there seems to be no way to fix this with BigDB
# at the moment
def auth(host, username="admin", password=""):
    if host in session_map:
        return
    login = {"user": username,
             "password": password}
    session = json.loads(request("/auth/login",
                                 prefix='',
                                 method='POST', 
                                 data=json.dumps(login), 
                                 host=host, 
                                 secure=True))
    if not session["success"]:
        raise Exception(session["error_message"])
    if ("session_cookie" not in session):
        raise Exception("Failed to authenticate: session cookie not set")
    
    session_map[host] = session["session_cookie"]

def get(url):
    return request(url)

def post(url, data):
    return request(url, method='POST', data=data)

def patch(url, data):
    return request(url, method='PATCH', data=data)

def put(url, data):
    return request(url, method='PUT', data=data)

