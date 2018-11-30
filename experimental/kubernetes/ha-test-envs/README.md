# HA Kubernetes Test Environments

## Prerequisites
* Download the latest version of docker
* Once docker is installed, go into preference menu, enable kubernetes and increase the max memory(8gb recommended) and cpu
* Check: `docker version` is atleast 18.06.1 and `kubectl version` is atleast v1.10.3 for client and server

## Spin up an example test scenario
* Check you are using the correct kubernetes cluster, `kubectl config get-contexts` (`docker-for-desktop` is your local one)
* Clean up cluster, `./clean-kub.sh`
* Add private registry secret to cluster, `./registry-secret.sh <Registry Password>`
* `cd scenario/<pick a scenario>`
* `./run.sh`

## Dashboard
* Port forward kubernetes-dashboard, `kubectl proxy` available at http://localhost:8001/api/v1/namespaces/kube-system/services/https:kubernetes-dashboard:/proxy

## Azure Kubernetes Service
* `az login`
* Add cluster info, `az aks get-credentials --resource-group <resource_group_name> --name <cluster_name> --subscription <subscription> --overwrite-existing`
* `kubectl config use-context <cluster_name>`

## Generate scenarios from templates
Jinja2 is used for templating, install using `pip install jinja2`. Jinja2 template syntax, "Hello {{ name }}" render(name="bob") -> "Hello bob".
You can generate your own test environments using `generate_from_template.py` script.

`./generate_from_template.py -o <Output folder> -t <Template folder> -v <Dictionary of template values, '{"var_name" : "value"}'>`

### Doorman Sample
```
./generate_from_template.py -o doorman/0.1 -t templates/doorman/0.1 -v '{"image":"haregistry.azurecr.io/doorman:0.1"}'
```
### Notary Sample
```
./generate_from_template.py -o notary/corda/3.2 -t templates/notary/corda/3.2 -v '{"name":"notary", "image":"haregistry.azurecr.io/corda:3.2", "legal_name":"O=Notary,L=London,C=GB", "compatibility_zone_url":"http://doorman:10000"}'
```
### Corda Sample
```
./generate_from_template.py -o corda/3.2 -t templates/corda/3.2 -v '{"name":"corda", "image":"haregistry.azurecr.io/corda:3.2", "legal_name":"O=Corda,L=London,C=GB", "compatibility_zone_url":"http://doorman:10000"}'
```
### Artemis Sample
```
./generate_from_template.py -o artemis/2.6.3 -t templates/artemis/2.6.3 -v '{"name":"artemis", "image":"vromero/activemq-artemis:2.6.3", "artemis_users":["NodeA=O=PartyA, L=London, C=GB", "NodeB=O=PartyB, L=London, C=GB"], "artemis_role_nodes":"Node=NodeA,NodeB"}'
```
### Bridge Sample
```
./generate_from_template.py -o bridge/latest -t templates/bridge/latest -v '{"name":"bridge", "image":"haregistry.azurecr.io/corda-firewall", "artemis_name":"artemis"}'
``` 