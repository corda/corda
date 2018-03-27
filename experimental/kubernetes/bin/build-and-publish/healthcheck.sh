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

load_gen_image="${registry}/r3/load-generator:$rev"

(
  cd $workspace
  git apply $patch

  # Build Healthcheck
  ./gradlew tools:notaryhealthcheck:shadowJar
  cp tools/notaryhealthcheck/build/libs/shadow.jar build-contexts/load-generator/app.jar

  cd build-contexts/load-generator
  ${docker_cmd}  build -t  $load_gen_image .
  ${docker_cmd}  push $load_gen_image
)

mkdir -p built
echo "$load_gen_image" > built/load_generator.txt

rm -rf $workspace

git worktree prune
