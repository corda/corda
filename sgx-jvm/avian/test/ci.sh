#!/usr/bin/env bash

set -eo pipefail

root_dir=$(pwd)

flags="use-werror=true ${@}"

is-mac() {
  if [[ $(uname -s) == "Darwin" || ${TRAVIS_OS_NAME} == "osx" ]]; then
    return 0
  fi
  return 1
}

install-deps() {
  if is-mac; then
    echo "------ Installing dependencies for Mac ------"
  else
    echo "------ Installing dependencies for Linux ------"
    sudo apt-get update -qq
    sudo apt-get install -y libc6-dev-i386 mingw-w64 gcc-mingw-w64-x86-64 g++-mingw-w64-i686 binutils-mingw-w64-x86-64 lib32z1-dev zlib1g-dev g++-mingw-w64-x86-64
  fi
}

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

### START ###

echo "java version:"
${JAVA_HOME}/bin/java -version

install-deps

if [[ "${1}" == "PUBLISH" ]]; then
  if is-mac; then
    publish "macosx" "i386 x86_64"
  elif [[ $(uname -s) == "Linux" ]]; then
    publish "linux windows" "i386 x86_64"
  fi
else
  if [[ $(uname -o) != "Cygwin" ]]; then
    run_cmake -DCMAKE_BUILD_TYPE=Debug
  fi

  make_target=test

  if ! has_flag arch; then
    run make ${flags} jdk-test
  fi

  run make ${flags} ${make_target}
  run make ${flags} mode=debug ${make_target}
  run make ${flags} process=interpret ${make_target}

  if has_flag openjdk-src || ! has_flag openjdk; then
    run make ${flags} mode=debug bootimage=true ${make_target}
    run make ${flags} bootimage=true ${make_target}
    run make ${flags} bootimage=true bootimage-test=true ${make_target}
  fi

  if ! has_flag openjdk && ! has_flag android && ! has_flag arch; then
    run make ${flags} openjdk=$JAVA_HOME ${make_target}
  fi

  run make ${flags} tails=true continuations=true heapdump=true ${make_target}
  run make ${flags} codegen-targets=all
fi
