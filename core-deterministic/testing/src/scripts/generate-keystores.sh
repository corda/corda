#!/bin/sh

STOREPASS=deterministic
STORE=txsignature.pfx
ALIAS=tx

if !(openssl ecparam -name secp256k1 -genkey -noout -out privateKey.pem); then
    echo "Failed to generate EC private key"
    exit 1
fi
openssl req -new -key privateKey.pem -x509 -out server.crt -days 1000 <<EOF
.
.
.
.
.
localhost
.
EOF

openssl pkcs12 -export -out ${STORE} -inkey privateKey.pem -in server.crt -passout pass:${STOREPASS}

if !(keytool -keystore ${STORE} -storetype pkcs12 -changealias -alias 1 -destalias ${ALIAS} -storepass ${STOREPASS}); then
    rm ${STORE}
    exit 1
fi

rm -f *.pem *.crt
