import os
import sys
import subprocess
import urllib2
import signal

def preexec_function():
    signal.signal(signal.SIGINT, signal.SIG_DFL)

def command_request(argv):
    if len(argv) > 1 and argv[1] == '-c' and len(argv) > 2:
        return argv[2]
    return None

def is_scp_command_request(argv_command_request):
    # Note: the complete scp command is identified here, which
    #  is a bit brittle, but likely the right thing.
    if argv_command_request and argv_command_request.startswith('scp '):
        return argv_command_request
    return None


def main():
    # run bigcli.
    signal.signal(signal.SIGINT, signal.SIG_IGN)

    # determine the invocation, if called with '-c' then perform a single
    # command, if called to invoke scp, since the cli doesn't support
    # scp, exec scp in place of bigcli.

    ssh_command_request = command_request(sys.argv)
    if ssh_command_request and is_scp_command_request(ssh_command_request):
        # if scp is requested, invoke it directly
        # should we have a specific ssh group to allow its use
        # Don't use shell=True, since that opens the possibility of
        # having pipes or multiple commands.
        rc = subprocess.call(['/usr/bin/scp'] +
                             ssh_command_request.split()[1:],
                             preexec_fn = preexec_function)
    elif ssh_command_request:
        rc = subprocess.call(["/usr/bin/floodlight-cli",
                              "-c", ssh_command_request],
                             preexec_fn = preexec_function)
    else:
        # typical case
        rc = subprocess.call("/usr/bin/floodlight-cli",
                             preexec_fn = preexec_function)

    # look for the session cookie.
    cookie = os.environ.get('FL_SESSION_COOKIE')
    if cookie:
        # Nothing fancy here, just a simple request to delete a cookie
        # 8082 ought to be removed after apache2/bigdb consolidation
        url = 'http://localhost:8082/api/v1/data/controller/core/aaa/session[auth-token="%s"]' % cookie
        headers = { 'Cookie' : 'session_cookie=%s' % cookie,
                    'Content-Type':'application/json'}
        request = urllib2.Request(url, headers = headers)
        request.get_method = lambda: 'DELETE'
        try:
            response = urllib2.urlopen(request)
            result = response.read()
        except Exception, e:
            pass # silently fail
        # environment variable is useless now.

if __name__ == '__main__':
    main()
