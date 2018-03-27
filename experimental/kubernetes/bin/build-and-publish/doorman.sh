#!/bin/sh

set -eux

target_rev=${1:-"HEAD"}
registry=${CONTAINER_REGISTRY:-"ctesting.azurecr.io"}

workspace=$(mktemp -d -t kubeform-XXX)

export rev=$(git rev-parse $target_rev)
rev_short=$(git rev-parse --short $target_rev)

git worktree add $workspace $rev
cp -r build-contexts $workspace

docker_cmd="docker"
if [ "$(uname)" = "Linux" ]; then
  docker_cmd="sudo docker"
fi

patch="0001-Read-corda-rev-from-environment-var.patch"
cp $patch $workspace

container_image="${registry}/r3/doorman:$rev"

(
  cd $workspace
  git apply $patch
  ./gradlew network-management:capsule:buildDoormanJAR
  JAR=$(ls -S network-management/capsule/build/libs/doorman-*.jar | head -n1)
  cp $JAR build-contexts/doorman/doorman.jar

  cd build-contexts/doorman
  ${docker_cmd}  build -t  $container_image .
  ${docker_cmd}  push $container_image
)

mkdir -p built
echo "doorman-r3-$rev_short $container_image" > built/doorman.txt

rm -rf $workspace

git worktree prune
