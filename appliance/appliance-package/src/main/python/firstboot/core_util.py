#!/usr/bin/env python

from util import run

import env
import time
import json
import re
import log_util

def getMemoryInMb():
    out = run("dmidecode -t memory | grep 'Size:'", True)

    # NOTE: support KB, MB and GB
    kb = re.compile("^\t*Size: (\d+) KB")
    mb = re.compile("^\t*Size: (\d+) MB")
    gb = re.compile("^\t*Size: (\d+) GB")

    total = 0
    for line in out.split("\n"):
        m = kb.search(line)
        if m:
            total += int(m.group(1))/1024
            continue

        m = mb.search(line)
        if m:
            total += int(m.group(1))
            continue

        m = gb.search(line)
        if m:
            total += int(m.group(1))*1024

    return total

def getNumCpuCores():
    return int(run("cat /proc/cpuinfo | grep processor | wc -l", True))
