#!/bin/bash
set +o posix

ALIAS=ias
KEYPASS=attestation
STOREPASS=attestation

rm -f dummyIAS.pfx dummyIAS-trust.pfx

CNF=`cat <<EOF
[ v3_ca ]
keyUsage=digitalSignature,keyEncipherment
subjectKeyIdentifier=hash
authorityKeyIdentifier=keyid:always,issuer:always
basicConstraints=CA:TRUE
EOF
`

# Generate keystore
openssl genrsa -out client.key 2048
openssl req -key client.key -new -out client.req <<EOF
.
.
.
.
.
localhost
.
.
.
EOF
openssl x509 -req -days 1000 -in client.req -signkey client.key -out client.crt -extensions v3_ca -extfile <(echo $CNF)
openssl pkcs12 -export -out dummyIAS.pfx -inkey client.key -in client.crt -passout pass:${STOREPASS}

keytool -keystore dummyIAS.pfx -storetype pkcs12 -changealias -alias 1 -destalias ${ALIAS} -storepass ${STOREPASS}

# Generate truststore
keytool -importcert -file client.crt -keystore dummyIAS-trust.pfx -storetype pkcs12 -alias ${ALIAS} -storepass ${STOREPASS} <<EOF
yes
EOF

rm -f client.key client.crt client.req
