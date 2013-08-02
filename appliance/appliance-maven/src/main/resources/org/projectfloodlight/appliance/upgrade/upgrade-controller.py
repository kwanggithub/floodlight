#!/usr/bin/python

import json
import sys
import os, sys
import subprocess
import tempfile
import stat
import getpass

args = list(sys.argv[1:])
otherArgs = []
img = None
user = None
pw = None
force = False
while args:
    arg = args.pop(0)

    if arg == '--force':
        force = True
        continue

    if arg == '--user':
        user = args.pop(0)
        continue

    if arg == '--pass':
        pw = args.pop(0)
        continue

    if img is None:
        img = arg
        continue

    otherArgs.append(arg)

if img is None:
    raise SystemExit("missing image")

if user is None:
    try:
        user = raw_input("User for admin account (usually 'admin'): ")
    except EOFError:
        sys.exit(1)

if pw is None:
    try:
        pw = getpass.getpass("Password for admin account: ")
    except EOFError:
        sys.exit(1)
    except getpass.GetPassWarning:
        print "cannot get secret!"
        sys.exit(1)

manifest = json.loads(subprocess.check_output(["unzip", "-p", sys.argv[1], "Manifest"]))

for step in manifest:
    print "  %s - %s" % (step['step'], step['description'])
    stepScript = tempfile.NamedTemporaryFile(delete=False)
    try:
        scriptName = "scripts/%s" % step['action'].strip()

        step = subprocess.check_output(["unzip", "-p", sys.argv[1], scriptName])
        stepScript.write(step)
        stepScript.flush()
        stepScript.close()
        os.chmod(stepScript.name, stat.S_IXUSR | stat.S_IWUSR | stat.S_IRUSR)

        # ha ha, '--force' always
        cmd = [stepScript.name, sys.argv[1],]
        cmd.append('--force')
        cmd.extend(['--user', user])
        cmd.extend(['--pass', pw])
        cmd.extend(otherArgs)
        print subprocess.check_output(cmd, stderr=subprocess.STDOUT)
    except subprocess.CalledProcessError, e:
        print "Error running %s\nreturn code %s\nOutput:\n%s" % \
            (e.cmd, e.returncode, e.output)
        sys.exit(1)
    finally:
        os.unlink(stepScript.name)
