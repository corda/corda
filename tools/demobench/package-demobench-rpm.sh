#!/bin/sh

DIRNAME=$(dirname $0)

if [ -z "$JAVA_HOME" -o ! -x $JAVA_HOME/bin/java ]; then
    echo "Please set JAVA_HOME correctly"
    exit 1
fi

if ($DIRNAME/../../gradlew -PpackageType=rpm javapackage $*); then
    echo
    echo "Wrote installer to '$(find $DIRNAME/build/javapackage/bundles -type f)'"
    echo
else
    echo "Failed to create installer."
    exit 1
fi
