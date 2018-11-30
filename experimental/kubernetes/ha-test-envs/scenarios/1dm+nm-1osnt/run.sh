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

kubectl create configmap notary-configmap --from-file=notary/corda/3.2/configMaps
kubectl create -f notary/corda/3.2/services/notary.yml
kubectl create -f notary/corda/3.2/jobs/notary-init.yml

wait_for "kubectl get pods | grep notary-init | grep Completed"

kubectl delete pod doorman || true
kubectl create -f doorman/0.1/jobs/doorman-generate-network-parameters.yml

wait_for "kubectl get pods | grep doorman-generate-network-parameters | grep Completed"

kubectl create -f doorman/0.1/pods/doorman.yml

wait_for "kubectl get pod doorman | grep Running"

kubectl create -f notary/corda/3.2/pods/notary.yml

kubectl create -f misc/kubernetes-dashboard.yml || true