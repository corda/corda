package net.corda.sgx.bridge.wrapper

import net.corda.sgx.attestation.entities.AttestationContext
import net.corda.sgx.enclave.EnclaveIdentifier
import net.corda.sgx.sealing.ProvisionedSecret
import net.corda.sgx.sealing.SealedSecret

@Suppress("KDocMissingDocumentation")
internal object NativeWrapper {

    init {
        System.loadLibrary("corda_sgx_ra")
    }

    // enclave-management.cpp

    @Throws(Exception::class)
    external fun getDeviceStatus(): Int

    @Throws(Exception::class)
    external fun getExtendedGroupIdentifier(): ExtendedGroupIdentifierResult

    @Throws(Exception::class)
    external fun createEnclave(
            path: String,
            usePlatformServices: Boolean,
            token: LaunchToken
    ): EnclaveResult

    @Throws(Exception::class)
    external fun destroyEnclave(
            id: Long
    ): Boolean

    // remote-attestation.cpp

    @Throws(Exception::class)
    external fun initializeRemoteAttestation(
            enclaveIdentifier: EnclaveIdentifier,
            usePlatformServices: Boolean,
            challengerKey: ByteArray
    ): InitializationResult

    @Throws(Exception::class)
    external fun finalizeRemoteAttestation(
            enclaveIdentifier: EnclaveIdentifier,
            context: AttestationContext
    ): Long

    @Throws(Exception::class)
    external fun getPublicKeyAndGroupIdentifier(
            enclaveIdentifier: EnclaveIdentifier,
            context: AttestationContext,
            maxRetryCount: Int,
            retryWaitInSeconds: Int
    ): PublicKeyAndGroupIdentifier

    @Throws(Exception::class)
    external fun processServiceProviderDetailsAndGenerateQuote(
            enclaveIdentifier: EnclaveIdentifier,
            context: AttestationContext,
            publicKey: ByteArray,
            serviceProviderIdentifier: ByteArray,
            quoteType: Short,
            keyDerivationFunctionIdentifier: Short,
            signature: ByteArray,
            messageAuthenticationCode: ByteArray,
            signatureRevocationSize: Int,
            signatureRevocationList:ByteArray,
            maxRetryCount: Int,
            retryWaitInSeconds: Int
    ): QuoteResult

    @Throws(Exception::class)
    external fun verifyAttestationResponse(
            enclaveIdentifier: EnclaveIdentifier,
            context: AttestationContext,
            attestationResultMessage: ByteArray,
            aesCmac: ByteArray,
            secret: ByteArray,
            gcmIV: ByteArray,
            gcmMac: ByteArray
    ): VerificationResult

    // sealing.cpp

    @Throws(Exception::class)
    external fun sealSecret(
            enclaveIdentifier: EnclaveIdentifier,
            provisionedSecret: ProvisionedSecret
    ): SealingOperationResult

    @Throws(Exception::class)
    external fun unsealSecret(
            enclaveIdentifier: EnclaveIdentifier,
            sealedSecret: SealedSecret
    ): Long

}
