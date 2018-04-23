#!/bin/sh

ALIAS=jetty
KEYPASS=attestation
STOREPASS=attestation

rm -f keystore truststore

# Generate the keystore and truststore that will allow us to enable HTTPS.
# Both keystore and truststore are expected to use password "attestation".

keytool -keystore keystore -storetype pkcs12 -genkey -alias ${ALIAS} -dname CN=localhost -keyalg RSA -keypass ${KEYPASS} -storepass ${STOREPASS}
keytool -keystore keystore -storetype pkcs12 -export -alias ${ALIAS} -keyalg RSA -file jetty.cert -keypass ${KEYPASS} -storepass ${STOREPASS}
keytool -keystore truststore -storetype pkcs12 -import -alias ${ALIAS} -file jetty.cert -keypass ${KEYPASS} -storepass ${STOREPASS} <<EOF
yes
EOF

rm -f jetty.cert
