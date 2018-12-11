#!/bin/bash
set -eu

kubectl delete --all statefulsets
kubectl delete --all deployments
kubectl delete --all services
kubectl delete --all pods
kubectl delete --all persistentvolumeclaims
kubectl delete --all persistentvolumes
kubectl delete --all jobs
kubectl delete --all configmaps
kubectl delete --all secrets
kubectl delete --all namespaces