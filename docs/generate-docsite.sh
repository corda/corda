#!/usr/bin/env bash

set -xeo pipefail

if [ ! -e ./gradlew ]; then
    echo "Run from the root directory please"
    exit 1
fi

if [ ! -e lib/dokka.jar ]; then
    echo "Downloading Dokka tool ... "
    echo
    curl -L -o lib/dokka.jar https://github.com/Kotlin/dokka/releases/download/0.9.8/dokka-fatjar.jar
fi

(
    cd docs

    if [ ! -d "virtualenv" ]
    then
        # Check if python2.7 is installed explicitly otherwise fall back to the default python
        if type "python2.7" > /dev/null; then
            virtualenv -p python2.7 virtualenv
        else
            virtualenv virtualenv
        fi
    fi

    if [ -d "virtualenv/bin" ]
    then
        # it's a Unix system
        . virtualenv/bin/activate
    else
        . virtualenv/Scripts/activate
    fi

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
java -jar lib/dokka.jar -output docs/build/html/api core/src/main/kotlin finance/src/main/kotlin node/src/main/kotlin client/src/main/kotlin  | grep -v "No documentation for"

echo
echo "Writing robots.txt"
echo

cat <<EOF >docs/build/html/robots.txt
User-agent: *
Disallow: /
EOF

echo "Done"
