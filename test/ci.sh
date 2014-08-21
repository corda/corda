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

has_flag() {
  local arg=$1
  for f in ${flags}; do
    local key=$(echo $f | awk -F '=' '{print $1}')
    if [ ${key} = ${arg} ]; then
      return 0
    fi
  done
  return 1
}

make_target=test

test `uname -o` = "Cygwin" || run_cmake -DCMAKE_BUILD_TYPE=Debug

run make jdk-test
run make ${flags} ${make_target}
run make ${flags} mode=debug ${make_target}
run make ${flags} process=interpret ${make_target}

(has_flag openjdk-src || ! has_flag openjdk) && \
  run make ${flags} mode=debug bootimage=true ${make_target} && \
  run make ${flags} bootimage=true ${make_target}

(! has_flag openjdk && ! has_flag android) && \
  run make ${flags} openjdk=$JAVA_HOME ${make_target}

run make ${flags} tails=true continuations=true heapdump=true ${make_target}
run make ${flags} codegen-targets=all
