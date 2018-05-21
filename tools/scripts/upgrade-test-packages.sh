#!/bin/bash

#
# Run this script with the path to your source code as the first argument. It will do some basic search/replace
# changes to the files to ease the port from 2.0 to 3.0 (but it isn't a complete solution).
#

s=$( which gsed )
if [[ $? == 1 ]]; then
    s="sed"
fi

find $1 -type f \( -iname \*.kt -o -iname \*.java \) -exec $s -i "
s/net.corda.testing.\(\*\|generateStateRef\|freeLocalHostAndPort\|getFreeLocalPorts\|getTestPartyAndCertificate\|TestIdentity\|chooseIdentity\|singleIdentity\|TEST_TX_TIME\|DUMMY_NOTARY_NAME\|DUMMY_BANK_A_NAME\|DUMMY_BANK_B_NAME\|DUMMY_BANK_C_NAME\|BOC_NAME\|ALICE_NAME\|BOB_NAME\|CHARLIE_NAME\|DEV_INTERMEDIATE_CA\|DEV_ROOT_CA\|dummyCommand\|DummyCommandData\|MAX_MESSAGE_SIZE\|SerializationEnvironmentRule\|setGlobalSerialization\|expect\|sequence\|parallel\|replicate\|genericExpectEvents\)/net.corda.testing.core.\1/g
" '{}' \;