#!/bin/bash
set -eu

kubectl create configmap notary-configmap --from-file=configMaps
kubectl create -f services/notary.yml
kubectl create -f jobs/notary-init.yml