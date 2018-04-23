#!/bin/sh

set -eux

# Configuration files
kubectl create configmap corda --from-file=config-files
kubectl create configmap doorman --from-file=config-files/doorman

# Claim volumes.
kubectl apply -f config/persistent-volume-claims/
# TODO: do we need to wait for the claims to be bound?

# Distribute cordapps.
kubectl apply -f config/load-generator/distribute-cordapp-job.yml
set +e
while :; do
  sleep 5
  kubectl describe job distribute-cordapps | grep '1 Succeeded'
  if [[ $? -eq 0 ]]; then
    break
  fi
done
set -e
kubectl delete job distribute-cordapps

# Apply config.
for d in $(find config -name 'r3-*' -type d); do
  kubectl apply -f $d
done
for d in $(find config -name 'corda-*' -type d); do
  kubectl apply -f $d
done

# Bootup doorman and notary.
./bin/bootstrap.sh

# Startup corda nodes and load generator.
for d in $(find config -name 'r3-*' -type d); do
  kubectl scale sts $(basename $d) --replicas=1
done
for d in $(find config -name 'corda-*' -type d); do
  kubectl scale sts $(basename $d) --replicas=1
done
kubectl apply -f config/load-generator/load-generator.yml
kubectl scale deployment load-generator --replicas=1
