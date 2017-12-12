#!/bin/sh

rm -f isv.pfx ias.pfx

ALIAS=isv
KEYPASS=attestation
STOREPASS=attestation

DIRHOME=$(dirname $0)
KEYFILE=${DIRHOME}/client.key
CRTFILE=${DIRHOME}/client.crt
INTEL_CRTFILE=${DIRHOME}/AttestationReportSigningCACert.pem

openssl verify -x509_strict -purpose sslclient -CAfile ${CRTFILE} ${CRTFILE}

if [ ! -r ${KEYFILE} ]; then
    echo "Development private key missing. This is the key that IAS expects our HTTP client to be using for Mutual TLS."
    exit 1
fi

openssl pkcs12 -export -out client.pfx -inkey ${KEYFILE} -in ${CRTFILE} -passout pass:${STOREPASS}

keytool -importkeystore -srckeystore client.pfx -srcstoretype pkcs12 -destkeystore isv.pfx -deststoretype pkcs12 -srcstorepass ${STOREPASS} -deststorepass ${STOREPASS}

keytool -keystore isv.pfx -storetype pkcs12 -changealias -alias 1 -destalias ${ALIAS} -storepass ${STOREPASS}

rm -rf client.pfx

# Generate trust store for connecting with IAS.
if [ -r ${INTEL_CRTFILE} ]; then
    keytool -import -keystore ias.pfx -storetype pkcs12 -file ${INTEL_CRTFILE} -alias ias -storepass ${STOREPASS} <<EOF
yes
EOF
fi
