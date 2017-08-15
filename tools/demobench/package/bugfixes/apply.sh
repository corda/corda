#!/bin/sh

BASEDIR=$(dirname $0)
SOURCEDIR=$BASEDIR/java
BUILDDIR=$BASEDIR/build
RTJAR=$1

if [ -z "$RTJAR" ]; then
    echo "Need location of rt.jar"
    exit 1
elif [ $(basename $RTJAR) != "rt.jar" ]; then
    echo "File is not rt.jar"
    exit 1
elif [ ! -f $RTJAR ]; then
    echo "$RTJAR not found"
    exit 1
fi

# Bugfixes:
# =========
#
# sun.swing.JLightweightFrame:473
# https://bugs.openjdk.java.net/browse/JDK-8185890

rm -rf $BUILDDIR && mkdir $BUILDDIR
if (javac -O -d $BUILDDIR $(find $SOURCEDIR -name "*.java")); then
    chmod u+w $RTJAR
    jar uvf $RTJAR -C $BUILDDIR .
    chmod ugo-w $RTJAR
fi

