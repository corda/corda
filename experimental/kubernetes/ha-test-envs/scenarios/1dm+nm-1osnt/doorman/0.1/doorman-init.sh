#!/bin/bash
set -eu

kubectl create configmap doorman-configmap --from-file=configMaps
kubectl create -f services/doorman-db.yml
kubectl create -f deployments/doorman-db.yml
kubectl create -f jobs/doorman-init.yml
kubectl create -f services/doorman.yml
kubectl create -f pods/doorman-init.yml