### Overview

This folder has a set of utilities for generating a report outlining the
differences between two versions of the OpenJDK project, more specifically, the
`rt.jar` build artefact across versions.

##### Files

The following scripts and utilities are used:

 * `compare-versions.sh` - extract exclusions and binary diff between JDK8 and
   Deterministic JVM
 * `find-exclusions.sh` - utility used in the above script to find JavaDoc
   exclusions in code base
 * `generate-report.sh` - once the data has been gathered with
   `compare-versions.sh`, this script can be used to generate an HTML report
 * `report-template.html` - the template to use for the HTML report
 * `flatten.sh` - flatten the file hierarchy of supporting diff and exclusion
   files into a single directory

##### Dependencies

 * Tweaked version of the [Package Changes Analyzer (corda/pkgdiff)](https://github.com/corda/pkgdiff)

##### Assumptions

The first two scripts are intended to be run from the root directory of the
OpenJDK project, and assume two built JAR files:

 * `jdk-8.0.0.jar`, which is the `rt.jar` build artefact for branch
   `jdk8u/jdk8u`
 * `jdk-8.0.0-determinstic.jar`, which is the `rt.jar` build artefact for
   branch `deterministic-jvm8`

### How to run?

From the root folder of your cloned OpenJDK project:

 1. Assuming that the files in this folder is in a subfolder `tools` of the
    OpenJDK root folder
 2. Build `jdk8u/jdk8u` and copy `build/<target>/images/lib/rt.jar` to
    `jdk-8.0.0.jar`
 3. Build `deterministic-jvm8` and copy `build/<target>/images/lib/rt.jar` to
    `jdk-8.0.0-deterministic.jar`
 4. Run `bash tools/compare-versions.sh`
 5. Make a copy of the resulting report:
    - `report/`
