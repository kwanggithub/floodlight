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

if [ -f "$DST/$DEB_NAME.deb" ]; then
    echo "$DST/$DEB_NAME.deb already exists"
    exit 0
fi

BASEPATH="/var/cache/pbuilder/${SUITE}-${ARCH}"

workdir=`/bin/mktemp -d /tmp/build-XXXXXX`
do_cleanup()
{
  rm -fr "$workdir"
}
trap "do_cleanup" 0 1

cp -T -r "$SRC" "$workdir/build"
sed -i "s/__VERSION__/${VER}/" "$workdir/build/debian/control"
sed -i "s/__VERSION__/${VER}/" "$workdir/build/debian/changelog"

pushd "$workdir/build"
dpkg-buildpackage -S -d -us -uc
popd

sudo cowbuilder --build $workdir/*.dsc --basepath "${BASEPATH}" --buildresult "$workdir" --debbuildopts -b

mkdir -p "$DST"
cp "$workdir/$DEB_NAME.deb" "$DST/."
