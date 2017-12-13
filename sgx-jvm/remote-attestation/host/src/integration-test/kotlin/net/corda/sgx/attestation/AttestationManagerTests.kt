package net.corda.sgx.attestation

import net.corda.sgx.attestation.entities.QuoteType
import net.corda.sgx.attestation.service.ISVHttpClient
import net.corda.sgx.bridge.EnclaveConfiguration
import net.corda.sgx.bridge.attestation.NativeAttestationEnclave
import net.corda.sgx.enclave.SgxStatus
import net.corda.sgx.sealing.SecretManager
import org.hamcrest.CoreMatchers.anyOf
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.*
import org.junit.After
import org.junit.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals
import kotlin.test.assertFails

@Suppress("KDocMissingDocumentation")
class AttestationManagerTests {

    private companion object {

        @JvmStatic
        private val log: Logger = LoggerFactory
                .getLogger(AttestationManagerTests::class.java)

    }

    private val usePlatformServices = false

    private lateinit var manager: AttestationManager

    private fun enclave() = NativeAttestationEnclave(
            EnclaveConfiguration.path,
            usePlatformServices
    )

    private fun enclaveAndManager(
            name: String
    ): Pair<AttestationEnclave, AttestationManager> {
        val enclave = enclave()
        manager = AttestationManager(
                enclave = enclave,
                usePlatformServices = usePlatformServices,
                secretManager = SecretManager(enclave),
                isvClient = ISVHttpClient(name = name)
        )
        return Pair(enclave, manager)
    }

    @After
    fun cleanup() {
        manager.cleanUp()
    }

    @Test
    fun `step 1 - can initialize attestation process`() {
        // No interaction with ISV.
        val (_, manager) = enclaveAndManager("Step1")
        val challenge = manager.requestChallenge()
        manager.initialize(challenge)
    }

    @Test
    fun `step 2 - can request challenge provisioning`() {
        // Request challenge from ISV.
        val (_, manager) = enclaveAndManager("Step2")
        val challenge = manager.requestChallenge()
        assertNotEquals(0, challenge.nonce.length)
    }

    @Test
    fun `step 3 - can send extended group identifier`() {
        // Send MSG0.
        val (_, manager) = enclaveAndManager("Step3")
        val challenge = manager.requestChallenge()
        manager.initialize(challenge)
        manager.sendExtendedGroupIdentifier()
    }

    @Test
    fun `step 4a - can retrieve public key and group identifier`() {
        // No interaction with ISV.
        val (enclave, manager) = enclaveAndManager("Step4a")
        val challenge = manager.requestChallenge()
        manager.initialize(challenge)
        val (publicKey, groupId) = enclave.getPublicKeyAndGroupIdentifier()
        assertNotEquals(0, publicKey.bytes.size)
        assertNotEquals(0, groupId)
    }

    @Test
    fun `step 4b - can send public key and group identifier`() {
        // Send MSG1.
        val (_, manager) = enclaveAndManager("Step4b")
        val challenge = manager.requestChallenge()
        manager.initialize(challenge)
        manager.sendPublicKeyAndGroupIdentifier()
    }

    @Test
    fun `step 5 - can receive challenger details`() {
        // Send MSG1 and receive MSG2.
        val (_, manager) = enclaveAndManager("Step5")
        val challenge = manager.requestChallenge()
        manager.initialize(challenge)
        val details = manager.sendPublicKeyAndGroupIdentifier()
        assertEquals(1, details.keyDerivationFunctionIdentifier)
        assertEquals(QuoteType.LINKABLE, details.quoteType)
    }

