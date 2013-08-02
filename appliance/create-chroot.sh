#!/bin/bash
set -exu
: ${SUITE? is unset}
: ${ARCH? is unset}

EXTRA_PACKAGES="debhelper libssl-dev pkg-config autoconf automake openssl module-assistant python libpcap0.8 libpcap0.8-dev"
BASEPATH="/var/cache/pbuilder/${SUITE}-${ARCH}"

if [ -d "$BASEPATH" ]; then
    echo "chroot already exists in $BASEPATH"
    exit 0
fi

sudo mkdir -p /var/cache/pbuilder
sudo cowbuilder --create \
                --basepath ${BASEPATH} \
                --distribution ${SUITE} \
                --debootstrapopts --arch --debootstrapopts ${ARCH} \
                --components "main universe"

# Debian bug #606542: can't specify --extrapackages with --create
sudo cowbuilder --update \
                --basepath ${BASEPATH} \
                --extrapackages "${EXTRA_PACKAGES}"
