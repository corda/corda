#!/bin/sh

kubectl apply -f config/ha/db-service.yml
kubectl apply -f config/ha/db-pod.yml
kubectl apply -f config/ha/zk-service.yml
kubectl apply -f config/ha/zk-pod.yml
kubectl apply -f templates/services/hotwarm.yml
kubectl apply -f config/ha/hot-warm.yml
