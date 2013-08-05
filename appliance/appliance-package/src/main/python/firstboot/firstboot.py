#!/usr/bin/env python

from util import run

import config
import collect
import prestart
import precheck
import pdesc
import log_util
import env
import atexit
import param

import os
import sys
import traceback
import signal
import subprocess

logger = log_util.getMainLogger(__name__)
locked = False

RESET_RESET = "reset"
RESET_DEBUG = "debug"

def createSetupLock():
    global locked
    try:
        run("mkdir %s" % env.SETUP_LOCK)
        locked = True
    except:
        logger.error("!!! First-time setup is already in progress. Exiting...")
        sys.exit(1)

def removeSetupLock():
    global locked
    if locked:
        run("rmdir %s" % env.SETUP_LOCK, ignoreError=True)

def cleanExit():
    removeSetupLock()
    exit(0)

def interruptHandler(signal, frame):
    print "Interrupted"
    cleanExit()

def enableSigIntTrap():
    signal.signal(signal.SIGINT, signal.SIG_IGN)

def disableSigIntTrap():
    signal.signal(signal.SIGINT, interruptHandler)

def normalErrorHandler(e):
    logger.error("!!! The following error occurred during initialization:")
    logger.error("!!!")
    logger.error("!!! %s" % str(e))
    logger.error("!!! Please correct corresponding parameters and retry.")
    rc = subprocess.call(['/bin/bash'])

def enterToContinue():
    raw_input("Press enter to continue > ")

def fatalErrorHandler(e):
    logger.error("!!! Unrecoverable fatal error occurred:")
    logger.error("!!!")
    logger.error("!!! %s" % str(e))
    traceback.print_exc()
    for l in traceback.format_exc().split("\n"):
        logger.debug(l)

    enterToContinue()
    cleanExit()

def main():
    disableSigIntTrap()
    createSetupLock()

    try:
        # System could reboot if user declines EULA during prestart
        prestart_ = prestart.PreStart()
        prestart_.run()

        # Perform system precheck
        enableSigIntTrap()
        precheck_ = precheck.PreCheck()
        precheck_.run()
        disableSigIntTrap()

        logger.info("\nStarting first-time setup\n")
        firstRun = True
        collect_ = collect.Collect(pdesc.PARAMETERS)
        collect_.runInit()
        while True:
            try:
                if firstRun:
                    firstRun = False
                    collect_.runCollect(reset=True)
                else:
                    collect_.runCollect(reset=False)

                enableSigIntTrap()
                cMap = collect_.getConfigMap()
                config_ = config.Config(cMap)
                config_.runInit()
                config_.runConfig()
                break
            except Exception as e:
                normalErrorHandler(e)
            finally:
                disableSigIntTrap()

    except KeyboardInterrupt as e:
        print "Interrupted"
    except Exception as e:
        fatalErrorHandler(e)
    finally:
        removeSetupLock()

    logger.info("\nFirst-time setup is complete!\n")
    enterToContinue()

if __name__ == "__main__":
    main()
