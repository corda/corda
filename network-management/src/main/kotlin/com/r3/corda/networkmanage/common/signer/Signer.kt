package com.r3.corda.networkmanage.common.signer

import net.corda.core.crypto.DigitalSignature
import net.corda.core.serialization.CordaSerializable
import java.security.cert.CertPath

@CordaSerializable
data class SignatureAndCertPath(val signature: DigitalSignature, val certPath: CertPath)

/**
 * An interface for arbitrary data signing functionality.
 */
interface Signer {

    /**
     * Signs given [data]. The signing key selction strategy is left to the implementing class.
     * @return [SignatureAndCertPath] that encapsulates the signature and the certificate path used in the signing process.
     */
    fun sign(data: ByteArray): SignatureAndCertPath?
}