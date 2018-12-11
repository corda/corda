#!/bin/bash
set -eu

SCRIPT_DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
cd ../..

./generate_from_template.py -o ${SCRIPT_DIR}/doorman/0.1 -t templates/doorman/0.1 -v '{"image":"haregistry.azurecr.io/doorman:0.1"}'
./generate_from_template.py -o ${SCRIPT_DIR}/notary/corda/3.2 -t templates/notary/corda/3.2 -v '{"name":"notary", "legal_name":"O=Notary,L=London,C=GB", "image":"haregistry.azurecr.io/corda:3.2"}'

./generate_from_template.py -o ${SCRIPT_DIR}/misc -t templates/misc -v '{}'