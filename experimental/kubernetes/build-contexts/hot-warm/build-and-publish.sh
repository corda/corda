#!/bin/bash

set -eux

img="ctesting.azurecr.io/r3/hanode:$(git rev-parse --short HEAD)"

docker build -t $img .
docker push $img
