/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.doorman.signer

import com.r3.corda.networkmanage.common.signer.Signer
import net.corda.core.crypto.Crypto
import net.corda.core.internal.DigitalSignatureWithCert
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate

/**
 *  This local signer is intended to be used in testing environment where hardware signing module is not available.
 */
class LocalSigner(private val signingKey: PrivateKey, private val signingCert: X509Certificate) : Signer {
    constructor(certAndKeyPair: CertificateAndKeyPair) : this(certAndKeyPair.keyPair.private, certAndKeyPair.certificate)

    override fun signBytes(data: ByteArray): DigitalSignatureWithCert {
        return DigitalSignatureWithCert(signingCert, Crypto.doSign(signingKey, data))
    }
}
