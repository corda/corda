#!/bin/sh

set -e

make mode=debug test
make test
make process=interpret test
# bootimage and openjdk builds without openjdk-src don't work:
if [ -z "${openjdk}" ]; then
  make bootimage=true test
fi
make tails=true continuations=true test
