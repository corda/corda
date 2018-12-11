#!/bin/bash
set -eu

kubectl create configmap corda-configmap --from-file=configMaps
kubectl create -f services/corda.yml
kubectl create -f jobs/corda-init.yml