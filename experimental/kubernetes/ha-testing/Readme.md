# HA Testing

## Hot Cold

Components:
- Postgres DB service and pod called `db`
- hanode-a and hanode-b pods, a service called `hanode` to load balance between the two
- persistent volumes for artemis, cordapps, doorman and notary

Registration is currently done with the doorman, the bootstrapper didn't work
for me, because some environment varaibles in the config file didn't resolve
properly. This could be improved by adding defaults.
