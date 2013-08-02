#!/usr/bin/env python

import logging
import logging.handlers
import env

l1 = logging.getLogger("main")
l1.setLevel(logging.DEBUG)

console = logging.StreamHandler()
consoleFormatter = logging.Formatter("%(message)s")
console.setLevel(logging.DEBUG if env.CONSOLE_DEBUG else logging.INFO)
console.setFormatter(consoleFormatter)

syslog = logging.handlers.SysLogHandler(address="/dev/log")
syslogFormatter = logging.Formatter("firstboot: %(levelname)s [%(name)s] %(message)s")
syslog.setLevel(logging.DEBUG)
syslog.setFormatter(syslogFormatter)

l1.addHandler(console)
l1.addHandler(syslog)

l2 = logging.getLogger("module")
l2.setLevel(logging.DEBUG)

mConsole = logging.StreamHandler()
mConsoleFormatter = logging.Formatter("   * %(message)s")
mConsole.setLevel(logging.DEBUG if env.CONSOLE_DEBUG else logging.INFO)
mConsole.setFormatter(mConsoleFormatter)

mSyslog = logging.handlers.SysLogHandler(address="/dev/log")
mSyslog.setLevel(logging.DEBUG)
mSyslog.setFormatter(syslogFormatter)

l2.addHandler(mConsole)
l2.addHandler(mSyslog)

def getMainLogger(name):
    return logging.getLogger("main." + name)

def getModuleLogger(name):
    return logging.getLogger("module." + name)
