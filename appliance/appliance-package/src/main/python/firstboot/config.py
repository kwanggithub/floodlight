#!/usr/bin/env python

from util import run

import pdesc
import env
import core_util
import util
import log_util
import rest_lib
import json
import time
import netifaces
import ipaddr
import urllib
import re

logger = log_util.getMainLogger(__name__)

def getv(map, key):
    if (key in map):
        return map[key]
    else:
        return None

def getl(map, key):
    if (key in map):
        return [map[key]]
    else:
        return None

def waitFor(fun, max=10, *args, **kwargs):
    start = time.time();
    while True:
        if fun(*args, **kwargs):
            return True
        
        time.sleep(.5)
        now = time.time();
        if (now - start >= max):
            return False

def testPing(remote, count=1, deadline=10):
    out = run("ping -c %d -w %d %s" % (count, deadline, remote), ignoreError=True)
    if " 0% packet loss" in out:
        return True
    return False

class Config(object):
    def __init__(self, configMap):
        self.configMap = configMap
        self.stage = -1
        self.thisNodeId = None

    def _nextStage(self):
        stage = self.stage
        self.stage += 1
        return stage

    def _initSystem(self):
        logger.info("[Stage %d] Initializing system" % self._nextStage())

        # Set admin password
        pw = urllib.quote(self.configMap[pdesc.PARAM_MASTER_PASSWORD], '')
        hp = json.loads(rest_lib.get("core/aaa/hash-password[password=\"%s\"]" % 
                                     (pw)))
        if len(hp) == 0:
            raise Exception("Failed to update password")
        rest_lib.put("core/aaa/local-user[user-name=\"admin\"]/password", 
                     json.dumps(hp[0]['hashed-password']))

        # Set recovery password
        setPass = {"user-name": "recovery",
                   "password": self.configMap[pdesc.PARAM_RECOVERY_PASSWORD]}
        rest_lib.post("os/action/system-user/reset-password", json.dumps(setPass))

        # Initialize SSL certificate and SSH key
        logger.info("  Generating cryptographic keys")
        regenerateKeys = {"action": ["web-ssl", "ssh"]}
        rest_lib.post("os/action/services/regenerate-keys", 
                      json.dumps(regenerateKeys))

    def _checkCluster(self):
        if (self.configMap[pdesc.PARAM_CLUSTER_OPTION] == pdesc.ROLE_SLAVE):
            # Check connectivity
            mip = self.configMap[pdesc.PARAM_MASTER_IP]
            if (not testPing(mip)):
                raise Exception("Could not ping remote master node %s" % mip)

    def _configController(self):
        logger.info("[Stage %d] Configuring controller" % self._nextStage())

        # Set up local node configuration
        if (self.configMap[pdesc.PARAM_IP_MODE] == pdesc.IP_STATIC):
            iface = {"type": "Ethernet",
                     "number": 0,
                     "config-mode": "static",
                     "ip-address": getv(self.configMap,pdesc.PARAM_IP_ADDRESS),
                     "netmask": getv(self.configMap,pdesc.PARAM_IP_NETMASK)}
        else:
            iface = {"type": "Ethernet",
                     "number": 0,
                     "config-mode": "dhcp"}

        networkConfig = {"default-gateway": getv(self.configMap, 
                                                 pdesc.PARAM_IP_GATEWAY),
                         "domain-lookups-enabled": True,
                         "domain-name": getv(self.configMap,pdesc.PARAM_DOMAIN),
                         "dns-servers": getl(self.configMap,pdesc.PARAM_DNS),
                         "network-interfaces": [iface]}
        timeConfig = {}
        if (self.configMap[pdesc.PARAM_CLUSTER_OPTION] == pdesc.ROLE_MASTER):
            timeConfig = {"ntp-servers": [self.configMap[pdesc.PARAM_NTP]]}
        cnode = {"network-config": networkConfig, "time-config": timeConfig}
        osconfig = {"local-node": cnode}

        #print json.dumps(osconfig, indent=2)
        rest_lib.patch("os/config", json.dumps(osconfig))
        
        logger.info("  Waiting for network configuration")
        if (not self._waitForNetwork()):
            raise Exception("Could not acquire a usable IP address on eth0")
        logger.info("  IP address on eth0 is %s" % self.ipAddr)

        # Retrieve time configuration from remote node and copy locally
        if (self.configMap[pdesc.PARAM_CLUSTER_OPTION] == pdesc.ROLE_SLAVE):
            mip = self.configMap[pdesc.PARAM_MASTER_IP]
            host = "%s:8443" % mip
            rest_lib.auth(host, password=self.configMap[pdesc.PARAM_MASTER_PASSWORD])

            time_config = json.loads(rest_lib.request("os/config/local-node/time-config",
                                                      host="%s:8443" % mip, secure=True))
            if (len(time_config) == 0):
                raise Exception("Could not retrieve time configuration from %s" % mip)
            
            time_config = time_config[0]
            if ('ntp-servers' in time_config and len(time_config['ntp-servers']) > 0):
                self.configMap[pdesc.PARAM_NTP] = time_config['ntp-servers'][0]
                
            rest_lib.patch("os/config/local-node/time-config", json.dumps(time_config))

        # Set system time using ntpdate
        logger.info("  Retrieving time from NTP server %s" % 
                    self.configMap[pdesc.PARAM_NTP])
        ntpAction = {"ntp-server": self.configMap[pdesc.PARAM_NTP]}
        rest_lib.post("os/action/time/ntp", json.dumps(ntpAction))

    def _configCluster(self):
        logger.info("[Stage %d] Configuring cluster" % self._nextStage())
        if self._clusterConfigured():
            logger.info("  Cluster is already configured")
            return

        # Set up controller cluster
        if (self.configMap[pdesc.PARAM_CLUSTER_OPTION] == pdesc.ROLE_MASTER):
            cconfig = {"seeds": "", 
                       "local-domain-id": 1,
                       "local-node-auth": {"cluster-secret": ""}}
            rest_lib.post("cluster/config", json.dumps(cconfig))
            
        elif (self.configMap[pdesc.PARAM_CLUSTER_OPTION] == pdesc.ROLE_SLAVE):
            mip = self.configMap[pdesc.PARAM_MASTER_IP]
            host = "%s:8443" % mip
            rest_lib.auth(host, password=self.configMap[pdesc.PARAM_MASTER_PASSWORD])

            # Retrieve cluster secret
            secret = \
                json.loads(rest_lib.request("cluster/config/local-node-auth/cluster-secret",
                                            host=host, secure=True))
            if (len(secret) == 0):
                raise Exception("Could not retrieve cluster secret from %s" % mip)
            secret = secret[0]

            # Join cluster
            cconfig = {"seeds": "%s:6642" % mip,
                       "local-domain-id": 1,
                       "local-node-auth": {"cluster-secret": secret}}
            
            rest_lib.post("cluster/config", json.dumps(cconfig))
            
        if self._waitForCluster():
            self._displayCluster()
        else:
            raise Exception("Failed to configure clustering: %s" % self.clusterError)

    def _configAdminUser(self):
        setShell = {"user-name": "admin",
                    "shell": "cli"}
        rest_lib.post("os/action/system-user/set-shell", json.dumps(setShell))

    # init sets up basic networking and system settings
    # changes made during init are relatively trivial to overwrite
    # exceptions thrown in init are recoverable within firstboot
    def runInit(self):
        self.stage = 0
        self._initSystem()

    # config commits permanent db and controller changes
    # once we start config we are past the point of no return (for now)
    # exceptions thrown in config are unrecoverable within firstboot
    def runConfig(self):
        self._configController()
        self._checkCluster()
        self._configCluster()
        self._configAdminUser()

    def _waitForNetwork(self, *args, **kwargs):
        return waitFor(self._networkConfigured, *args, **kwargs)

    def _networkConfigured(self, iface='eth0'):
        if (iface not in netifaces.interfaces()):
            return False
        addrs = netifaces.ifaddresses(iface)
        if netifaces.AF_INET in addrs:
            for addr in addrs[netifaces.AF_INET]:
                a = ipaddr.IPv4Address(addr['addr'])
                if (not a.is_link_local and not a.is_loopback):
                    self.ipAddr = a
                    return True
        if netifaces.AF_INET6 in addrs:
            pattern = re.compile(r"^([\w:]+)")
            for addr in addrs[netifaces.AF_INET6]:
                m = pattern.search(addr['addr'])
                if not m:
                    continue
                a = ipaddr.IPv6Address(m.group(1))
                if (not a.is_link_local and not a.is_loopback):
                    self.ipAddr = a
                    return True
        return False

    def _waitForCluster(self, *args, **kwargs):
        return waitFor(self._clusterConfigured, *args, **kwargs)

    def _getClusterStatus(self):
        cs = json.loads(rest_lib.get("cluster/status"))
        if (len(cs) > 0):
            self.clusterStatus = cs[0]
        else:
            self.clusterStatus = None

    def _clusterConfigured(self):
        self._getClusterStatus()
        if (self.clusterStatus == None):
            self.clusterError = "Could not retrieve cluster status"
            return False
        local_node_id = self.clusterStatus['local-node-id']
        for node in self.clusterStatus['nodes']:
            if (node['node-id'] == local_node_id and 
                node['hostname'] != "localhost"):
                return True
        self.clusterError = "Could not retrieve local node ID"
        return False

    def _displayCluster(self):
        logger.info("  Cluster configured successfully.")
        logger.info("  Current node ID is %d" % 
                    (self.clusterStatus['local-node-id']))
        logger.info("  All cluster nodes:")
        for node in self.clusterStatus['nodes']:
            logger.info("    Node %d: %s:%d" % 
                        (node['node-id'], node['hostname'], node['port']))

def main():
    cMap = {
                pdesc.PARAM_IP_MODE         : pdesc.IP_STATIC,
                pdesc.PARAM_HOSTNAME        : "firstboot-slave",
                pdesc.PARAM_IP_ADDRESS      : "192.168.116.149",
                pdesc.PARAM_IP_NETMASK      : "255.255.255.0",
                pdesc.PARAM_IP_GATEWAY      : "192.168.116.2",
                pdesc.PARAM_DNS             : "192.168.116.2",
                pdesc.PARAM_DOMAIN          : "localdomain",
                pdesc.PARAM_CLUSTER_OPTION  : pdesc.ROLE_SLAVE,
                pdesc.PARAM_MASTER_IP       : "192.168.116.148",
                pdesc.PARAM_MASTER_PASSWORD : "adminadmin",
           }

    c = Config(cMap)
    c.runInit()
    c.runConfig()

if __name__ == "__main__":
    main()
