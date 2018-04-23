#!/bin/bash

REPORT_FILE="$1"
EXCLUSIONS_FILE="$2"

if [[ -z "$REPORT_FILE" ]]; then
    REPORT_FILE=pkgdiff_extra/files.xml
fi

if [[ -z "$EXCLUSIONS_FILE" ]]; then
    EXCLUSIONS_FILE=exclusions/files.dat
fi

function category {
    CATEGORY="$1"
    if [[ $CATEGORY = 'exclusions' ]]; then
        if [[ ! -z "$EXCLUSIONS_FILE" ]]; then
            cat $EXCLUSIONS_FILE
        fi
        return
    fi
    cat $REPORT_FILE \
        | sed '/META-INF/d' \
        | sed -n "/<$CATEGORY>/,/<\/$CATEGORY>/p" \
        | sed '1d; $d' \
        | sed 's/^[ \t]*//'
}

function unchanged_rows {
    # file
    cat \
        | sed 's/^\(.*\.class\)$/["class", "\1", "unchanged", "", ""],/' \
        | sed 's/^\([^.]*\)$/["package", "\1", "unchanged", "", ""],/'
}

function added_rows {
    # file
    cat \
        | sed 's/^\(.*\.class\)$/["class", "\1", "added", "", ""],/' \
        | sed 's/^\([^.]*\)$/["package", "\1", "added", "", ""],/'
}

function removed_rows {
    # file
    cat \
        | sed 's/^\(.*\.class\)$/["class", "\1", "removed", "", ""],/' \
        | sed 's/^\([^.]*\)$/["package", "\1", "removed", "", ""],/'
}

function changed_rows {
    # file ([0-9]+%)
    sed 's/^\([^ ]*\)[ ](\([^)]*\))$/["class", "\1", "changed", "\2", "\1"],/'
}

function renamed_rows {
    # from;to ([0-9]+%)
    sed 's/^\([^;]*\);\([^ ]*\) (\([0-9%.]*\))$/["class", "\1 -> \2", "renamed", "\3", ""],/'
}

function moved_rows {
    # from;to ([0-9]+%)
    sed 's/^\([^;]*\);\([^ ]*\) (\([0-9%.]*\))$/["class", "\1 -> \2", "moved", "\3", ""],/'
}

function exclusion_rows {
    # file
    sed 's/^\([^ ]*\)$/["java", "\1", "exclusions", "", "\1"],/'
}

category 'unchanged'  | unchanged_rows
category 'added'      |     added_rows
category 'removed'    |   removed_rows
category 'changed'    |   changed_rows
category 'moved'      |     moved_rows
category 'renamed'    |   renamed_rows
category 'exclusions' | exclusion_rows
