#!/usr/bin/python

import json
import sys
import os
import subprocess
import re
import syslog

def output(message):
    syslog.syslog(message)
    print message

syslog.openlog("upgrade")

upgradePkg = sys.argv[1]
force = "--force" in sys.argv

checksums = subprocess.check_output(["unzip", "-p", 
                                     upgradePkg, 
                                     "checksums"]).strip().split('\n')
for sumline in checksums:
    (csum, path) = sumline.split()
    path = re.sub(r"^\./", "", path)

    p1 = subprocess.Popen(["unzip", "-p", upgradePkg, path], 
                          stdout=subprocess.PIPE)
    p2 = subprocess.Popen(["sha1sum"], stdin=p1.stdout, 
                          stdout=subprocess.PIPE)
    out = p2.communicate()[0]
    found_csum = out.split()[0]
    
    if (csum != found_csum):
        output("Error in checksum for %s: expected %s got %s" % \
            (path, csum, found_csum))
        if not force:
            output("Continuing despite checksum errors because of force")
            sys.exit(1)
    output("Verified %s" % path)
