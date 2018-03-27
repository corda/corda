#!/bin/bash

set -eux

doorman_name="doorman"
notary_name="notary"

kubectl create -f config/doorman/service.yml
kubectl create -f config/notary/service.yml

kubectl create -f config/doorman/pod-init.yml

wait_for_doorman() {
  set +e
  # TODO: use wait-for
  while :; do
    sleep 5
    kubectl logs $doorman_name | grep 'services started on doorman'
    if [[ $? -eq 0 ]]; then
      break
    fi
  done
  set -e
}

wait_for_doorman

kubectl cp ${doorman_name}:/data/doorman/certificates/distribute-nodes/network-root-truststore.jks .
kubectl delete secret truststore-3.0.0 || true
kubectl create secret generic truststore-3.0.0 --from-file=network-root-truststore.jks
rm network-root-truststore.jks

set +e

kubectl create -f config/notary/pod-init.yml

while :; do
  sleep 5
  kubectl logs ${notary_name} | grep 'DONE_BOOTSTRAPPING'
  if [[ $? -eq 0 ]]; then
    break
  fi
done

set -e

kubectl cp ${notary_name}:/data/notary-node-info .
kubectl cp notary-node-info ${doorman_name}:/data/
rm notary-node-info

kubectl delete po ${notary_name} ${doorman_name}

# TODO: wait for containers to be terminated, e.g. with grep on kubectl get po

while :; do
  sleep 5
  n=$(kubectl get po | wc -l)
  if [[ n -eq 0 ]]; then
    break
  fi
done

kubectl create -f config/doorman/pod.yml

wait_for_doorman

kubectl create -f config/notary/pod.yml
