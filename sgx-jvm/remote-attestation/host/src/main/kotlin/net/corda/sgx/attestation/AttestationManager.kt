package net.corda.sgx.attestation

import net.corda.sgx.attestation.entities.*
import net.corda.sgx.attestation.service.ChallengerDetails
import net.corda.sgx.attestation.service.ISVClient
import net.corda.sgx.enclave.SgxStatus
import net.corda.sgx.sealing.SealedSecret
import net.corda.sgx.sealing.SecretManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Remote attestation flow.
 *
 * @property enclave The enclave used for the remote attestation process.
 * @property secretManager The secret manager used to store and manage secrets.
 * @property isvClient Client used to communicate with the remote attestation
 * service provider.
 * @property usePlatformServices Indication of whether to use platform services
 * or not to. Platform services supplies a trusted time source in addition to a
 * monotonic counter, which in combination can be used to protect against
 * replay attacks during nonce generation and to securely set time validity of
 * secrets.
 */
class AttestationManager @JvmOverloads constructor(
        private val enclave: AttestationEnclave,
        private val secretManager: SecretManager,
        private val isvClient: ISVClient,
        private val usePlatformServices: Boolean = false
) {

    private companion object {

        @JvmStatic
        private val log: Logger = LoggerFactory
                .getLogger(AttestationManager::class.java)

    }

    /**
     * The backbone of the remote attestation flow. Ensure that the attestation
     * context gets closed (if needed) upon completion.
     */
    fun attest() {
        val challenge = requestChallenge()
        initialize(challenge)
        try {
            sendExtendedGroupIdentifier()
            val details = sendPublicKeyAndGroupIdentifier()
            val response = generateAndSubmitQuote(challenge, details)
            val (status, secret) = verifyAttestationResponse(response)
            if (status != SgxStatus.SUCCESS) {
                throw AttestationException(
                        "Failed to validate CMAC manifest (${status.name})"
                )
            }
            persistSecret(secret)
        } finally {
            cleanUp()
        }
    }

    /**
     * Ask service provider to provision challenge.
     */
    fun requestChallenge(): Challenge {
        // Request challenge from service provider.
        try {
            return isvClient.requestChallenge()
        } catch (ex: Exception) {
            log.error("Failed to request challenge.", ex)
            throw ex
        }
    }

    /**
     * Activate enclave and initialize the key exchange and remote attestation
     * flow.
     */
    fun initialize(challenge: Challenge) {
        // Make sure the system is SGX-enabled and that we have a running
        // enclave to work with.
        try {
            enclave.activate()
        } catch (ex: Exception) {
            log.error("Failed to activate enclave.", ex)
            throw ex
        }

        // If we have already been through an attestation, chances are that the
        // existing sealed secret might still be valid. So, if we have a sealed
        // secret, we first try to unseal it to see whether we need to
        // re-attest or not.
        if (!checkIfAttestationIsNeeded()) {
            // The secret was already provisioned and is still valid, so we can
            // relinquish control to the enclave to operate further on the
            // provisioned data.
            return
        }

        // Initialize the key exchange and remote attestation process. Platform
        // services supplies a trusted time source in addition to a monotonic
        // counter, which in combination can be used to protect against replay
        // attacks during nonce generation and to securely set time validity of
        // secrets.

        try {
            enclave.initializeKeyExchange(challenge.publicKey)
        } catch (ex: Exception) {
            log.error("Failed to initialize key exchange.", ex)
            throw ex
        }
    }


    /**
     * Send extended group identifier to service provider.
     */
    fun sendExtendedGroupIdentifier() {
        // Next, we need to send our extended group identifier to let the
        // service provider know what EPID group to use during remote
        // attestation. This corresponds to message 0 in the Intel remote
        // attestation flow.
        val egid = try {
            enclave.system.getExtendedGroupIdentifier()
        } catch (ex: Exception) {
            log.error("Failed to get extended group identifier.", ex)
            throw ex
        }
        try {
            if (!isvClient.validateExtendedGroupIdentifier(egid)) {
                throw AttestationException("Extended group not accepted.")
            }
        } catch (ex: Exception) {
            log.error("Failed validating extended group identifier.", ex)
            throw ex
        }
    }

    /**
     * Send public key and group identifier, and receive details about the
     * challenger.
     */
    fun sendPublicKeyAndGroupIdentifier(): ChallengerDetails {
        // Call into the Intel Provisioning Server to provision attestation
        // key, if necessary. Otherwise, just go ahead and retrieve the
        // public key and the group identifier.
        val (publicKey, gid) = try {
            enclave.getPublicKeyAndGroupIdentifier()
        } catch (ex: Exception) {
            log.error("Failed to get public key and group identifier.", ex)
            throw ex
        }

        // Send our public key and our group identifier to the service
        // provider, which in turn will check our details against the current
        // version of the revocation list. The service provider will forward
        // this list together with its service provider ID, signatures, quote
        // type, etc. to us (the client). This request corresponds to message 1
        // in the Intel remote attestation flow.
        try {
            return isvClient.sendPublicKeyAndGroupIdentifier(publicKey, gid)
        } catch (ex: Exception) {
            log.error("Failed sending PK and group identifier to ISV.", ex)
            throw ex
        }
    }

    /**
     * Process response from the service provider, generate a quote and send
     * it to the service provider to get it verified.
     */
    fun generateAndSubmitQuote(
            challenge: Challenge,
            details: ChallengerDetails
    ): AttestationResult {
        // Process response from the service provider and generate a quote.
        val quote = try {
            enclave.processChallengerDetailsAndGenerateQuote(details)
        } catch (ex: Exception) {
            log.error("Failed to process challenger details and generate " +
                    "quote.", ex)
            throw ex
        }

        // Send the quote to the service provider, which in turn will verify
        // the attestation evidence with the Intel attestation service.
        return submitQuote(challenge, quote)
    }

    /**
     * Send quote to service provider, which in turn will verify the
     * attestation evidence with the Intel attestation service.
     */
    fun submitQuote(
            challenge: Challenge,
            quote: Quote
    ): AttestationResult {
        // Send the quote to the service provider, which in turn will verify
        // the attestation evidence with the Intel attestation service.
        val attestationResponse = try {
            isvClient.submitQuote(challenge, quote)
        } catch (ex: Exception) {
            log.error("Failed to submit quote to ISV.", ex)
            throw ex
        }
        if (!attestationResponse.isSuccessful) {
            val status = attestationResponse.status
            throw AttestationException("Failed to verify quote. $status")
        }
        return attestationResponse
    }

    /**
     * Verify the received attestation response from service provider.
     */
    fun verifyAttestationResponse(
            response: AttestationResult
    ): Pair<SgxStatus, SealedSecret> {
        if (response.quoteStatus == QuoteStatus.GROUP_OUT_OF_DATE) {
            log.warn(QuoteStatus.GROUP_OUT_OF_DATE.description)
        } else if (response.quoteStatus != QuoteStatus.OK) {
            val code = response.quoteStatus
            throw AttestationException("${code.description} (${code.name})")
        }

        if (response.secretHash.isEmpty()) {
            throw AttestationException("No secret hash available")
        }

        // The attestation service is happy with the quote, and the remote
        // attestation has been verified. Now, we need to verify the response,
        // decrypt the secret and seal it in the enclave.
        try {
            return enclave.verifyAttestationResponse(response)
        } catch (ex: Exception) {
            log.error("Failed to verify attestation response.", ex)
            throw ex
        }
    }

    /**
     * Finalize remote attestation and key exchange process.
     */
    fun cleanUp() {
        try {
            // The secret has been provisioned, so we are now ready to
            // relinquish control to the enclave to operate further on the
            // provisioned data.
            enclave.finalizeKeyExchange()
        } catch (ex: Exception) {
            log.error("Failed to finalize key exchange.", ex)
            throw ex
        }
    }

    /**
     * Persist the secret sealed by the enclave.
     */
    fun persistSecret(secret: SealedSecret) {
        // The attestation response was verified, so we persist the secret.
        try {
            secretManager.persistSealedSecret(secret)
        } catch (ex: Exception) {
            log.error("Failed to persist sealed secret.", ex)
            throw ex
        }
    }

    /**
     * Checks if we already have a sealed secret from a previous attestation
     * that is still valid.
     */
    private fun checkIfAttestationIsNeeded(): Boolean {
        // If we do not have a secret at hand, we need to be provisioned one.
        // This is accomplished by running through the attestation process. If
        // we have already been provisioned a secret, that does not necessarily
        // mean that we are validly attested. We also need to check that we can
        // unseal said secret and consequently that the attestation has not
        // expired.
        return try {
            !secretManager.isValid()
        } catch (ex: Exception) {
            log.warn("Failed to unseal and validate secret.", ex)
            true
        }
    }

}
