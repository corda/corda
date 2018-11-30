#!/bin/bash

wait_for() {
    set +e
    while :; do
        sleep 5
        eval $1
        if [[ $? -eq 0 ]]; then
        break
        fi
    done
    set -e
}

kubectl create configmap doorman-configmap --from-file=doorman/0.1/configMaps
kubectl create -f doorman/0.1/services/doorman-db.yml
kubectl create -f doorman/0.1/deployments/doorman-db.yml

wait_for "kubectl get pods | grep doorman-db |grep Running"

kubectl create -f doorman/0.1/jobs/doorman-init.yml
kubectl create -f doorman/0.1/services/doorman.yml
kubectl create -f doorman/0.1/pods/doorman-init.yml

wait_for "kubectl get pod doorman | grep Running"

kubectl create configmap notary-configmap --from-file=notary/r3-corda/3.2/configMaps
kubectl create -f notary/r3-corda/3.2/services/notary.yml
kubectl create -f notary/r3-corda/3.2/jobs/notary-init.yml

wait_for "kubectl get pods | grep notary-init | grep Completed"

kubectl delete pod doorman || true
kubectl create -f doorman/0.1/jobs/doorman-generate-network-parameters.yml

wait_for "kubectl get pods | grep doorman-generate-network-parameters | grep Completed"

kubectl create -f doorman/0.1/pods/doorman.yml

wait_for "kubectl get pod doorman | grep Running"

kubectl create -f notary/r3-corda/3.2/pods/notary.yml

wait_for "kubectl get pods | grep notary | grep Running"

kubectl create configmap artemis-configmap --from-file=artemis/latest/configMaps
kubectl create -f artemis/latest/services/artemis.yml
kubectl create -f artemis/latest/deployments/artemis.yml

wait_for "kubectl get pods | grep artemis | grep Running"

kubectl create -f bridge/latest/services/bridge.yml

kubectl create configmap partya-configmap --from-file=partya/3.2/configMaps
kubectl create -f partya/3.2/services/partya.yml
kubectl create -f partya/3.2/jobs/partya-init.yml

wait_for "kubectl get pods | grep partya-init | grep Completed"

kubectl create -f partya/3.2/pods/partya.yml

wait_for "kubectl exec notary ls | grep network-parameters"
kubectl cp notary:/opt/corda/network-parameters .
kubectl create secret generic network-parameters-secret --from-file=network-parameters
rm network-parameters

kubectl create configmap bridge-configmap --from-file=bridge/latest/configMaps
kubectl create -f bridge/latest/pods/bridge.yml

wait_for "kubectl get pods | grep bridge | grep Running"

kubectl create configmap partyb-configmap --from-file=partyb/3.2/configMaps
kubectl create -f partyb/3.2/services/partyb.yml
kubectl create -f partyb/3.2/jobs/partyb-init.yml

wait_for "kubectl get pods | grep partyb-init | grep Completed"

kubectl create -f partyb/3.2/pods/partyb.yml

kubectl create -f misc/kubernetes-dashboard.yml || true