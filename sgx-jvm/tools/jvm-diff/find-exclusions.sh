#!/bin/bash

OUTPUT_DIR=exclusions

rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"

if [[ $(uname) = "Darwin" ]]; then
    alias sed=gsed # ensure that we use GNU sed on macOS
fi

function files_with_exclusions {
    local DIRS="jaxp jaxws jdk"
    find $DIRS -name '*.java' -exec grep -l '@exclude' {} \;
}

function extract_exclusions {
    echo -n "."
    echo "$1" >> "$OUTPUT_DIR/files.dat"
    mkdir -p "$OUTPUT_DIR/$(dirname "$1")"
    cat "$1" | \
        sed 's/^\s*package/public package/' | \
        sed 's/^\s*class/public class/' | \
        sed -n '/@exclude/,/public.*[a-z]/{
                    s/^.*\(@exclude.*\)$/\/\/ \1/p;
                    s/^\s*\(public.*[a-z].*\)$/\1/p
                }' | \
        sed 's/^public package/package/' | \
        sed 's/[ ][ ]*/ /g' | \
        sed 's/[{][ ]*$//g' \
        > "$OUTPUT_DIR/$1"
}

echo -n "Finding files with exclusions ..."
files_with_exclusions | while read f; do extract_exclusions "$f"; done
echo " [done]"
