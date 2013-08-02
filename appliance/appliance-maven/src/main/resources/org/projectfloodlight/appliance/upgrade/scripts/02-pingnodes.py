#!/usr/bin/env python

import subprocess
import urllib2
import simplejson
import sys
import traceback
from threading import Thread
from Queue import Queue
import syslog

def output(message):
    syslog.syslog(message)
    print message

syslog.openlog("upgrade")

def pinger(queue, success):
    while True:
        host = queue.get()
        ret = subprocess.call("ping -c 1 %s" % host, shell=True, stdout=open('/dev/null', 'w'), stderr=subprocess.STDOUT)
        if ret == 0:
            success.append({'host':host, 'success':True})
        else:
            success.append({'host':host, 'success':False})
        queue.task_done()

def pingHosts(hosts = []):
    num_threads = 4
    success = []
    queue = Queue()
    for i in range(num_threads):
        worker = Thread(target=pinger, args=(queue, success))
        worker.setDaemon(True)
        worker.start()
    for h in hosts:
        queue.put(h)
    queue.join()
    ret = True
    for s in success:
        if s['success'] == False:
            ret = False
    return ret

def getControllerIps(host = "localhost"):
    ips = []
    url = "http://" + host + "/rest/v1/model/controller-interface"
    response = urllib2.urlopen(url).read() 
    controllers = simplejson.loads(response)
    for c in controllers:
        ips.append(c['discovered-ip'])
    return ips


def main():
    force = "--force" in sys.argv
    ret = True
    try:
        ips = getControllerIps()
        output("Pinging: %s" % ", ".join(ips))
        ret = pingHosts(ips)
    except Exception, e:
        output("Error verifying connectivity: " + str(e))
        if force:
            output("Continuing despite error because of force")
            sys.exit(0)

        sys.exit(1)

    if ret:
        #print '{"status":"OK", "description":"All nodes responded"}'
        output("All nodes responded")
        sys.exit(0)
    else:
        #print '{"status":"ERROR", "description":"Not all nodes responded"}'
        output("Not all nodes responded")
        if force:
            output("Continuing despite error because of force")
            sys.exit(0)
        sys.exit(2)
if __name__ == "__main__":
    main()
