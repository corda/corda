#!/bin/sh

set -e

root_dir=$(pwd)

run() {
  echo '==============================================='
  if [ ! $(pwd) = ${root_dir} ]; then
    printf "cd $(pwd); "
  fi
  echo "${@}"
  echo '==============================================='
  "${@}"
}

run_cmake() {
  mkdir -p cmake-build
  rm -rf cmake-build/*
  cd  cmake-build
  run cmake ${@} ..
  run make -j4 check
  cd ..
}

if [ ${#} -gt 0 ]; then
  run make ${@}
else
  run_cmake -DCMAKE_BUILD_TYPE=Debug

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
