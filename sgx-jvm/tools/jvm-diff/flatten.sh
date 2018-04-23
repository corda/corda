#!/bin/bash

find {pkgdiff_reports/jdk,exclusions} -type f \( -iname '*.html' -o -iname '*.java' \) | while read f; do
    mv $f $(echo $f | tr '/' '.')
done
