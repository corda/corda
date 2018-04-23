package com.r3.corda.networkmanage.common.sockets

import net.corda.core.serialization.CordaSerializable
import java.security.cert.X509CRL
import java.time.Instant

@CordaSerializable
data class CertificateRevocationListSubmission(val list: X509CRL,
                                               val signer: String,
                                               val revocationTime: Instant)