#!/bin/sh

DIRNAME=$(dirname $0)

if [ -z "$JAVA_HOME" ]; then
    echo "Please set JAVA_HOME correctly"
    exit 1
fi

exec $DIRNAME/gradlew -PpackageType=dmg javapackage $*
