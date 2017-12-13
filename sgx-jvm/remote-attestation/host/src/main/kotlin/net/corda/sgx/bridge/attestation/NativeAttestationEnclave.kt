package net.corda.sgx.bridge.attestation

import net.corda.sgx.attestation.AttestationEnclave
import net.corda.sgx.attestation.entities.AttestationContext
import net.corda.sgx.attestation.entities.AttestationException
import net.corda.sgx.attestation.entities.AttestationResult
import net.corda.sgx.attestation.entities.Quote
import net.corda.sgx.attestation.service.ChallengerDetails
import net.corda.sgx.bridge.enclave.NativeEnclave
import net.corda.sgx.bridge.wrapper.NativeWrapper
import net.corda.sgx.enclave.ECKey
import net.corda.sgx.enclave.SgxException
import net.corda.sgx.enclave.SgxStatus
import net.corda.sgx.sealing.*
import net.corda.sgx.system.GroupIdentifier
import net.corda.sgx.system.SgxSystem
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.concurrent.withLock

/**
 * Enclave used in remote attestation.
 */
class NativeAttestationEnclave @JvmOverloads constructor(
        enclavePath: String,
        override val usePlatformServices: Boolean = false
) : NativeEnclave(enclavePath, usePlatformServices), AttestationEnclave {

    private companion object {

        @JvmStatic
        private val log: Logger = LoggerFactory
                .getLogger(NativeAttestationEnclave::class.java)

    }

    private val maxRetryCount: Int = 5

    private val retryDelayInSeconds: Int = 5

    private var context: AttestationContext? = null

    /**
     * Create a context for the remote attestation and key exchange process.
     *
     * @param challengerKey The elliptic curve public key of the challenger
     * (NIST P-256 elliptic curve).
     *
     * @throws SgxException If unable to create context.
     */
    override fun initializeKeyExchange(challengerKey: ECKey) {
        lock.withLock {
            val result = NativeWrapper.initializeRemoteAttestation(
                    identifier,
                    usePlatformServices,
                    challengerKey.bytes
            )
            val status = SgxSystem.statusFromCode(result.result)
            context = result.context
            if (status != SgxStatus.SUCCESS) {
                throw SgxException(status, identifier, context)
            }
        }
    }

    /**
     * Clean up and finalize the remote attestation process.
     */
    override fun finalizeKeyExchange() {
        lock.withLock {
            val oldContext = context
            if (oldContext != null) {
                val code = NativeWrapper.finalizeRemoteAttestation(
                        identifier,
                        oldContext
                )
                context = null
                val status = SgxSystem.statusFromCode(code)
                if (status != SgxStatus.SUCCESS) {
                    throw SgxException(status, identifier, oldContext)
                }
            }
        }
    }

    /**
     * Get the public key of the application enclave, based on NIST P-256
     * elliptic curve, and the identifier of the EPID group the platform
     * belongs to.
     */
    override fun getPublicKeyAndGroupIdentifier(): Pair<ECKey, GroupIdentifier> {
        lock.withLock {
            val context = context
                    ?: throw AttestationException("Not initialized.")
            val result = NativeWrapper.getPublicKeyAndGroupIdentifier(
                    identifier,
                    context,
                    maxRetryCount,
                    retryDelayInSeconds
            )
            val status = SgxSystem.statusFromCode(result.result)
            if (status != SgxStatus.SUCCESS) {
                throw SgxException(status, identifier, context)
            }

            return Pair(
                    ECKey.fromBytes(result.publicKey),
                    result.groupIdentifier
            )
        }
    }

    /**
     * Process the response from the challenger and generate a quote for the
     * final step of the attestation process.
     *
     * @param challengerDetails Details from the challenger.
     */
    override fun processChallengerDetailsAndGenerateQuote(
            challengerDetails: ChallengerDetails
    ): Quote {
        lock.withLock {
            val context = context
                    ?: throw AttestationException("Not initialized.")
            val result = NativeWrapper.processServiceProviderDetailsAndGenerateQuote(
                    identifier,
                    context,
                    challengerDetails.publicKey.bytes,
                    challengerDetails.serviceProviderIdentifier,
                    challengerDetails.quoteType.value,
                    challengerDetails.keyDerivationFunctionIdentifier,
                    challengerDetails.signature,
                    challengerDetails.messageAuthenticationCode,
                    challengerDetails.signatureRevocationList.size,
                    challengerDetails.signatureRevocationList,
                    maxRetryCount,
                    retryDelayInSeconds
            )
            val status = SgxSystem.statusFromCode(result.result)
            if (status != SgxStatus.SUCCESS) {
                throw SgxException(status, identifier, context)
            }
            return Quote(
                    result.messageAuthenticationCode,
                    ECKey.fromBytes(result.publicKey),
                    result.securityProperties,
                    result.payload
            )
        }
    }

    /**
     * Verify the attestation response received from the service provider.
     *
     * @param attestationResult The received attestation response.
     *
     * @return A pair of (1) the outcome of the validation of the CMAC over
     * the security manifest, and (2) the sealed secret, if successful.
     *
     * @throws SgxException If unable to verify the response or seal the
     * secret.
     */
    override fun verifyAttestationResponse(
            attestationResult: AttestationResult
    ): Pair<SgxStatus, SealedSecret> {
        lock.withLock {
            val context = context
                    ?: throw AttestationException("Not initialized.")
            val result = NativeWrapper.verifyAttestationResponse(
                    identifier,
                    context,
                    attestationResult.attestationResultMessage,
                    attestationResult.aesCMAC,
                    attestationResult.secret,
                    attestationResult.secretIV,
                    attestationResult.secretHash
            )
            val cmacValidationStatus = SgxSystem.statusFromCode(
                    result.cmacValidationStatus
            )

            if (cmacValidationStatus != SgxStatus.SUCCESS) {
                if (attestationResult.aesCMAC.isEmpty()) {
                    log.warn("No CMAC available")
                } else {
                    log.warn(
                            "Failed to validate AES-CMAC ({}).",
                            cmacValidationStatus.name
                    )
                }
            }

            val status = SgxSystem.statusFromCode(result.result)
            if (status != SgxStatus.SUCCESS) {
                throw SgxException(status, identifier, context)
            }

            return Pair(cmacValidationStatus, result.secret)
        }
    }

    /**
     * Attempt to unseal a secret inside the enclave and report the outcome of
     * the operation.
     */
    override fun unseal(sealedSecret: SealedSecret): SgxStatus {
        lock.withLock {
            val result = NativeWrapper.unsealSecret(identifier, sealedSecret)
            return SgxSystem.statusFromCode(result)
        }
    }

}
