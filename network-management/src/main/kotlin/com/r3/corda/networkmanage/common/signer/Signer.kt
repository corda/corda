package com.r3.corda.networkmanage.common.signer

import net.corda.nodeapi.internal.network.DigitalSignatureWithCert

/**
 * An interface for arbitrary data signing functionality.
 */
interface Signer {
    /**
     * Signs given [data]. The signing key selction strategy is left to the implementing class.
     * @return [SignatureAndCertPath] that encapsulates the signature and the certificate path used in the signing process.
     * @throws [AuthenticationException] if fails authentication
     */
    fun sign(data: ByteArray): DigitalSignatureWithCert
}

class AuthenticationException : Exception()
