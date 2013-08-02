#!/usr/bin/env python

import param
import env
import core_util
import log_util

import os
import time

ANSWER_YES = "yes"
ANSWER_NO = "no"
ANSWER_VIEW = "view"

class PreStart(object):
    def __init__(self):
        self.eulaParam = None
        self.recoveryPasswordParam = None
        self.__setParams()

    def __setParams(self):
        name = "RECOVERY_PASSWORD_OPTION"
        prompt = "Re-use admin password? (Yes/No)"
        self.recoveryPasswordOptionParam = param.Parameter(name, prompt)
        self.recoveryPasswordOptionParam.setDefault("Yes")

        name = "RECOVERY_PASSWORD"
        prompt = "Recovery Password"
        self.recoveryPasswordParam = param.PasswordParameter(name, prompt)

        name = "EULA_OPTION"
        prompt = "Do you accept the EULA for this product? (Yes/No/View)"
        self.eulaParam = param.Parameter(name, prompt)
        self.eulaParam.setDefault("Yes")

    def __displayWelcome(self):
        path = os.path.join(os.path.dirname(__file__), env.TEXT_WELCOME)
        os.system("cat %s" % path)

    def __displayEula(self):
        path = os.path.join(os.path.dirname(__file__), env.TEXT_EULA)
        os.system("less -P '%s' %s" % (env.LESS_PROMPT, path))

    def __doEulaPrompt(self):
        print ""
        while True:
            self.eulaParam.doPrompt(showName=False)
            value = self.eulaParam.getValue()

            if value.lower() not in [ANSWER_YES, ANSWER_NO, ANSWER_VIEW]:
                print "'%s' is not a valid answer. Please try again.\n" % value
                continue
            return

    def __doDecline(self):
        print "\nYou have declined the EULA."

    def __setRecoveryPassword(self):
        print "The 'recovery' user account can be used access the system in case of emergency.\n"
        print "You can either re-use the admin password,"
        print "or set a different password for the 'recovery' account.\n"

        while True:
            self.recoveryPasswordOptionParam.doPrompt(showName=False, useInterruptHandler=False)
            value = self.recoveryPasswordOptionParam.getValue()

            if value.lower() == ANSWER_YES:
                print "Re-using the admin password for the 'recovery' account.\n"
                core_util.clonePassword("admin", "recovery")
                break

            if value.lower() == ANSWER_NO:
                print "Please set a new password for the 'recovery' account.\n"
                self.recoveryPasswordParam.doPrompt(showName=False, useInterruptHandler=False)
                core_util.changePassword("recovery", self.recoveryPasswordParam.getValue())
                break

    def run(self):
        os.system("clear")
        #self.__setRecoveryPassword()

        answer = None
        while answer != ANSWER_YES:
            os.system("clear")
            self.__displayWelcome()
            self.__doEulaPrompt()
            answer = self.eulaParam.getValue().lower()

            if answer == ANSWER_NO:
                self.__doDecline()
                raise Exception("EULA declined")

            if answer == ANSWER_VIEW:
                self.__displayEula()

def main():
    ps = PreStart()
    ps.run()

if __name__ == "__main__":
    main()
