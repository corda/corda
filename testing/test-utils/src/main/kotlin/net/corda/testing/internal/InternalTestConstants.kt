package net.corda.testing.internal

import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair

val DEV_INTERMEDIATE_CA: CertificateAndKeyPair by lazy { net.corda.nodeapi.internal.DEV_INTERMEDIATE_CA }

val DEV_ROOT_CA: CertificateAndKeyPair by lazy { net.corda.nodeapi.internal.DEV_ROOT_CA }
