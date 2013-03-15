#!/bin/sh

set -e

make test
make mode=debug test
make process=interpret test
# bootimage and openjdk builds without openjdk-src don't work:
if [ -z "${openjdk}" ]; then
  make bootimage=true test
fi
make tails=true continuations=true test
