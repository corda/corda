package net.corda.attestation.host.sgx.entities

import net.corda.attestation.host.sgx.sealing.ProvisionedSecret

/**
 * The outcome of a remote attestation process.
 */
class AttestationResult(
    /**
     * The received attestation result message.
     */
    val attestationResultMessage: ByteArray?,

    /**
     * The CMAC over the attestation result message.
     */
    val aesCMAC: ByteArray,

    /**
     * Provisioned, encrypted secret if the attestation was successful.
     */
    val secret: ProvisionedSecret,

    /**
     * The initialization vector used as part of the decryption.
     */
    val secretIV: ByteArray,

    /**
     * The GCM MAC returned as part of the attestation response.
     */
    val secretHash: ByteArray
)
