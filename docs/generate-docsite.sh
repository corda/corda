#!/usr/bin/env bash

set -xeo pipefail

if [ ! -e ./gradlew ]; then
    echo "Run from the root directory please"
    exit 1
fi

if [ ! -e lib/dokka.jar ]; then
    echo "Downloading Dokka tool ... "
    echo
    wget -O lib/dokka.jar https://github.com/Kotlin/dokka/releases/download/0.9.8/dokka-fatjar.jar
fi

(
    cd docs

    if [ ! -d "virtualenv" ]
    then
        virtualenv -p python2.7 virtualenv
    fi
    . virtualenv/bin/activate
    if [ ! -d "virtualenv/lib/python2.7/site-packages/sphinx" ]
    then
        echo "Installing pip dependencies ... "
        pip install -r requirements.txt
    fi

    echo "Generating docsite ..."
    echo

    make html
)

echo
echo "Generating API docs ..."
echo

SOURCES=$(find . \( -wholename  "*src/main/kotlin" -or -wholename "*src/main/java" \) -and -not -wholename "./samples/*")
TARGET=docs/build/html/api
java -jar lib/dokka.jar -output $TARGET $SOURCES | grep -v "No documentation for"

echo "Generated documentation to $TARGET"

echo
echo "Writing robots.txt"
echo

cat <<EOF >docs/build/html/robots.txt
User-agent: *
Disallow: /
EOF

echo "Done"
