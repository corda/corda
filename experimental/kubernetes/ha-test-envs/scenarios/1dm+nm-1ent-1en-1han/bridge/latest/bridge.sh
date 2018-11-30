 #!/bin/bash
set -eu

kubectl create configmap bridge-configmap --from-file=configMaps
kubectl create -f services/bridge.yml
kubectl create -f deployments/bridge.yml