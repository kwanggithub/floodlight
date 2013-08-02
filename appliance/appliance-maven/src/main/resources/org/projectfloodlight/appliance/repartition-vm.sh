#!/bin/bash
set -exu

# This script extracts the root filesystem from the image created by
# vmbuilder.  It then uses this to build a new image with the disk
# layout we want for the controller image.   The root file system image
# is also used for generating an upgrade package

: ${IMAGEBASE?is unset}
: ${UPGRADEIMAGE?is unset}
: ${IMAGEFINALRAW?is unset}
: ${RELEASE?is unset}

LOOP=
MOUNT=

function cleanup()
{
    if [ "x$MOUNT" != x ]; then
	sudo umount "$MOUNT" || true
	MOUNT=
    fi
    if [ "x$LOOP" != x ]; then
	sudo kpartx -d "$LOOP" || true
	sudo losetup -d "$LOOP" || true
	LOOP=
    fi
}

trap cleanup SIGINT SIGTERM EXIT

# Mount the base image and copy out just the root filesystem
LOOP=$(sudo losetup -f)
sudo losetup "$LOOP" "$IMAGEBASE"
PART=$(sudo kpartx -av "$LOOP" | head -1 | cut -d' ' -f3)

truncate --size 0 "$UPGRADEIMAGE"
sudo ddrescue -q --sparse "/dev/mapper/$PART" "$UPGRADEIMAGE"

sudo kpartx -d "$LOOP"
sudo losetup -d "$LOOP"
LOOP=

# Create the new filesystem

# Create sparse raw image
truncate --size 0 "$IMAGEFINALRAW"
dd if=/dev/zero of="$IMAGEFINALRAW" bs=1024 count=0 seek=$[20*1000*1000] 

# Partition the raw image
LOOP=$(sudo losetup -f)
sudo losetup "$LOOP" "$IMAGEFINALRAW"

sudo parted -s "$LOOP" \
    mklabel msdos \
    unit cyl \
    mkpart primary ext4 0 2 \
    set 1 boot on \
    mkpart primary ext4 2 1003 \
    mkpart primary ext4 1003 2004 \
    mkpart extended 2004 2489 \
    mkpart logical ext4 2004 2247 \
    mkpart logical linux-swap 2247 2489 

# Format the partitions
sudo mkfs.ext4 -q $LOOP"p1"         # sysboot
sudo mkfs.ext4 -L log -q $LOOP"p5"  # log
sudo mkswap -L swap $LOOP"p6"       # swap

# Copy filesystem into rootfs1
sudo ddrescue -q --force "$UPGRADEIMAGE" $LOOP"p2"
sudo /sbin/e2fsck -fy $LOOP"p2"
sudo /sbin/resize2fs $LOOP"p2"

# Install extlinux into the sysboot partition and the MBR.  This will
# chainload the bootloader installed directly into the root filesystem
# partition
mkdir -p "$IMAGEFINALRAW.mnt"
MOUNT=$LOOP"p1"
sudo mount "$MOUNT" "$IMAGEFINALRAW.mnt"
sudo dd conv=notrunc bs=440 count=1 \
    if=/usr/lib/extlinux/mbr.bin of=$LOOP
sudo mkdir -p "$IMAGEFINALRAW.mnt/boot/extlinux"
sudo cp /usr/lib/syslinux/chain.c32 "$IMAGEFINALRAW.mnt/boot/extlinux"
sudo cp extlinux.conf menu.txt "$IMAGEFINALRAW.mnt/boot/extlinux"
sudo sed -i "s/RELEASE/$RELEASE/" "$IMAGEFINALRAW.mnt/boot/extlinux/menu.txt"
sudo extlinux --install "$IMAGEFINALRAW.mnt/boot/extlinux"
sudo umount "$MOUNT"

# Create some directory structure in the log partition
MOUNT=$LOOP"p5"
sudo mount "$MOUNT" "$IMAGEFINALRAW.mnt"
sudo mkdir -p "$IMAGEFINALRAW.mnt/nginx"
sudo umount "$MOUNT"
MOUNT=
rmdir "$IMAGEFINALRAW.mnt"

sudo kpartx -d "$LOOP"
sudo losetup -d "$LOOP"
LOOP=

# That ddrescue into the loop device kills our sparseness.  Uncomment
# this to restore the sparseness in the raw image, which is useful for
# making it easier to handle when debugging, but doesn't matter
# otherwise since we're about to convert to to qcow2 anyway.
#cp --sparse=always "$IMAGEFINALRAW" "$IMAGEFINALRAW.sparse"
#mv "$IMAGEFINALRAW.sparse" "$IMAGEFINALRAW"

