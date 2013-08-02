#!/usr/bin/python

# Here we import the appropriate parts of vmbuilder to allow us to add
# additional packages from our separate apt source into the chroot with
# the context set up correctly

import optparse
import VMBuilder
import sys
import subprocess
import os
import re
from VMBuilder.plugins.ubuntu.distro import Ubuntu
from VMBuilder.contrib.cli import CLI

if (not 'SUITE' in os.environ):
    raise Exception("SUITE is unset")
if (not 'ARCH' in os.environ):
    raise Exception("ARCH is unset")
if (not 'RELEASE' in os.environ):
    raise Exception("RELEASE is unset")
if len(sys.argv) < 2:
    raise Exception("Missing chroot argument")

suite = os.environ['SUITE']
arch = os.environ['ARCH']
release = os.environ['RELEASE']
chroot = sys.argv[1]

# Set up the distro objects using the vmbuilder infrastructure
cli = CLI()
optparser = optparse.OptionParser()
cli.set_usage(optparser)
optparser.disable_interspersed_args()
argv = ['kvm', 'ubuntu', "--suite", suite, '--arch', arch]
(dummy, args) = optparser.parse_args(argv)
optparser.enable_interspersed_args()

hypervisor, distro = cli.handle_args(optparser, args)

cli.add_settings_from_context(optparser, distro)
cli.add_settings_from_context(optparser, hypervisor)

# Mount the filesystem and prepare to run apt
distro.preflight_check()
distro.context.chroot_dir = chroot
distro.suite.prevent_daemons_starting()
distro.suite.mount_dev_proc()
distro.context.template_dirs += [os.path.dirname(__file__) + '/templates/%s']

# Install appliance release version file
distro.suite.install_from_template('/etc/floodlight-release', 
                                   'floodlight-release', {'release': release})

# Install a new fstab file that references the rootfs by UUID rather
# than by device.  This makes it so it can boot cleanly in a wider
# range of hypervisor environments
with open('/proc/mounts', 'r') as mounts:
    for line in mounts:
        vals = line.split()
        if (vals[1] == chroot):
            device = vals[0]
            break
blkid = subprocess.check_output(['blkid', device]).split()
keyval = re.compile(r'(\w+)="([\w-]+)"')
for val in blkid:
    m = keyval.match(val)
    if m and m.group(1) == "UUID":
        uuid = m.group(2)
        break

distro.suite.install_from_template('/etc/fstab', 'fstab', {'uuid': uuid})

# Nuke grub and install extlinux, which will be chainloaded from an 'outer'
# boot loader
cmd = ['apt-get', 'remove', '--purge', '-y', 'grub', 'grub-common']
distro.suite.run_in_target(env={ 'DEBIAN_FRONTEND' : 'noninteractive' }, *cmd)
cmd = ['apt-get', 'install', '-y', 'extlinux']
distro.suite.run_in_target(env={ 'DEBIAN_FRONTEND' : 'noninteractive' }, *cmd)
distro.suite.run_in_target('/bin/rm', '-rf', '/boot/grub')
distro.suite.install_from_template('/boot/extlinux/extlinux.conf', 'extlinux.conf', 
                                   {'uuid': uuid})
distro.suite.run_in_target('/bin/dd', 'bs=440', 'conv=notrunc', 
                           'count=1', 'if=/usr/lib/extlinux/mbr.bin', 
                           'of=%s' % (device))
distro.suite.run_in_target('extlinux', '--install', '/boot/extlinux')

# Install our extra packages using apt
distro.suite.install_from_template('/etc/apt/sources.list.d/vmbuild.list', 
                                   'vmbuild.list',
                                   {'local_mirror': 'http://localhost:9999/debian',
                                    'suite': suite})

distro.suite.run_in_target('apt-get', 'update')
cmd = ['apt-get', 'install', '-y', 
       '--force-yes', '--allow-unauthenticated']
addpkg = ['floodlight-appliance', 
          'floodlight-' + os.environ['FLAVOR']]
cmd += addpkg
distro.suite.run_in_target(env={ 'DEBIAN_FRONTEND' : 'noninteractive' }, *cmd)

# Tear down the system
distro.suite.unmount_volatile()
distro.suite.unmount_proc()
distro.suite.unmount_dev_pts()
distro.suite.unmount_dev()
distro.suite.unprevent_daemons_starting()
distro.suite.run_in_target('apt-get', 'clean');

