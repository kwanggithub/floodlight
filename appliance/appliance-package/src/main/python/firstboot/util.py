#!/usr/bin/env python

import env
import subprocess
import signal
import log_util
import os

logger = log_util.getModuleLogger(__name__)

# run
def run(c, ignoreError=False, sudo=False):
    def pre():
        os.setpgrp()
        signal.signal(signal.SIGINT, signal.SIG_IGN)

    args = ['/bin/bash', '-c', c]
    if sudo:
        args.insert(0, '/usr/bin/sudo')
    p = subprocess.Popen(args,
                         stdout=subprocess.PIPE,
                         stderr=subprocess.PIPE)
    r = p.communicate()
    logger.debug("running command, ignoreError=%s, sudo=%s" % 
                 (ignoreError, sudo))
    logger.debug("Cmd    : %s" % c)
    logger.debug("Stdout : %s" % str(r[0]).strip())
    logger.debug("Stderr : %s" % str(r[1]).strip())
    if p.returncode != 0 and not ignoreError:
        raise Exception("Non-zero return code! cmd=%s output=%s" % (c, r))
    return r[0]

def ipToInt(ip):
    octets = ip.split(".")
    assert len(octets) == 4
    ipInt = 0
    for n, o in enumerate(octets):
        ipInt += int(o) << (8*(3 - n))
    return ipInt

def intToIp(n):
    return ".".join([str((n >> (3 - i)*8) & 255) for i in range(4)])

def cidrToNetmaskInt(cidr):
    assert cidr >= 0 and cidr <= 32
    return (1 << 32) - (1 << (32 - cidr))

def netmaskToCidr(netmask):
    netmaskInt = ipToInt(netmask)
    for cidr in range(0, 33):
        if cidrToNetmaskInt(cidr) == netmaskInt:
            return cidr
    assert False
