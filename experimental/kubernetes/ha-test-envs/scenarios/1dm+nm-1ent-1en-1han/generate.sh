#!/bin/bash
set -eu

SCRIPT_DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
cd ../..

./generate_from_template.py -o ${SCRIPT_DIR}/doorman/0.1 -t templates/doorman/0.1 -v '{"image":"haregistry.azurecr.io/doorman:0.1"}'
./generate_from_template.py -o ${SCRIPT_DIR}/notary/r3-corda/3.2 -t templates/notary/r3-corda/3.2 -v '{"name":"notary", "legal_name":"O=Notary,L=London,C=GB", "image":"haregistry.azurecr.io/r3-corda:3.2"}'

# TODO generate artemis own keys
./generate_from_template.py -o ${SCRIPT_DIR}/artemis/2.6.3 -t templates/artemis/2.6.3 -v '{"name":"artemis", "artemis_users":["SystemUsers/Node=O=PartyA, L=London, C=GB"], "artemis_role_nodes":"SystemUsers/Node", "image":"vromero/activemq-artemis:2.6.3"}'

# TODO use FirewallConfiguration to generate firewall.conf
./generate_from_template.py -o ${SCRIPT_DIR}/bridge/latest -t templates/bridge/latest -v '{"name":"bridge", "bridge_nodes":["partya"], "image":"haregistry.azurecr.io/corda-firewall"}'

# TODO use NodeConfiguration to generate node.conf
# Right now we patch node.conf after generate...
./generate_from_template.py -o ${SCRIPT_DIR}/partya/r3-corda/3.2 -t templates/r3-corda/3.2 -v '{"name":"partya", "legal_name":"O=PartyA,L=London,C=GB", "image":"haregistry.azurecr.io/r3-corda:3.2"}'

./generate_from_template.py -o ${SCRIPT_DIR}/partyb/r3-corda/3.2 -t templates/r3-corda/3.2 -v '{"name":"partyb", "legal_name":"O=PartyB,L=London,C=GB", "image":"haregistry.azurecr.io/r3-corda:3.2"}'

./generate_from_template.py -o ${SCRIPT_DIR}/misc -t templates/misc -v '{}'