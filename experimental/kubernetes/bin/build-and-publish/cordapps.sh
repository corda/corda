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

load_gen_image="${registry}/r3/load-gen-cordapps:$rev"

(
  cd $workspace
  git apply $patch

  # Build Healthcheck Cordapps
  ./gradlew finance:jar
  JAR=$(ls -S finance/build/libs | head -n1)
  cp finance/build/libs/$JAR build-contexts/cordapps/

  cd build-contexts/cordapps
  ${docker_cmd}  build -t  $load_gen_image .
  ${docker_cmd}  push $load_gen_image
)

mkdir -p built
echo "$load_gen_image" > built/cordapps.txt

rm -rf $workspace

git worktree prune
