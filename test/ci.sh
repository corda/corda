#!/bin/sh

set -e

make mode=debug test
make test
make process=interpret test
make bootimage=true test
make tails=true continuations=true test
