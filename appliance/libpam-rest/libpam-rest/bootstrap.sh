#!/bin/sh

set -e
set -x

libtoolize --copy --force
aclocal -I config --force --install
autoheader -I config --force
automake --add-missing --copy
autoconf -I config

if test "$NOCONFIG"; then
  :
else
  ./configure --verbose "$@"
fi
