# HA Kubernetes Test Environments

## Prerequisites
* Download the latest version of docker
* Once docker is installed, go into preference menu, enable kubernetes and increase the max memory(8gb recommended) and cpu
* Check: `docker version` is atleast 18.06.1 and `kubectl version` is atleast v1.10.3 for client and server
* Get the registry password from someone!

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
* Create a new kubernetes cluster or use an existing one. TODO az aks create ...
* Add cluster info, `az aks get-credentials --resource-group <resource_group_name> --name <cluster_name> --subscription <subscription> --overwrite-existing`
* `kubectl config use-context <cluster_name>`
* Follow instructions on running test scenario

## Google Kubernetes Services
* TODO

## Amazon Kubernetes Services
* TODO

## Generate scenarios from templates
Jinja2 is used for templating, install using `pip install jinja2`. Jinja2 template syntax, "Hello {{ name }}" render(name="bob") -> "Hello bob".
You can generate your own test environments using `generate_from_template.py` script.

The templates have purposefully been kept very simple, the Corda one only parameterises the name, legal name and image. If you need anything more complicated,
patch/replace the node.conf after generation.

`./generate_from_template.py -o <Output folder> -t <Template folder> -v <Dictionary of template values, '{"var_name" : "value"}'>`

### Doorman Sample
```
./generate_from_template.py -o doorman/0.1 -t templates/doorman/0.1 -v '{"image":"haregistry.azurecr.io/doorman:0.1"}'
```
### Notary Sample
```
./generate_from_template.py -o notary/corda/3.2 -t templates/notary/corda/3.2 -v '{"name":"notary", "legal_name":"O=Notary,L=London,C=GB", "image":"haregistry.azurecr.io/corda:3.2"}'
```
### Corda Sample
```
./generate_from_template.py -o corda/3.2 -t templates/corda/3.2 -v '{"name":"corda", "legal_name":"O=Corda,L=London,C=GB", "image":"haregistry.azurecr.io/corda:3.2"}'
```
### Artemis Sample
```
./generate_from_template.py -o artemis/2.6.3 -t templates/artemis/2.6.3 -v '{"name":"artemis", "artemis_users":["NodeA=O=PartyA, L=London, C=GB", "NodeB=O=PartyB, L=London, C=GB"], "artemis_role_nodes":"NodeA,NodeB", "image":"vromero/activemq-artemis:2.6.3"}'
```
### Bridge Sample
```
./generate_from_template.py -o bridge/latest -t templates/bridge/latest -v '{"name":"bridge", "bridge_nodes":["partya"], "image":"haregistry.azurecr.io/corda-firewall"}'
```

## Adding Cordapp, customizing docker images
* Example creating a new docker image with the finance cordapp
```
FROM haregistry.azurecr.io/r3-corda:3.2

RUN mkdir -p /opt/corda/cordapps/config && \
    echo "issuableCurrencies = [ USD ]" > /opt/corda/cordapps/config/corda-finance-3.2-corda.conf

COPY corda-finance-3.2.jar /opt/corda/cordapps/corda-finance-3.2.jar
```

## TODO
* official docker image
* use NodeConfiguration.kt to generate node.conf, there's no point in templating out the whole node.conf and passing all the values into generate_from_template.py
* use FirewallConfiguration.kt to generate firewall.conf
* more docs on creating/customising docker images for cordapp, corda.jar. Check-in in scripts to create/customize docker images, push to registry
* get rid of bash to wait for pods to be running(wait_for), move into initContainers which polls service. Should be possible to generate run.sh or better kubectl apply -f folder
* use namespaces and better clean_kub.sh
* explorer, perf/load/networkDriver
* maybe use https://github.com/GoogleContainerTools/jib for dev workflow
* logging/monitoring(graffna/prometheus/fluentd)
* security/RBAC
* try multi-cluster setup
* net latency, chaos monkey etc
