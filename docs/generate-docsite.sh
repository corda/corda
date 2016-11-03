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

echo "Installing pip dependencies ... "
echo
cd docs
pip install -r requirements.txt;

echo "Generating docsite ..."
echo

make clean html

echo
echo "Generating API docs ..."
echo

java -jar lib/dokka.jar -output docs/build/html/api core/src/main/kotlin contracts/src/main/kotlin node/src/main/kotlin src/main/kotlin client/src/main/kotlin  | grep -v "No documentation for"

echo
echo "Writing robots.txt"
echo

cat <<EOF >docs/build/html/robots.txt
User-agent: *
Disallow: /
EOF

echo "Done"