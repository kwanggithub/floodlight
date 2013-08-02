#!/usr/bin/env python

import param
import pdesc
import util
import traceback
import pprint
import sys
import time
import os

ACTION_APPLY = "Apply settings"
ACTION_RESET = "Reset and start over"
ACTION_UPDATE = "Update"

CONFIRM_YES = "Yes"
CONFIRM_NO = "No"

class Collect(object):
    def __init__(self, pDesc):
        self.pDesc = pDesc
        self.pMap = {}
        self.groups = []
        self.pRootMap = {}

    def __parseParams(self):
        # 1st pass: create a map
        for d in self.pDesc:
            try:
                name = d["name"]
                if name in self.pMap:
                    raise Exception("Duplicated parameter name: %s" % name)

                class_ = eval("param.%s" % d["class"])
                initArgs = d["init-args"]
                p = class_(*(d[arg] for arg in initArgs))
                if "default" in d:
                    p.setDefault(d["default"])
                self.pMap[name] = p
            except:
                print "Failed to parse description:"
                pprint.pprint(d)
                traceback.print_exc()
                raise

        # 2nd pass: process groups, sub-parameters and references
        for d in self.pDesc:
            try:
                p = self.pMap[d["name"]]

                if "group" in d:
                    if "parent" in d:
                        raise Exception("Parameter must not have both 'group' and 'parent'")

                    group = d["group"]
                    if group not in self.groups:
                        self.groups.append(group)
                        self.pRootMap[group] = []
                    self.pRootMap[group].append(p)

                elif "parent" in d:
                    parent = self.pMap[d["parent"]]
                    value = d["parent-value"]
                    parent.addSubParameter(value, p)

                else:
                    raise Exception("Parameter must have one of 'group' or 'parent'")

                for ref in d.get("refs", []):
                    pRef = self.pMap[ref]
                    p.addReference(ref, pRef)
            except:
                print "Failed to process groups and sub-parameters:"
                pprint.pprint(d)
                traceback.print_exc()
                raise

    def __collectParams(self):
        for _, p in self.pMap.iteritems():
            p.reset()

        for g in self.groups:
            pList = reduce(lambda x, y: x + y.getOrderedParamList(), self.pRootMap[g], [])
            if len(pList) == 0:
                continue

            print "\n%s\n%s\n" % (g, "-"*len(g))
            for p in pList:
                p.doPrompt(showName=False)

    def __getOrderedParamList(self):
        pRootList = reduce(lambda x, y: x + self.pRootMap[y], self.groups, [])
        return reduce(lambda x, y: x + y.getOrderedParamList(), pRootList, [])

    def __actionMenu(self):
        name = "Menu"
        prompt = "Please choose an option"
        choiceApply = {"label": ACTION_APPLY, "value": ACTION_APPLY,}
        choiceReset = {"label": ACTION_RESET, "value": ACTION_RESET,}
        while True:
            params = self.__getOrderedParamList()
            longest = -1
            ready = True
            for p in params:
                if len(p.getName()) > longest:
                    longest = len(p.getName())
                if not p.isSet() or p.detectConflict():
                    ready = False

            choices = ([choiceApply] if ready else []) + [choiceReset]
            for p in params:
                c = p.getConflict()
                l = "%s %s   (%s%s)" % (ACTION_UPDATE, p.getName().ljust(longest),
                                        p.getDisplayValue(),
                                        ", %s" % c if c is not None else "")
                v = p.getName()
                choices.append({"label": l, "value": v,})
            actionMenu = param.ChoiceParameter(name, prompt, choices)
            if ready:
                actionMenu.setDefault(ACTION_APPLY)
            actionMenu.doPrompt()

            action = actionMenu.getValue()
            if action not in [ACTION_APPLY, ACTION_RESET]:
                # action is a parameter name
                p = self.pMap[action]
                p.reset()
                p.doPrompt()
                continue

            return action

    def getConfigMap(self):
        m = dict([(p.getName(), p.getValue()) for p in self.__getOrderedParamList()])

        ip = m[pdesc.PARAM_IP_ADDRESS]

        # config expects the ip mode param
        m[pdesc.PARAM_IP_MODE] = pdesc.IP_DHCP if ip == pdesc.IP_DHCP else pdesc.IP_STATIC

        # config expects the netmask param and ip not in cidr notation
        if ip != pdesc.IP_DHCP and "/" in ip:
            ip2, cidr = ip.split("/")
            netmask = util.intToIp(util.cidrToNetmaskInt(int(cidr)))
            m[pdesc.PARAM_IP_ADDRESS] = ip2
            m[pdesc.PARAM_IP_NETMASK] = netmask

        # blank out ntp if special case - no ntp
        if pdesc.PARAM_NTP in m and m[pdesc.PARAM_NTP] == pdesc.NO_NTP:
            m[pdesc.PARAM_NTP] = ""

        return m

    def runInit(self):
        self.__parseParams()

    def runCollect(self, reset=True):
        action = ACTION_RESET if reset else ""
        while action != ACTION_APPLY:
            if action == ACTION_RESET:
                self.__collectParams()
            action = self.__actionMenu()

def main():
    c = Collect(pdesc.PARAMETERS)
    c.runInit()
    c.runCollect()
    pprint.pprint(c.getConfigMap())

if __name__ == "__main__":
    main()
