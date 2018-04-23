#!/bin/sh

rm -f ias.pfx

STOREPASS=attestation

DIRHOME=$(dirname $0)
INTEL_CRTFILE=${DIRHOME}/AttestationReportSigningCACert.pem

## Generate trust store for Intel's certificate.
if [ -r ${INTEL_CRTFILE} ]; then
    keytool -import -keystore ias.pfx -storetype pkcs12 -file ${INTEL_CRTFILE} -alias ias -storepass ${STOREPASS} <<EOF
yes
EOF
fi
