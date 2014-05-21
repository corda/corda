#!/bin/sh

set -e

run() {
  echo '==============================================='
  echo "${@}"
  echo '==============================================='
  "${@}"
}

if [ -z "${test_target}" ]; then
  test_target=test
fi

# we shouldn't run jdk-test builds if we're not running the test target
if [ ${test_target} = test ]; then
 run make ${flags} jdk-test
fi

run make ${flags} ${test_target}
run make ${flags} mode=debug ${test_target}
run make ${flags} process=interpret ${test_target}
# bootimage and openjdk builds without openjdk-src don't work:
if [ -z "${openjdk}" ]; then
  run make ${flags} bootimage=true ${test_target}
  # might as well do an openjdk test while we're here:
  run make openjdk=$JAVA_HOME ${flags} ${test_target}
fi
run make ${flags} tails=true continuations=true ${test_target}
run make ${flags} codegen-targets=all
