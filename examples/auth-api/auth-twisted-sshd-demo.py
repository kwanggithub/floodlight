#!/usr/bin/env python
#
# A Small ssh server that does authentication against the floodlight rest API, then
# sets the cookie id to environment variable BSC_SESSION_COOKIE and launches
# a given program

from twisted.web.client import Agent
from twisted.web.http_headers import Headers
import twisted.python.log as log

from twisted.internet.defer import succeed, setDebugging
from twisted.web.iweb import IBodyProducer

from twisted.cred import portal, error, checkers, credentials
from twisted.cred.checkers import ICredentialsChecker
from twisted.conch import error, avatar
from twisted.conch.checkers import SSHPublicKeyDatabase
from twisted.conch.ssh import factory, userauth, connection, keys, session
from twisted.internet import reactor, protocol, defer
from twisted.python import log
from zope.interface import implements

import copy
import json
from optparse import OptionParser
import re
import sys

log.startLogging(sys.stderr)
setDebugging(True)
class StringProducer(object):
    implements(IBodyProducer)

    def __init__(self, body):
        self.body = body
        self.length = len(body)

    def startProducing(self, consumer):
        consumer.write(self.body)
        return succeed(None)

    def pauseProducing(self):
        pass

    def stopProducing(self):
        pass

class ExampleAvatar(avatar.ConchUser):

    def __init__(self, username):
        avatar.ConchUser.__init__(self)
        self.username = username
        self.channelLookup.update({'session':session.SSHSession})

class ExampleRealm:
    implements(portal.IRealm)

    def requestAvatar(self, avatarId, mind, *interfaces):
        return interfaces[0], ExampleAvatar(avatarId), lambda: None

new_line = re.compile(r'\r\n|\r|\n')

class ProcessClientProtocol(protocol.ProcessProtocol):
    def __init__(self, peer):
        self.peer = peer
        self.ended = False

    def connectionMade(self):
        log.msg("Process client established")
        self.peer.peer = self
    def outReceived(self, data):
       self.peer.transport.write(new_line.sub(data, "\r"))
    def errReceived(self, data):
       self.peer.transport.write(data)
    def processEnded(self, reason):
        if not self.ended:
            self.ended = True
            self.peer.transport.loseConnection()
    def exit(self):
        if not self.ended:
            self.transport.signalProcess('KILL')


class SSHServerConsoleProtocol(protocol.Protocol):
    def __init__(self, terminal):
        self.terminal = terminal

    """this is our example protocol that we will run over SSH
    """
    def connectionMade(self):
        log.msg("SSHServerConsoleProtocol: connection made")
        client = ProcessClientProtocol(self)
        local_env = copy.copy(env)
        local_env['TERM'] = self.terminal
        reactor.spawnProcess(client, args[0], args, local_env, usePTY=True)

    def dataReceived(self, data):
        if self.peer:
            data = new_line.sub('\n', data)
            log.msg("Sent data '%s' to process" % repr(data))
            self.peer.transport.write(data)
    def connectionLost(self, reason):
        if self.peer:
            self.peer.exit()

publicKey = 'ssh-rsa AAAAB3NzaC1yc2EAAAABIwAAAGEArzJx8OYOnJmzf4tfBEvLi8DVPrJ3/c9k2I/Az64fxjHf9imyRJbixtQhlH9lfNjUIx+4LmrJH5QNRsFporcHDKOTwTTYLh5KmRpslkYHRivcJSkbh/C+BR3utDS555mV'

privateKey = """-----BEGIN RSA PRIVATE KEY-----
MIIByAIBAAJhAK8ycfDmDpyZs3+LXwRLy4vA1T6yd/3PZNiPwM+uH8Yx3/YpskSW
4sbUIZR/ZXzY1CMfuC5qyR+UDUbBaaK3Bwyjk8E02C4eSpkabJZGB0Yr3CUpG4fw
vgUd7rQ0ueeZlQIBIwJgbh+1VZfr7WftK5lu7MHtqE1S1vPWZQYE3+VUn8yJADyb
Z4fsZaCrzW9lkIqXkE3GIY+ojdhZhkO1gbG0118sIgphwSWKRxK0mvh6ERxKqIt1
xJEJO74EykXZV4oNJ8sjAjEA3J9r2ZghVhGN6V8DnQrTk24Td0E8hU8AcP0FVP+8
PQm/g/aXf2QQkQT+omdHVEJrAjEAy0pL0EBH6EVS98evDCBtQw22OZT52qXlAwZ2
gyTriKFVoqjeEjt3SZKKqXHSApP/AjBLpF99zcJJZRq2abgYlf9lv1chkrWqDHUu
DZttmYJeEfiFBBavVYIF1dOlZT0G8jMCMBc7sOSZodFnAiryP+Qg9otSBjJ3bQML
pSTqy7c3a2AScC/YyOwkDaICHnnD3XyjMwIxALRzl0tQEKMXs6hH8ToUdlLROCrP
EhQ0wahUTCk1gKA4uPD6TMTChavbh4K63OvbKg==
-----END RSA PRIVATE KEY-----"""


