#!/bin/sh

# This is a sample oswrapper script used for unit test.  Note that
# this script is not secure and should not be used on a real system.

CLASSPATH=$1
shift
MAIN=$1
shift

java -cp "${CLASSPATH}" "${MAIN}" "$@"
