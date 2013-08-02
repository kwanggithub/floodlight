#!/usr/bin/env python

import log_util
import core_util
import env
import netifaces

logger = log_util.getMainLogger(__name__)

def colorRed(s):
    return "\033[31m%s\033[0m" % s

class PreCheck(object):
    def __init__(self):
        pass

    def __checkNic(self, interface="eth0"):
        if "eth0" not in netifaces.interfaces():
            raise Exception("Network interface %s is not found" % interface)
        logger.info("Found %s" % interface)

    def __checkCpu(self):
        numCores = core_util.getNumCpuCores()
        logger.info("Found %d CPU cores" % numCores)
        if numCores < env.MIN_CPU_CORES:
            w = "Warning: Minimum recommendation is %d CPU cores" % env.MIN_CPU_CORES
            logger.warning(colorRed(w))

    def __checkMemory(self):
        memInMb = core_util.getMemoryInMb()
        logger.info("Found %d MB of memory" % memInMb)
        if memInMb < env.MIN_MEM_IN_MB:
            w = "Warning: Minimum recommendation is %d MB of memory" % env.MIN_MEM_IN_MB
            logger.warning(colorRed(w))

    def run(self):
        logger.info("\nRunning system pre-check\n")
        self.__checkNic()
        self.__checkCpu()
        self.__checkMemory()
        logger.info("\nFinished system pre-check\n")

def main():
    chk = PreCheck()
    chk.run()

if __name__ == "__main__":
    main()
