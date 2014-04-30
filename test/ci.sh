#!/bin/sh

set -e

if [ -z "${test_target}" ]; then
  test_target=test
fi

# we shouldn't run jdk-test builds if we're not running the test target
if [ ${test_target} = test ]; then
  make ${flags} jdk-test
fi

make ${flags} ${test_target}
make ${flags} mode=debug ${test_target}
make ${flags} process=interpret ${test_target}
# bootimage and openjdk builds without openjdk-src don't work:
if [ -z "${openjdk}" ]; then
  make ${flags} bootimage=true ${test_target}
fi
make ${flags} tails=true continuations=true ${test_target}
make ${flags} codegen-targets=all
