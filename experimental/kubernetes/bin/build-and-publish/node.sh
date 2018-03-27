#!/bin/sh

set -eux

target_rev=${1:-"HEAD"}
container_name=${2:-"r3-corda"}
registry=${CONTAINER_REGISTRY:-"ctesting.azurecr.io"}

export rev=$(git rev-parse $target_rev)
rev_short=$(git rev-parse --short $target_rev)

workspace=$(mktemp -d -t kubeform-XXX)


git worktree add $workspace $rev
cp -r build-contexts $workspace

docker_cmd="docker"
if [ "$(uname)" = "Linux" ]; then
  docker_cmd="sudo docker"
fi

patch="0001-Read-corda-rev-from-environment-var.patch"
cp $patch $workspace

container_image="${registry}/r3/${container_name}:$rev"

(
  cd $workspace
  git apply $patch
  ./gradlew jar
  JAR=$(ls -S node/capsule/build/libs | head -n1)
  cp node/capsule/build/libs/$JAR build-contexts/node/corda.jar

  cd build-contexts/node
  ${docker_cmd}  build -t  $container_image .
  ${docker_cmd}  push $container_image
)

mkdir -p built
echo "r3-$rev_short $container_image" >> built/node-images.txt

rm -rf $workspace

git worktree prune
