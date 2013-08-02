#!/usr/bin/python

import unittest
import subprocess
import os
import BaseHTTPServer
import threading
import urlparse
import json

import config

class HTTPServer(BaseHTTPServer.HTTPServer):

    def __init__(self, test, address, handler_klass):
        BaseHTTPServer.HTTPServer.__init__(self, address, handler_klass)
        self.test = test

        self.timeout = 0.25
        # do not spin forever in select()

class RestRequestHandler(BaseHTTPServer.BaseHTTPRequestHandler):

    def __init__(self, request, address, server):
        BaseHTTPServer.BaseHTTPRequestHandler.__init__(self, request, address, server)

    def _allow(self):

        self.log_message("allowing authentication to proceeed")
        self.send_response(200, "Authentication data follows")
        d = {config.REST_NAME : self.server.test.session,
             'success' : True,
             'message' : 'Congratulations!',
             }
        buf = json.dumps(d)
        sz = len(buf)
        self.send_header("Set-Cookie", "cookie1=some-cookie1-value")
        self.send_header("Set-Cookie", "%s=%s"
                         % (config.COOKIE_NAME, self.server.test.session,))
        self.send_header("Set-Cookie", "cookie2=some-cookie2-value")
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", sz)
        self.end_headers()
        self.wfile.write(buf)

    def _handleForm(self, form):

        user = form.get('user', [None])[0]
        _pass = form.get('password', [None])[0]
        host = form.get('host', [None])[0]
        port = form.get('port', [None])[0]

        self.log_message("user is %s", user)
        self.log_message("pass is %s", _pass)
        self.log_message("host is %s", host)
        self.log_message("port is %s", port)

        if self.server.test.users.get(user, None) == _pass:
            self._allow()
            return

        self.log_error("authentication fails")
        self.send_error(401, "not authorized")

    def _handleJson(self, form):

        user = form.get('user', None)
        _pass = form.get('password', None)
        host = form.get('host', None)
        port = form.get('port', None)

        self.log_message("user is %s", user)
        self.log_message("pass is %s", _pass)
        self.log_message("host is %s", host)
        self.log_message("port is %s", port)

        if self.server.test.users.get(user, None) == _pass:
            self._allow()
            return

        self.log_error("authentication fails")
        self.send_error(401, "not authorized")

    def do_GET(self):

        rec = urlparse.urlparse(self.path)
        if rec.path != "/auth":
            self.send_error(404, "invalid path")
            return

        q = urlparse.parse_qs(rec.query)

        return self._handleForm(q)

    def do_POST(self):

        rec = urlparse.urlparse(self.path)
        if rec.path != "/auth":
            self.send_error(404, "invalid path")
            return

        if rec.query:
            self.send_error(501, "query string not allowed")
            return

        formLen = int(self.headers.get('content-length'))
        buf = self.rfile.read(formLen)
        print repr(buf)
        self.log_message("received %d bytes: %s"
                         % (len(buf), buf.replace('%', '%%'),))

        # figure out the content type here
        # if it's a www form, call _handleForm()
        # if it's JSON, call _handleJson()
        typ = self.headers.get('content-type', 'text/plain')
        if typ.startswith('application/x-www-form-urlencoded'):
            q = urlparse.parse_qs(buf)
            return self._handleForm(q)
        if typ.startswith('application/json'):
            q = json.loads(buf)
            return self._handleJson(q)

        self.send_error(501, "invalid content type: %s" % typ)

class RestMixin:

    server_klass = HTTPServer
    handler_klass = RestRequestHandler
    server_address = ('', 8000)

    users = {
        'testuser' : 'secret',
        }

    session = "some-auth-session"

    def setUpRest(self):
        self.httpd = self.server_klass(self, self.server_address, self.handler_klass)
        self.keep_running = True

        self.thread = threading.Thread(target=self.runServer)
        self.thread.start()

    def runServer(self):
        while self.keep_running:
            self.httpd.handle_request()

    def tearDownRest(self):
        self.keep_running = False
        ##self.httpd.socket.close()
        self.thread.join()

class TestCase(RestMixin,
               unittest.TestCase):

    def setUp(self):
        self.setUpRest()

    def tearDown(self):
        self.tearDownRest()

    def test_ping(self):

        testbin = os.path.join(os.environ['abs_builddir'], 'test_pam_rest')
        cmd = (testbin,)
        ##cmd = ("gdb", testbin,)
        pipe = subprocess.Popen(cmd,
                                stdout=subprocess.PIPE)

        # process will attempt to connect to us for authorization
        
        out, _ = pipe.communicate()
        code = pipe.wait()
        self.assertEquals(code, 0)

        self.assertEquals(out.strip(), self.session)

if __name__ == "__main__":
    unittest.main()
