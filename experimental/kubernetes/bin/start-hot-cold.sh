#!/bin/sh

kubectl apply -f config/ha/db-service.yml
kubectl apply -f config/ha/db-pod.yml
kubectl apply -f config/ha/service.yml
kubectl apply -f config/ha/node-a.yml
