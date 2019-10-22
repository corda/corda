#!/bin/sh

KEYPASS=deterministic
STOREPASS=deterministic

rm -f keystore testing.cert

keytool -keystore keystore -storetype pkcs12 -genkey -dname 'CN=localhost, O=R3, L=London, C=UK' -keyalg RSA -validity 3650 -keypass ${KEYPASS} -storepass ${STOREPASS}
keytool -keystore keystore -storetype pkcs12 -export -keyalg RSA -file testing.cert -keypass ${KEYPASS} -storepass ${STOREPASS}

rm -f keystore
