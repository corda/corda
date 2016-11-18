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
    if [ ! -d "docs/virtualenv/lib/python2.7/site-packages/sphinx" ]
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
java -jar lib/dokka.jar -output docs/build/html/api core/src/main/kotlin finance/src/main/kotlin node/src/main/kotlin client/src/main/kotlin  | grep -v "No documentation for"

echo
echo "Writing robots.txt"
echo

cat <<EOF >docs/build/html/robots.txt
User-agent: *
Disallow: /
EOF

echo "Done"
