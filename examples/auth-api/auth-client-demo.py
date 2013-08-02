#!/usr/bin/python
### Demo script that show cases the BigDB authentication protocol

import httplib as client
from optparse import OptionParser
import inspect
import json
import sys
import logging


class AuthBigDBClient(object):
    def __init__(self, host):
        self.connection = client.HTTPConnection(host)
        self.connection.set_debuglevel(1)

    def login(self, user, password):
        logging.info("== 1st request: login / create session")

        headers = {'Content-type': 'application/json'}
        req = {'user': options.user, 'password' : options.password }
        json_foo = json.dumps(req)

        self.connection.request('POST', '/auth/login', json_foo, headers)

        response = self.connection.getresponse()

        print "Login Status: %d" % response.status
        print "Login: Headers: %s" % response.getheaders()
        json_data = json.loads(response.read().decode())

        print(json_data)
        if json_data.get('session_cookie'):
            print 'export BSC_SESSION_COOKIE="%s"' % json_data['session_cookie']

        if response.status != 200:
            sys.exit(1)
        self.session_cookie = json_data['session_cookie']
        return json_data['session_cookie']

    def authenticated_request(self, url, method='GET', data=''):
        headers = {'Content-type': 'application/json' }

        if session_cookie:
            headers['Cookie'] = 'session_cookie=%s'%session_cookie

        self.connection.request(method, url, data, headers)
        response = self.connection.getresponse()
        print "Status: %d" % response.status
        print "Headers: %s" % response.getheaders()
        print response.read().decode()

    def status(self):
        print "==== request controller status via authenticated API"
        self.authenticated_request('/api/v1/data/controller/core/controller')

    def schema(self):
        print "==== request auth schema"
        self.authenticated_request('/api/v1/schema/controller/core/aaa/session')

    def list_sessions(self):
        print "==== List sessions"
        self.authenticated_request('/api/v1/data/controller/core/aaa/session')

    def get_session_by_cookie(self, cookie):
        print "==== List sessions"
        self.authenticated_request("/api/v1/data/controller/core/aaa/session[auth-token='%s']"% cookie)

    def get_session_by_id(self, get_id):
        print "==== List sessions"
        self.authenticated_request("/api/v1/data/controller/core/aaa/session[id=%s]"% get_id)

    def get(self, url):
        print "==== get %s === " %url
        self.authenticated_request(url)

    def post(self, url):
        print "==== post %s === " % url
        self.authenticated_request(url, method="POST", data=sys.stdin.read())

    def patch(self, url):
        print "==== patch %s === " % url
        self.authenticated_request(url, method="PATCH", data=sys.stdin.read())

    def put(self, url):
        print "==== put %s === " % url
        self.authenticated_request(url, method="PUT", data=sys.stdin.read())


    def delete_session(self, delete_session_id):
        print "==== delete session session: %s" % delete_session_id
        self.authenticated_request("/api/v1/data/controller/core/aaa/session[id=%s]" % delete_session_id, method="DELETE")

    def set_admin_full_name(self, admin_full_name):
        print "==== set_admin_full_name: %s" % admin_full_name
        headers = {'Content-type': 'application/json'}
        req = {'full-name': admin_full_name}
        json_foo = json.dumps(req)

        self.authenticated_request("/api/v1/data/controller/core/aaa/local-user[user-name='admin']", data=json_foo, method="PATCH")


parser = OptionParser()
parser.add_option("-H", "--host", dest="host",
        help="host and port connect to [%default]", default="localhost:8082", metavar="HOST")
parser.add_option("-u", "--user", dest="user", default="admin", help="user to authenticate as [%default]")
parser.add_option("-p", "--pass", dest="password", default="admin", help="password to authenticate with [%default]")
parser.add_option("-n", "--no-auth", dest="authenticate", action="store_false", default=True, help="Do not authenticate")
parser.add_option("-v", "--verbose", dest="verbose", action="store_true", default=False, help="Enable verbose logging")

(options, args) = parser.parse_args()

if options.verbose:
    logging.basicConfig(level=logging.DEBUG)

client = AuthBigDBClient(options.host)


if options.authenticate:
    session_cookie = client.login(options.user, options.password)

if not args:
    print "\nCommands: status, schema, list-sessions, delete-session <session_id>"
    sys.exit(2)

def shift_arg(n=1):
    command = args[0:n]
    for n in range(0,n):
        del args[0]

    return command

while args:
    command = shift_arg()[0].replace("-", "_")

    logging.debug("command: %s" % command)
    if not hasattr(client, command):
        raise Exception("Command %s not found" % command)

    cmd_function = getattr(client, command)

    if not callable(cmd_function):
        raise Exception("Command %s not callable" % command)

    (func_args, _, _, _) = inspect.getargspec(cmd_function)

    cmd_function(*shift_arg(n=len(func_args)-1))
