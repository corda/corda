#!/bin/sh

STOREPASS=attestation
ALIAS=isv-svc

rm -f isv-svc.pfx

openssl ecparam -name secp256r1 -genkey -noout -out privateKey.pem
openssl req -new -key privateKey.pem -x509 -out server.crt -days 1000 <<EOF
.
.
.
.
.
localhost
.
EOF

openssl pkcs12 -export -out isv-svc.pfx -inkey privateKey.pem -in server.crt -passout pass:${STOREPASS}

keytool -keystore isv-svc.pfx -storetype pkcs12 -changealias -alias 1 -destalias ${ALIAS} -storepass ${STOREPASS}

rm -f *.pem *.crt
