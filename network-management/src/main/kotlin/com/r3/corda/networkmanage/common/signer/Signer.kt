/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.common.signer

import net.corda.core.internal.DigitalSignatureWithCert
import net.corda.core.internal.SignedDataWithCert
import net.corda.core.serialization.serialize

/**
 * An interface for arbitrary data signing functionality.
 */
interface Signer {
    /**
     * Signs given bytes. The signing key selection strategy is left to the implementing class.
     * @return [DigitalSignatureWithCert] that encapsulates the signature and the certificate path used in the signing process.
     * @throws [AuthenticationException] if fails authentication
     */
    fun signBytes(data: ByteArray): DigitalSignatureWithCert

    fun <T : Any> signObject(obj: T): SignedDataWithCert<T> {
        val serialised = obj.serialize()
        return SignedDataWithCert(serialised, signBytes(serialised.bytes))
    }
}

class AuthenticationException : Exception()
