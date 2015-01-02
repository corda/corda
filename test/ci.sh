#!/usr/bin/env bash

set -eo pipefail

root_dir=$(pwd)

flags="${@}"

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

publish() {
  local platforms="${1}"
  local arches="${2}"

  local platform
  for platform in ${platforms}; do
    local arch
    for arch in ${arches}; do
      echo "------ Publishing ${platform}-${arch} ------"
      ./gradlew artifactoryPublish -Pplatform=${platform} -Parch=${arch}
    done
  done
}

has_flag() {
  local arg=${1}

  local f
  for f in ${flags}; do
    local key=$(echo $f | awk -F '=' '{print $1}')
    if [ ${key} = ${arg} ]; then
      return 0
    fi
  done
  return 1
}

make_target=test

if [[ "${1}" == "PUBLISH" ]]; then
  if [[ $(uname -s) == "Darwin" || ${TRAVIS_OS_NAME} == "osx" ]]; then
    publish "macosx" "i386 x86_64"
  elif [[ $(uname -s) == "Linux" ]]; then
    publish "linux windows" "i386 x86_64"
  fi
else
  if [[ $(uname -o) != "Cygwin" ]]; then
    run_cmake -DCMAKE_BUILD_TYPE=Debug
  fi

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
fi