    @Test
    fun `step 6a - can generate quote`() {
        // Send MSG1, receive MSG2, and generate MSG3.
        val (enclave, manager) = enclaveAndManager("Step6a")
        val challenge = manager.requestChallenge()
        manager.initialize(challenge)
        val details = manager.sendPublicKeyAndGroupIdentifier()
        val quote = enclave
                .processChallengerDetailsAndGenerateQuote(details)
        assertNotNull(quote.publicKey)
        assertNotEquals(0, quote.publicKey.bytes.size)
        assertNotNull(quote.messageAuthenticationCode)
        assertNotEquals(0, quote.messageAuthenticationCode.size)
        assertNotNull(quote.payload)
        assertNotEquals(0, quote.payload.size)
        assertNotNull(quote.securityProperties)
        assertNotEquals(0, quote.securityProperties.size)
    }

    @Test
    fun `step 6b - can generate and submit quote`() {
        // Request challenge from ISV, send MSG1, receive MSG2, and send MSG3.
        val (_, manager) = enclaveAndManager("Step6b")
        val challenge = manager.requestChallenge()
        manager.initialize(challenge)
        val details = manager.sendPublicKeyAndGroupIdentifier()
        assertEquals(64, details.signature.size)
        val response = manager.generateAndSubmitQuote(challenge, details)
        assertThat(response.quoteStatus.name, anyOf(
                containsString("OK"),

                // If the attestation request to IAS returns
                // GROUP_OUT_OF_DATE, the machine needs a microcode
                // upgrade. In this case, it's up to the challenger
                // whether or not to trust the content of the quote.
                containsString("GROUP_OUT_OF_DATE")
        ))
    }

    @Test
    fun `step 7a - can verify attestation response`() {
        // Request challenge from ISV, send MSG0 and MSG1, receive MSG2, send
        // MSG3, and receive MSG4.
        val sealingHeaderSize = 560
        val (_, manager) = enclaveAndManager("Step7a")
        val challenge = manager.requestChallenge()
        manager.initialize(challenge)
        val details = manager.sendPublicKeyAndGroupIdentifier()
        val response = manager.generateAndSubmitQuote(challenge, details)
        val (macStatus, sealedSecret) =
                manager.verifyAttestationResponse(response)
        assertEquals(SgxStatus.SUCCESS, macStatus)
        assertNotNull(sealedSecret)
        assertEquals(
                sealingHeaderSize + response.secret.size,
                sealedSecret.size
        )
    }

    @Test
    fun `step 7b - cannot verify attestation response with invalid IV`() {
        // Request challenge from ISV, send MSG0 and MSG1, receive MSG2, send
        // MSG3, and receive MSG4.
        val (_, manager) = enclaveAndManager("Step7b")
        val challenge = manager.requestChallenge()
        manager.initialize(challenge)
        val details = manager.sendPublicKeyAndGroupIdentifier()
        val response = manager.generateAndSubmitQuote(challenge, details)
        response.secretIV[0] = ((response.secretIV[0] + 1) % 255).toByte()
        assertFails { manager.verifyAttestationResponse(response) }
    }

    @Test
    fun `step 8 - can seal and reuse provisioned secret`() {
        // Request challenge from ISV, send MSG0 and MSG1, receive MSG2, send
        // MSG3, and receive MSG4. Seal the secret and then unseal it again.
        val sealingHeaderSize = 560
        val (enclave, manager) = enclaveAndManager("Step8")
        val challenge = manager.requestChallenge()
        manager.initialize(challenge)
        val details = manager.sendPublicKeyAndGroupIdentifier()
        val response = manager.generateAndSubmitQuote(challenge, details)
        val (macStatus, sealedSecret) =
                manager.verifyAttestationResponse(response)
        assertEquals(SgxStatus.SUCCESS, macStatus)
        assertNotNull(sealedSecret)
        assertEquals(
                sealingHeaderSize + response.secret.size,
                sealedSecret.size
        )
        val unsealingResult = enclave.unseal(sealedSecret)
        assertEquals(SgxStatus.SUCCESS, unsealingResult)

        val invalidSecret = sealedSecret.copyOf(sealedSecret.size - 10) +
                byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
        val unsealingResultInvalid = enclave.unseal(invalidSecret)
        assertNotEquals(SgxStatus.SUCCESS, unsealingResultInvalid)
    }

}
