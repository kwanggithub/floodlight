#!/bin/bash

function output {
    logger -t upgrade $1
    echo $1
}

set -e -o pipefail

UPGRADE_PKG=$1
shift
[ -f ${UPGRADE_PKG} ] || { output "${UPGRADE_PKG} not found"; exit 1; }

FORCE=
FEATURE=bvs
while test $# -gt 0; do
    arg=$1; shift
    case "$arg" in
        --force)
            FORCE=1
        ;;
        --feature)
            FEATURE=$1
            shift
        ;;
    esac
done

output "Checking for appropriate VM resources"
set +e
unzip -p ${UPGRADE_PKG} check | /bin/bash
CHECK_FAILED=$?

if [ ${CHECK_FAILED} != 0 ]; then
    if ! [ "${FORCE}" ]; then
	exit ${CHECK_FAILED}
    fi
    output "Continuing despite errors because of force"
fi
set -e

# add default features IF a features directory does not exist
# see bigcon/controller/oswrapper.py, bigcon/controller/config.py, bigcli/desc/bigdb/core.py
if ! [ -d /opt/bigswitch/feature ] ; then
    mkdir -p /opt/bigswitch/feature
    case "$FEATURE" in
        bigtap)
            touch /opt/bigswitch/feature/bigtapconfig
        ;;
        flow-pusher)
            touch /opt/bigswitch/feature/staticflowonlyconfig
        ;;
        *)
            touch /opt/bigswitch/feature/$FEATURE
        ;;
    esac
fi
