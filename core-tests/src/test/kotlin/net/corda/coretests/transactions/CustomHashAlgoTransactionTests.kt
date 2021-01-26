package net.corda.coretests.transactions

import net.corda.core.contracts.*
import net.corda.core.contracts.ComponentGroupEnum.*
import net.corda.core.crypto.*
import net.corda.core.crypto.internal.DigestAlgorithmFactory
import net.corda.core.internal.accessAvailableComponentHashes
import net.corda.core.internal.accessAvailableComponentNonces
import net.corda.core.serialization.serialize
import net.corda.core.transactions.*
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyState
import net.corda.testing.core.*
import org.bouncycastle.crypto.digests.Blake2sDigest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import kotlin.test.*

class CustomHashAlgoTransactionTests {
    private companion object {
        val DUMMY_KEY_1 = generateKeyPair()
        val DUMMY_KEY_2 = generateKeyPair()
        val BOB = TestIdentity(BOB_NAME, 80).party
        val DUMMY_NOTARY = TestIdentity(DUMMY_NOTARY_NAME, 20).party
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private val dummyOutState = TransactionState(DummyState(0), DummyContract.PROGRAM_ID, DUMMY_NOTARY)
    private val stateRef1 = StateRef(SecureHash.randomSHA256(), 0)
    private val stateRef2 = StateRef(SecureHash.randomSHA256(), 1)
    private val stateRef3 = StateRef(SecureHash.randomSHA256(), 0)

    private val inputs = listOf(stateRef1, stateRef2, stateRef3) // 3 elements.
    private val outputs = listOf(dummyOutState, dummyOutState.copy(notary = BOB)) // 2 elements.
    private val commands = listOf(dummyCommand(DUMMY_KEY_1.public, DUMMY_KEY_2.public)) // 1 element.
    private val notary = DUMMY_NOTARY
    private val timeWindow = TimeWindow.fromOnly(Instant.now())
    private val privacySalt: PrivacySalt = PrivacySalt()

    private val inputGroup by lazy { ComponentGroup(INPUTS_GROUP.ordinal, inputs.map { it.serialize() }) }
    private val outputGroup by lazy { ComponentGroup(OUTPUTS_GROUP.ordinal, outputs.map { it.serialize() }) }
    private val commandGroup by lazy { ComponentGroup(COMMANDS_GROUP.ordinal, commands.map { it.value.serialize() }) }
    private val notaryGroup by lazy { ComponentGroup(NOTARY_GROUP.ordinal, listOf(notary.serialize())) }
    private val timeWindowGroup by lazy { ComponentGroup(TIMEWINDOW_GROUP.ordinal, listOf(timeWindow.serialize())) }
    private val signersGroup by lazy { ComponentGroup(SIGNERS_GROUP.ordinal, commands.map { it.signers.serialize() }) }

    private val componentGroupsA by lazy {
        listOf(
                inputGroup,
                outputGroup,
                commandGroup,
                notaryGroup,
                timeWindowGroup,
                signersGroup
        )
    }

    class BLAKE2s256DigestAlgorithm : DigestAlgorithm {
        override val algorithm = "BLAKE_TEST"

        override val digestLength = 32

        override fun digest(bytes: ByteArray): ByteArray {
            val blake2s256 = Blake2sDigest(null, digestLength, null, "12345678".toByteArray())
            blake2s256.reset()
            blake2s256.update(bytes, 0, bytes.size)
            val hash = ByteArray(digestLength)
            blake2s256.doFinal(hash, 0)
            return hash
        }

        /**
         * Computes the digest of the [ByteArray] which is resistant to pre-image attacks.
         * Default implementation provides double hashing, but can it be changed to single hashing or something else for better performance.
         */
        override fun preImageResistantDigest(bytes: ByteArray): ByteArray = digest(bytes)

        /**
         * Computes the digest of the [ByteArray] which is resistant to pre-image attacks.
         * Default implementation provides double hashing, but can it be changed to single hashing or something else for better performance.
         */
        override fun nonceDigest(bytes: ByteArray): ByteArray = digest(bytes)
    }

    private val customDigestService = DigestService("BLAKE_TEST")
    private val defaultDigestService = DigestService.sha2_256

    @Before
    fun before() {
        DigestAlgorithmFactory.registerClass(BLAKE2s256DigestAlgorithm::class.java.name)
    }

    @Test(timeout = 300_000)
    fun `component hashes are correct for custom preimage resistant hash algo`() {
        val wireTransaction = WireTransaction(componentGroups = componentGroupsA, privacySalt = privacySalt, digestService = customDigestService)
        val expected = componentGroupsA.associate {
            it.groupIndex to it.components.mapIndexed { componentIndexInGroup, _ ->
                customDigestService.computeNonce(privacySalt, it.groupIndex, componentIndexInGroup)
            }
        }

        assertEquals(expected, wireTransaction.accessAvailableComponentNonces())
    }

    @Test(timeout = 300_000)
    fun `component hashes are correct for default SHA256 hash algo`() {
        val wireTransaction = WireTransaction(componentGroups = componentGroupsA, privacySalt = privacySalt)
        val expected = componentGroupsA.associate {
            it.groupIndex to it.components.mapIndexed { componentIndexInGroup, componentBytes ->
                defaultDigestService.componentHash(componentBytes, privacySalt, it.groupIndex, componentIndexInGroup)
            }
        }

        assertEquals(expected, wireTransaction.accessAvailableComponentNonces())
    }
}

