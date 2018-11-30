#!/bin/bash
set -eu

REGISTRY_PASS=$1
#REGISTRY_PASS=
kubectl create secret docker-registry registrysecret --docker-server haregistry.azurecr.io --docker-email bla@r3.com --docker-username=haregistry --docker-password $REGISTRY_PASS || true