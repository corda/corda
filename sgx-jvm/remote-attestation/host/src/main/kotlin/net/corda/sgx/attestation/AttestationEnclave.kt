package net.corda.sgx.attestation

import net.corda.sgx.attestation.entities.AttestationResult
import net.corda.sgx.attestation.entities.Quote
import net.corda.sgx.attestation.service.ChallengerDetails
import net.corda.sgx.enclave.ECKey
import net.corda.sgx.enclave.Enclave
import net.corda.sgx.enclave.SgxException
import net.corda.sgx.enclave.SgxStatus
import net.corda.sgx.sealing.SealedSecret
import net.corda.sgx.system.GroupIdentifier

/**
 * Enclave used in remote attestation.
 */
interface AttestationEnclave : Enclave {

    /**
     * The platform services offer an architectural enclave which provides a
     * trusted time source and a monotonic counter, which in turn can be used
     * for replay protection during nonce generation and for securely
     * calculating the length of time for which a secret shall be valid.
     */
    val usePlatformServices: Boolean

    /**
     * Create a context for the remote attestation and key exchange process.
     *
     * @param challengerKey The elliptic curve public key of the challenger
     * (NIST P-256 elliptic curve).
     *
     * @throws SgxException If unable to create context.
     */
    fun initializeKeyExchange(challengerKey: ECKey)

    /**
     * Finalize the remote attestation and key exchange process.
     */
    fun finalizeKeyExchange()

    /**
     * Get the public key of the application enclave, based on NIST P-256
     * elliptic curve, and the identifier of the EPID group to which the
     * platform belongs.
     */
    fun getPublicKeyAndGroupIdentifier(): Pair<ECKey, GroupIdentifier>

    /**
     * Process the response from the challenger and generate a quote for the
     * final step of the attestation process.
     *
     * @param challengerDetails Details received from the challenger.
     */
    fun processChallengerDetailsAndGenerateQuote(
            challengerDetails: ChallengerDetails
    ): Quote

    /**
     * Verify the attestation response received from the service provider.
     *
     * @param attestationResult The received attestation response.
     *
     * @return A pair of (1) the outcome of the validation of the CMAC over
     * the security manifest, and (2) the sealed secret, if successful.
     *
     * @throws SgxException If unable to verify the response and seal the
     * secret.
     */
    fun verifyAttestationResponse(
            attestationResult: AttestationResult
    ): Pair<SgxStatus, SealedSecret>

    /**
     * Attempt to unseal a secret inside the enclave and report the outcome of
     * the operation.
     *
     * @param sealedSecret The sealed secret provisioned by the challenger.
     *
     * @return A status code indicative of the outcome of the operation.
     */
    fun unseal(sealedSecret: SealedSecret): SgxStatus

}
