 #!/bin/bash
set -eu

kubectl create configmap artemis-configmap --from-file=configMaps
kubectl create -f services/artemis.yml
kubectl create -f deployments/artemis.yml