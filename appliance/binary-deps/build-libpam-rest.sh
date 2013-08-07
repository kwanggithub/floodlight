#!/bin/bash

set -e
set -x

: ${PKG? is unset}
: ${VER? is unset}
: ${SUITE? is unset}
: ${ARCH? is unset}
SRC=${1?missing source directory argument}
DST=${2?missing source directory argument}

DEB_NAME="${PKG}_${VER}_${ARCH}"

if [[ -f "$DST/$DEB_NAME.deb" && \
	"$DST/$DEB_NAME.deb" -nt "$SRC/Makefile.am" && \
	"$DST/$DEB_NAME.deb" -nt "$SRC/pam_rest.c" \
    ]]; then
    echo "$DST/$DEB_NAME.deb already exists"
    exit 0
fi

BASEPATH="/var/cache/pbuilder/${SUITE}-${ARCH}"

workdir=`/bin/mktemp -d /tmp/build-XXXXXX` || exit 1
cowdir=`/bin/mktemp -u /var/cache/pbuilder/build/base.XXXXXX`
do_cleanup()
{
  /bin/rm -fr "$workdir"
  sudo /bin/rm -fr "$cowdir"
}
trap "do_cleanup" 0 1 EXIT

cp -T -r "$SRC" "$workdir/build"
sed -i "s/__VERSION__/${VER}/" "$workdir/build/debian/control"
sed -i "s/__VERSION__/${VER}/" "$workdir/build/debian/changelog"

pushd "$workdir/build"
dpkg-buildpackage -S -d -us -uc
popd

sudo /bin/cp -al "$BASEPATH" "$cowdir"
sudo /bin/rm -rf "$cowdir/etc/pam.d"
sudo /bin/cp -a "$BASEPATH/etc/pam.d" "$cowdir/etc"
sudo /bin/chmod 0777 "$cowdir/etc/pam.d"

sudo cowbuilder --build $workdir/*.dsc --basepath "$cowdir" --buildresult "$workdir" --debbuildopts -b

mkdir -p $DST
cp "$workdir/$DEB_NAME.deb" "$DST/."
