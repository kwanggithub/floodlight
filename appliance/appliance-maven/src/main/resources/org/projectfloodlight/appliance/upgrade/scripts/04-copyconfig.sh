#!/bin/bash

function output {
    logger -t upgrade $1
    echo $1
}

set -x -e -o pipefail

output "Backing up configuration"

FORCE=
img=
user=
pw=
while test $# -gt 0; do
    arg=$1; shift
    case "$arg" in
        --force)
            FORCE=1
        ;;
        --user)
            user=$1
            shift
        ;;
        --pass)
            pw=$1
            shift
        ;;
        --feature)
            shift
        ;;
        -*) ;;
        *)
            img=$arg
        ;;
    esac
done

if test -z "$user"; then
  output "ERROR: missing user"
  exit 2
fi
if test -z "$pw"; then
  output "ERROR: missing password"
  exit 2
fi

export PATH=/sbin:/usr/sbin:$PATH
NETADDR=$(ifconfig eth0 | grep 'inet addr:' | cut -f2 -d: | cut -f1 -d' ')
for ip in `/opt/bigswitch/sys/bin/get_upgraded_nodes localhost`; do
    if [ $ip == $NETADDR ]; then
        output "node already upgraded"
        exit 2
    fi
    exit 0 
done

# If we are doing an upgrade from older version with no BVS support,
# then remove BVS feature
# (i.e. 'show bvs' is an error or is an unrecoganized option),
configtype="boot-config"
if [ -f /opt/bigswitch/run/boot-config ]; then
    configtype=`grep "^boot-config=" /opt/bigswitch/run/boot-config | head -1 | cut -s -d= -f2`
fi

mkdir -p /opt/bigswitch/run

if [ "$configtype"x != "boot-config"x ]; then
    MASTERIP=`/opt/bigswitch/sys/bin/get_master_node`

    # Q: why is bvs getting tested here? is this for bigtap?
    # note: need to add reauth, $2 is user name, $3 is the password
    if ((echo connect $MASTERIP; echo reauth $user $pw; echo show bvs) | /opt/bigswitch/cli/bin/bigcli --init | egrep '^(Unrecognized show option:|Error:)') 1>/dev/null 2>&1 ; then
        rm -f /opt/bigswitch/feature/bvs
    fi

    output "Saving current controller configuration for upgrade ..."
    PROMPT="${MASTERIP}:80>"
    SHOW_OUTPUT=`mktemp`
    output "tempfile = $SHOW_OUTPUT"
    (echo connect $MASTERIP; echo show running-config) | /opt/bigswitch/cli/bin/bigcli --init > $SHOW_OUTPUT

    # Chop off header and trailer from output of show running config.
    # The output of bigcli --init could vary between versions.
    # The following awk script should maintain backward compatibility.
    # The bigcli prompt format has changed in newer versions. See BSC-3933.
    awk -v prompt="${PROMPT}" '
        BEGIN {
            f="/dev/null";
        }
        ($1 == "command:" || $1 == prompt) && $2 != "show" {
            f="/dev/null"; next;
        }
        ($1 == "command:" || $1 == prompt) && $2 == "show" && $3 == "running-config" {
            f="/opt/bigswitch/run/upgrade-config"; next;
        }
        $1 == "show" && $2 == "running-config" {
            next;
        }
        $1 == "firewall" && $2 == "allow" && ($NF == "6642" || $NF == "7000" || $NF == "vrrp" ) {
            next;
        }
        $1 == "Exiting." {
            next;
        }
        {print > f;}
    ' $SHOW_OUTPUT

    rm -f $SHOW_OUTPUT
    # Check if the current running-config was saved successfully
    # LOOK! If first line is '!', we assume that the save command worked as expected. Need better Error response!
    SAVED_CONFIG_FIRST_LINE=$(head -1 /opt/bigswitch/run/upgrade-config)
    if [ "${SAVED_CONFIG_FIRST_LINE}" != '!' ]; then
        mv /opt/bigswitch/run/upgrade-config /opt/bigswitch/run/upgrade-config-err
        output "ERROR: could not save running-config!"
        if ! [ "${FORCE}" ]; then
            exit 2
        fi
    output "Continuing despite errors because of force"
    fi
fi
