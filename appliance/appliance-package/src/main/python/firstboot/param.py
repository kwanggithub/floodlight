#!/usr/bin/env python

import check
import getpass
import util
import readline
import pdesc
import traceback
import env
import os
import sys

INTERRUPT_RESUME = "resume"
INTERRUPT_RESET = "reset"
INTERRUPT_DEBUG = "debug"

def colorRed(s):
    return "\033[31m%s\033[0m" % s

def getIpNetmask(ipParam, netmaskParam):
    ipValue = None
    netmaskValue = None
    if ipParam.isSet():
        v = ipParam.getValue()
        if "/" in v:
            ipValue, cidr = v.split("/")
            netmaskValue = util.intToIp(util.cidrToNetmaskInt(int(cidr)))
        else:
            ipValue = v
            if netmaskParam.isSet():
                netmaskValue = netmaskParam.getValue()
    return (ipValue, netmaskValue)

def testConflict(ip1Param, ip2Param, netmaskParam, testSubnet=True):
    ipValue, netmaskValue = getIpNetmask(ip2Param, netmaskParam)
    if ipValue is not None:
        if ipValue == ip1Param.getValue():
            return colorRed("SAME AS %s" % ip2Param.getName())

        if testSubnet and netmaskValue is not None:
            if not check.checkIpsInSameSubnet(ipValue, ip1Param.getValue(), netmaskValue):
                netmaskInt = util.ipToInt(netmaskValue)
                ipPrefix = util.intToIp(util.ipToInt(ipValue) & netmaskInt)
                cidr = util.netmaskToCidr(netmaskValue)
                return colorRed("NOT IN SUBNET OF %s/%s" % (ipPrefix, cidr))
    return None

class Parameter(object):
    def __init__(self, name, prompt):
        # constant states
        self.name = name
        self.prompt = prompt
        self.subparams = {}
        self.default = None
        self.refs = {}

        # runtime states
        self.value = None
        self.lastError = None
        self.conflict = None

    def isSet(self):
        return self.value is not None

    def reset(self):
        self.value = None
        self.lastError = None
        self.conflict = None

    def getName(self):
        return self.name

    def getValue(self):
        return self.value

    def setValue(self, value):
        self.value = value

    def getDisplayValue(self):
        if self.value == "":
            return "<none>"
        if self.value == None:
            return colorRed("<NOT SET, UPDATE REQUIRED>")
        return self.value

    def setDefault(self, default):
        self.default = default

    def getDefault(self):
        return self.default

    def isOptional(self):
        return self.default == ""

    def hasDefault(self):
        return self.default is not None

    def isEnabled(self):
        return True

    def showLastError(self):
        if self.lastError:
            print colorRed("\n!!! Error: %s\n" % self.lastError)
            self.lastError = None

    def addSubParameter(self, value, parameter):
        if value not in self.subparams:
            self.subparams[value] = []
        self.subparams[value].append(parameter)

    def addReference(self, key, parameter):
        self.refs[key] = parameter

    def promptForInput(self):
        prompt_ = self.prompt
        if self.isOptional():
            prompt_ += " (Optional)"
        elif self.hasDefault():
            prompt_ += " [%s]" % self.getDefault()
        prompt_ += " > "
        self.showLastError()
        return raw_input(prompt_).strip()

    def doPrompt(self, showName=True, useInterruptHandler=True):
        if not self.isEnabled():
            return

        while True:
            try:
                if showName:
                    print "\n%s\n%s\n" % (self.name, "-"*len(self.name))

                input = self.promptForInput()
                if len(input) == 0:
                    value = self.handleEmptyInput()
                else:
                    value = self.handleNonEmptyInput(input)

                # if parameter is not optional, value should not be empty
                if not self.isOptional():
                    assert len(value) != 0

                if len(value) != 0:
                    self.validate(value)
                self.setValue(value)
                break
            except Exception as e:
                if env.PARAM_DEBUG:
                    traceback.print_exc()
                self.lastError = e
                continue

        subparams = self.subparams.get(self.value, [])
        for p in subparams:
            p.reset()

        for p in subparams:
            p.doPrompt(showName=showName, useInterruptHandler=useInterruptHandler)

    def handleEmptyInput(self):
        if self.isOptional():
            return ""

        if not self.hasDefault():
            raise Exception("Non-empty input required")
        return self.getDefault()

    def handleNonEmptyInput(self, input):
        return input

    def validate(self, value):
        pass

    def detectConflict(self):
        return False

    def getConflict(self):
        return self.conflict

    def getOrderedParamList(self):
        if not self.isEnabled():
            return []
        initialList = [(self)]
        subparamList = self.subparams.get(self.value, [])
        return reduce(lambda x, y: x + y.getOrderedParamList(), subparamList, initialList)

class ChoiceParameter(Parameter):
    def __init__(self, name, prompt, choices):
        Parameter.__init__(self, name, prompt)
        self.valueToLabelMap = {}
        for c in choices:
            assert "label" in c
            assert "value" in c
            self.valueToLabelMap[c["value"]] = c["label"]
        self.choices = choices

    def isOptional(self):
        return False

    def getDisplayValue(self):
        return self.valueToLabelMap[self.value]

    def promptForInput(self):
        # choice parameters should never be optional
        assert not self.isOptional()

        rjust = 2 if len(self.choices) > 9 else 1
        for n, c in enumerate(self.choices):
            print "[%s] %s" % (str(n + 1).rjust(rjust), c["label"])
        print "\n%s:\n" % self.prompt

        prompt_ = ""
        if self.hasDefault():
            for n, c in enumerate(self.choices):
                if c["value"] == self.getDefault():
                    prompt_ += "[%d] " % (n + 1)
                    break
        prompt_ += "> "
        self.showLastError()
        return raw_input(prompt_).strip()

    def handleNonEmptyInput(self, input):
        try:
            value = int(input)
        except:
            raise Exception("Input should be an integer")

        if value < 1 or value > len(self.choices):
            raise Exception("Input should be between 1 and %d" % len(self.choices))
        return self.choices[value - 1]["value"]

