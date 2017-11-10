#!/bin/bash

BASE_VERSION="jdk-8.0.0.jar"
NEW_VERSION="jdk-8.0.0-deterministic.jar"

# Derive list of differences between the two JARs
pkgdiff -check-byte-code -track-unchanged -extra-info pkgdiff_extra \
    "$BASE_VERSION" "$NEW_VERSION"

# Find packages and classes marked for exclusion in JavaDoc
${SHELL} tools/find-exclusions.sh

# Generate report
sed -n '1,/\/\* DATASET \*\//p' < ./tools/report-template.html > report.html
${SHELL} tools/generate-report.sh >> report.html
sed -n '/\/\* DATASET \*\//,$p' < ./tools/report-template.html >> report.html

# Generate structure for upload to Azure
mkdir -p report
mv exclusions pkgdiff_extra pkgdiff_reports report/
cd report
${SHELL} ../tools/flatten.sh
cd ..
