#!/bin/sh

set -eux

# TODO: perhaps delte the namespace and recreate the PVCs?

kubectl delete configmap corda
kubectl delete configmap doorman
kubectl delete --all statefulsets
kubectl delete --all deployments
kubectl delete --all services
kubectl delete --all pods
kubectl delete --all jobs

while :; do
  sleep 5
  n=$(kubectl get pods | wc -l)
  if [[ n -eq 0 ]]; then
    break
  fi
done

kubectl delete --all persistentvolumeclaims
