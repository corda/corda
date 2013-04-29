#!/bin/sh

set -e

make ${flags} test
make ${flags} mode=debug test
make ${flags} process=interpret test
# bootimage and openjdk builds without openjdk-src don't work:
if [ -z "${openjdk}" ]; then
  make ${flags} bootimage=true test
fi
make ${flags} tails=true continuations=true test