class PasswordParameter(Parameter):
    def promptForInput(self):
        # password parameters should never be optional
        assert not self.isOptional()

        # password parameters should never have defaults
        assert not self.hasDefault()

        prompt_ = "%s > " % self.prompt
        self.showLastError()
        pass1 = getpass.getpass(prompt_).strip()
        if len(pass1) == 0:
            raise Exception("Non-empty password required")

        prompt_ = "Retype %s > " % self.prompt
        pass2 = getpass.getpass(prompt_).strip()

        if pass1 != pass2:
            raise Exception("Retyped password does not match the original")
        return pass1

    def getDisplayValue(self):
        return "*"*len(self.value)

    def validate(self, value):
        Parameter.validate(self, value)

class HostParameter(Parameter):
    def validate(self, value):
        Parameter.validate(self, value)

        if not (check.checkDomain(value) or check.checkIp(value)):
            raise Exception("Invalid host or IP")

class IpParameter(Parameter):
    def validate(self, value):
        Parameter.validate(self, value)

        if not check.checkIp(value):
            raise Exception("Invalid IPv4 address")

class MasterIpParameter(IpParameter):
    def validate(self, value):
        IpParameter.validate(self, value)

    def detectConflict(self):
        self.conflict = None
        if not self.isSet():
            return False

        ip = self.refs[pdesc.PARAM_IP_ADDRESS]
        if not ip.isSet() or ip.getValue() == pdesc.IP_DHCP:
            return False

        netmask = self.refs[pdesc.PARAM_IP_NETMASK]
        self.conflict = testConflict(self, ip, netmask)
        return self.conflict is not None

class LocalIpParameter(Parameter):
    def validate(self, value):
        Parameter.validate(self, value)
        # special case - dhcp
        if value.lower() == pdesc.IP_DHCP.lower():
            return

        if not check.checkIp(value):
            parts = value.split("/")
            if len(parts) != 2:
                raise Exception("Invalid IPv4 or CIDR notation")

            ip, cidr = parts
            if not check.checkIp(ip):
                raise Exception("%s is not a valid IPv4 address" % ip)

            if not check.checkCidr(cidr):
                raise Exception("%s is not a valid subnet mask number" % cidr)

    def setValue(self, value):
        if value.lower() == pdesc.IP_DHCP.lower():
            self.value = pdesc.IP_DHCP
        else:
            self.value = value

class NetmaskParameter(Parameter):
    def validate(self, value):
        Parameter.validate(self, value)

        if not check.checkNetmask(value):
            raise Exception("Invalid IPv4 netmask")

    def isEnabled(self):
        ip = self.refs[pdesc.PARAM_IP_ADDRESS]
        # disable if ip has cidr
        if ip.isSet() and ("/" in ip.getValue() or ip.getValue() == pdesc.IP_DHCP):
            return False
        return True

class GatewayParameter(IpParameter):
    def validate(self, value):
        IpParameter.validate(self, value)

    def detectConflict(self):
        self.conflict = None
        if not self.isSet() or self.value == "":
            return False

        ip = self.refs[pdesc.PARAM_IP_ADDRESS]
        netmask = self.refs[pdesc.PARAM_IP_NETMASK]
        self.conflict = testConflict(self, ip, netmask)
        return self.conflict is not None

    def isEnabled(self):
        ip = self.refs[pdesc.PARAM_IP_ADDRESS]
        # disable if using dhcp

        if ip.isSet() and ip.getValue() == pdesc.IP_DHCP:
            return False
        return True

class DnsParameter(IpParameter):
    def isEnabled(self):
        ip = self.refs[pdesc.PARAM_IP_ADDRESS]
        # disable if using dhcp

        if ip.isSet() and ip.getValue() == pdesc.IP_DHCP:
            return False
        return True

class DomainParameter(Parameter):
    def validate(self, value):
        Parameter.validate(self, value)

        if not check.checkDomain(value):
            raise Exception("Invalid domain")

    def isEnabled(self):
        ip = self.refs[pdesc.PARAM_IP_ADDRESS]
        # disable if using dhcp

        if ip.isSet() and ip.getValue() == pdesc.IP_DHCP:
            return False
        return True

class HostnameParameter(Parameter):
    def validate(self, value):
        Parameter.validate(self, value)

        if not check.checkHostname(value):
            raise Exception("Invalid hostname")

    def isEnabled(self):
        ip = self.refs[pdesc.PARAM_IP_ADDRESS]
        # disable if using dhcp

        if ip.isSet() and ip.getValue() == pdesc.IP_DHCP:
            return False
        return True

class NtpParameter(HostParameter):
    def isEnabled(self):
        clusterOption = self.refs[pdesc.PARAM_CLUSTER_OPTION]
        if clusterOption.isSet() and clusterOption.getValue() == pdesc.ROLE_SLAVE:
            return False
        return True

    def validate(self, value):
        # special case - no ntp
        if value.lower() == pdesc.NO_NTP.lower():
            return

        HostParameter.validate(self, value)

    def setValue(self, value):
        if value.lower() == pdesc.NO_NTP.lower():
            self.value = pdesc.NO_NTP
        else:
            self.value = value
