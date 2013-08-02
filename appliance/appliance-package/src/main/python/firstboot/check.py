#!/usr/bin/env python

import util
import re

ipRegex = re.compile(r"^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$")
hostRegex = re.compile(r"^([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\-]{0,61}[A-Za-z0-9])$")
hostOrDomainRegex = re.compile(r"^(([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\-]{0,61}[A-Za-z0-9])\.)*([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\-]{0,61}[A-Za-z0-9])$")

# omitting 255.255.255.255 and 255.255.255.254
validCidrs = [i for i in range(0, 31)]
validSubnets = [util.cidrToNetmaskInt(cidr) for cidr in validCidrs]

def checkIp(ip):
    if ipRegex.match(ip) == None:
        return False

    ipInt = util.ipToInt(ip)
    if ipInt == 0:
        return False
    if (ipInt >> 24) == 127:
        return False
    return True

def checkCidr(cidr):
    cidrInt = -1
    try:
        cidrInt = int(cidr)
    except:
        return False

    return cidrInt in validCidrs

def checkNetmask(netmask):
    if ipRegex.match(netmask) == None:
        return False

    netmaskInt = util.ipToInt(netmask)
    return netmaskInt in validSubnets

def checkIpsInSameSubnet(ip1, ip2, netmask):
    ip1Int = util.ipToInt(ip1)
    ip2Int = util.ipToInt(ip2)
    netmaskInt = util.ipToInt(netmask)
    return (ip1Int & netmaskInt) == (ip2Int & netmaskInt)

def checkHostname(hostname):
    return hostRegex.match(hostname) != None

def checkDomain(domain):
    return hostOrDomainRegex.match(domain) != None
