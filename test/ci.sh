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

flags="${@}"

run_if_not_covered() {
  local arg=$1
  shift

  for f in ${flags}; do
    local key=$(echo $f | awk -F '=' '{print $1}')
    if [ ${key} = ${arg} ]; then
      return
    fi
  done

  run "${@}"
}

run_cmake -DCMAKE_BUILD_TYPE=Debug

run make jdk-test
run make ${flags} test
run make ${flags} mode=debug test
run make ${flags} process=interpret test
run make ${flags} bootimage=true test
run make ${flags} mode=debug bootimage=true test

run_if_not_covered openjdk make ${flags} openjdk=$JAVA_HOME test

run make ${flags} tails=true continuations=true heapdump=true test
run make ${flags} codegen-targets=all
