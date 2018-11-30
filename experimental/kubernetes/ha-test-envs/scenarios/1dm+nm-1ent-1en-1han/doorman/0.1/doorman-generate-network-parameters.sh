#!/bin/bash
set -eu

kubectl delete pod doorman || true
kubectl create -f jobs/doorman-generate-network-parameters.yml