#!/bin/sh

set -e

run() {
  echo '==============================================='
  echo "${@}"
  echo '==============================================='
  "${@}"
}

if [ ${#} -gt 0 ]; then
  run make ${@}
else
  run make jdk-test
  run make test
  run make mode=debug test
  run make process=interpret test
  run make bootimage=true test
  run make mode=debug bootimage=true test
  run make openjdk=$JAVA_HOME test
  run make tails=true continuations=true heapdump=true test
  run make codegen-targets=all
fi
