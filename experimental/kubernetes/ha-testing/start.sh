#!/bin/sh

set -eux

kubectl apply -f pvcs/artemis.yml
kubectl apply -f pvcs/certificates.yml
kubectl apply -f services/
kubectl apply -f pods/db.yml