class InMemoryPublicKeyChecker(SSHPublicKeyDatabase):

    def checkKey(self, credentials):
        return credentials.username == 'user' and \
            keys.Key.fromString(data=publicKey).blob() == credentials.blob

class FloodlightRestPasswordChecker(object):
    implements(ICredentialsChecker)
    credentialInterfaces = ( credentials.IUsernamePassword, )

    def __init__(self, agent, url, env):
        self.agent = agent
        self.url = url
        self.env = env

    def requestAvatarId(self, c):
        def cbRequest(response):
                log.msg('cbRequest Got Response code: %s'%response.code)
                for s in response.headers.getRawHeaders("Set-Cookie", ()):
                    (key, value) = s.split("=")
                    if key == "session_cookie":
                        self.env["BSC_SESSION_COOKIE"] = value

                if response.code == 200:
                    return defer.succeed(c.username)
                else:
                    return defer.fail(error.UnauthorizedLogin())
        def cbError(reason):
            print "+++ error during http request +++"
            reason.printTraceback()
            return defer.fail(error.UnauthorizedLogin(reason.getErrorMessage()))

        headers = {'Content-type': [ 'application/json' ]}
        req = {'user': c.username, 'password' : c.password }
        serialized_payload = json.dumps(req)
        log.msg("Requesting Payload %s" % serialized_payload)
        try:
            d = agent.request('POST', self.url, Headers(headers), StringProducer(serialized_payload))
            d.addCallbacks(cbRequest)
            d.addErrback(cbError)
        except Exception as e:
            log.error("Auth failed",e)
            return defer.fail(error.UnauthorizedLogin())
        log.msg("Returning deferred")
        return d

class ExampleSession:

    def __init__(self, avatar):
        """
        We don't use it, but the adapter is passed the avatar as its first
        argument.
        """

    def getPty(self, term, windowSize, attrs):
        self.terminalName = term
        self.windowSize = windowSize
        pass

    def execCommand(self, proto, cmd):
        raise Exception("no executing commands")

    def openShell(self, trans):
        log.msg(
            "Your terminal name is %r.  "
            "Your terminal is %d columns wide and %d rows tall." % (
                self.terminalName, self.windowSize[0], self.windowSize[1]))
        log.msg("ExampleSession: open shell!")
        ep = SSHServerConsoleProtocol(self.terminalName)
        ep.makeConnection(trans)
        trans.makeConnection(session.wrapProtocol(ep))

    def eofReceived(self):
        pass

    def closed(self):
        pass

from twisted.python import components
components.registerAdapter(ExampleSession, ExampleAvatar, session.ISession)

class ExampleFactory(factory.SSHFactory):
    publicKeys = {
        'ssh-rsa': keys.Key.fromString(data=publicKey)
    }
    privateKeys = {
        'ssh-rsa': keys.Key.fromString(data=privateKey)
    }
    services = {
        'ssh-userauth': userauth.SSHUserAuthServer,
        'ssh-connection': connection.SSHConnection
    }

parser = OptionParser()
parser.add_option("-H", "--host", dest="host",
        help="host and port connect to for REST authentication [%default]", default="localhost:8082", metavar="HOST")
parser.add_option("-p", "--port", dest="port",
        help="port the ssh server listens to", default=5022, type=int, metavar="PORT")
(options, args) = parser.parse_args()

if len(args) == 0:
    print "Call syntax: %s [OPTIONS] -- command to run"
    sys.exit(1)

env = {}
portal = portal.Portal(ExampleRealm())
agent = Agent(reactor)

restChecker = FloodlightRestPasswordChecker(agent, "http://%s/api/v1/auth/login" % options.host, env)
portal.registerChecker(restChecker)
ExampleFactory.portal = portal

if __name__ == '__main__':
    reactor.listenTCP(options.port, ExampleFactory())
    reactor.run()
