#!/usr/bin/env bash

if [ ! -e ./gradlew ]; then
    echo "Run from the root directory please"
    exit 1
fi

if [ ! -e lib/dokka.jar ]; then
    echo "Downloading Dokka tool ... "
    echo
    wget -O lib/dokka.jar https://github.com/Kotlin/dokka/releases/download/0.9.7/dokka-fatjar.jar
fi

echo "Generating docsite ..."
echo

( cd docs; make html )

echo
echo "Generating API docs ..."
echo

java -jar lib/dokka.jar -output docs/build/html/api src/main/kotlin core/src/main/kotlin contracts/src/main/kotlin  | grep -v "No documentation for"

echo
echo "Done"