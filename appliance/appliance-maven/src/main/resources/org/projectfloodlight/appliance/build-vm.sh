#!/bin/bash
set -exu

: ${ARCH?is unset}
: ${SUITE?is unset}
: ${FLAVOR?is unset}
: ${TARGET_DIR?is unset}
: ${RELEASE?is unset}
: ${VERSION?is unset}

HOOK_SCRIPT="$(pwd)/vmbuilder-hook.py"

VMTMP=$(mktemp -d --tmpdir vmbuilder.XXXXXX)
HTTP_SERVER_PID=
LOOPBASE=
LOOPIMAGE=

# Hook to clean up on exit
function cleanup()
{
    # Stop the local HTTP server
    if [ "x$HTTP_SERVER_PID" != x ]; then
        kill $HTTP_SERVER_PID
        HTTP_SERVER_PID=
    fi

    # Clean up any device mapper entries we created ourselves
    if [ "x$LOOPBASE" != x ]; then
        sudo losetup -d "$LOOPBASE" || true
    fi
    if [ "x$LOOPIMAGE" != x ]; then
        sudo losetup -d "$LOOPIMAGE" || true
    fi

    # vmbuilder doesn't properly clean up after itself for certain
    # failures.  Attempt to umount loopback volumes
    grep "$VMTMP" /proc/mounts && exitCode=0 || exitCode=$?
    if [ $exitCode -eq 0 ]; then
        grep "$VMTMP" /proc/mounts | cut -d' ' -f2 | sort -r | sudo xargs umount
    fi

    # Attempt to remove device mapper entries created by vmbuilder if
    # it's failed to clean up.  Only remove ones associated with this
    # run
    sudo losetup -a | grep "$VMTMP" && exitCode=0 || exitCode=$?
    if [ $exitCode -eq 0  ]; then
        for i in `sudo losetup -a | grep "$VMTMP" | cut -d' ' -f1 | cut -d: -f1`; do 
            j=`echo "$i" | cut -d/ -f 3`
            sudo losetup -a | grep "/dev/mapper/$j" && exitCode=0 || exitCode=$?
            if [ $exitCode -eq 0  ]; then
                for k in `sudo losetup -a | grep "/dev/mapper/$j" | cut -d' ' -f1 | cut -d: -f1`; do 
                    sudo losetup -d "$k" || true
                    sudo kpartx -d "$k" || true
                done
            fi
            sudo losetup -d "$i" || true
            sudo kpartx -d "$i" || true
        done
    fi

    # Clean up temporary files
    sudo rm -rf "$VMTMP" || true
}

trap cleanup SIGINT SIGTERM EXIT

# Start an HTTP instance to serve the packages we've build locally
# Should add an option to pull from an already-built repo
pushd "${TARGET_DIR}"
python -m SimpleHTTPServer 9999 &
popd
HTTP_SERVER_PID=$!

DEST="$TARGET_DIR/images/$FLAVOR"
if [ -d "$DEST" ]; then
    rm -rf "$DEST"
fi
mkdir -p "$DEST"

# Pick a mirror to use; either local proxy, office network proxy, or
# official Ubuntu mirror
if nc -z localhost 3142; then
    MIRROR='http://localhost:3142/us.archive.ubuntu.com/ubuntu'
elif nc -z apt-proxy.bigswitch.com 3142; then
    MIRROR='http://apt-proxy.bigswitch.com:3142/us.archive.ubuntu.com/ubuntu'
else
    echo "WARNING: Using official Ubuntu mirror.  Build will be slow"
    MIRROR='http://us.archive.ubuntu.com/ubuntu'
fi

IMAGEBASE="$VMTMP/basefs.img"
IMAGEFINALRAW="$VMTMP/controller.img"
UPGRADEIMAGE="$VMTMP/rootfs"

# Create sparse raw image
dd if=/dev/zero of="$IMAGEBASE" bs=1024 count=0 seek=$[2*1000*1000] 

# Run vmbuilder to create the base image
time sudo \
    ARCH="$ARCH" \
    SUITE="$SUITE" \
    FLAVOR="$FLAVOR" \
    RELEASE="$RELEASE" \
    TMPDIR="$VMTMP" \
    PATH="$(dirname "$0"):/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin" \
    vmbuilder kvm ubuntu -v \
    --suite "$SUITE" \
    --arch "$ARCH" \
    --rootsize 2000 --swapsize 512 \
    --hostname controller \
    --tmp "$VMTMP" \
    --execscript "$HOOK_SCRIPT" \
    --mirror "$MIRROR" \
    --dest "$VMTMP/dest" \
    --raw "$IMAGEBASE" \
    --user "recovery" \
    --name "Recovery User" \
    --pass "recovery" \
    --part vmbuilder-partition

# Extract the filesystem created by vmbuilder and rebuild the final
# image
time \
    IMAGEFINALRAW="$IMAGEFINALRAW" \
    UPGRADEIMAGE="$UPGRADEIMAGE" \
    IMAGEBASE="$IMAGEBASE" \
    RELEASE="$RELEASE" \
    ./repartition-vm.sh

# Generate a qcow2 image from the raw image and copy it to the
# destination
CONTROLLER_IMAGE="controller-$FLAVOR-$VERSION.qcow2"
time \
    qemu-img convert -O qcow2 "$IMAGEFINALRAW" "$DEST/$CONTROLLER_IMAGE"

pushd "$DEST"
ln -s "$CONTROLLER_IMAGE" "controller.qcow2"
popd 

# Build the upgrade package
SHOULD_BUILD_UPGRADE=${BUILD_UPGRADE:-}
if [ ! -z ${SHOULD_BUILD_UPGRADE} ]; then
    BASEDIR="$(pwd)/upgrade"
    CONTROLLER_UPGRADE="controller-upgrade-$FLAVOR-$VERSION.pkg"
    UGTMP="$VMTMP/upgrade.tmp"
    mkdir -p "$UGTMP/scripts"
    mv "$UPGRADEIMAGE" "$UGTMP/rootfs"

    find "$BASEDIR/scripts" -perm -u+x -type f -exec cp {} "$UGTMP/scripts" \;
    cp "$BASEDIR/upgrade-controller.py" "$UGTMP/install"
    cp "$BASEDIR/check-vm" "$UGTMP/check"
    cp "$BASEDIR/Manifest" "$UGTMP"
    
    pushd "$UGTMP"
    echo "$RELEASE" > release
    sha1sum `find . -type f` > checksums
    time zip -1 "$DEST/$CONTROLLER_UPGRADE" release check install Manifest checksums scripts/* rootfs
    popd

    pushd "$DEST"
    ln -s "$CONTROLLER_UPGRADE" "controller-upgrade.pkg"
    popd
fi
