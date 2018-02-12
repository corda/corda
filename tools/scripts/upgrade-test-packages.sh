#!/bin/bash

find $1 -type f \( -iname \*.kt -o -iname \*.java \) -exec sed -i "
s/net.corda.testing.\(\*\|generateStateRef\|freeLocalHostAndPort\|getFreeLocalPorts\|getTestPartyAndCertificate\|TestIdentity\|chooseIdentity\|singleIdentity\|TEST_TX_TIME\|DUMMY_NOTARY_NAME\|DUMMY_BANK_A_NAME\|DUMMY_BANK_B_NAME\|DUMMY_BANK_C_NAME\|BOC_NAME\|ALICE_NAME\|BOB_NAME\|CHARLIE_NAME\|DEV_INTERMEDIATE_CA\|DEV_ROOT_CA\|dummyCommand\|DummyCommandData\|MAX_MESSAGE_SIZE\|SerializationEnvironmentRule\|setGlobalSerialization\|expect\|sequence\|parallel\|replicate\|genericExpectEvents\)/net.corda.testing.core.\1/g
s/net.corda.testing.FlowStackSnapshotFactoryImpl/net.corda.testing.services.FlowStackSnapshotFactoryImpl/g
" '{}' \;