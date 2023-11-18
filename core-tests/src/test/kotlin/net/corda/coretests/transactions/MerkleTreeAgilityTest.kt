package net.corda.coretests.transactions

import net.corda.core.contracts.ComponentGroupEnum.COMMANDS_GROUP
import net.corda.core.contracts.ComponentGroupEnum.INPUTS_GROUP
import net.corda.core.contracts.ComponentGroupEnum.NOTARY_GROUP
import net.corda.core.contracts.ComponentGroupEnum.OUTPUTS_GROUP
import net.corda.core.contracts.ComponentGroupEnum.SIGNERS_GROUP
import net.corda.core.contracts.ComponentGroupEnum.TIMEWINDOW_GROUP
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.PrivacySalt
import net.corda.core.contracts.TimeWindow
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.DigestService
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.generateKeyPair
import net.corda.core.crypto.internal.DigestAlgorithmFactory
import net.corda.core.internal.SHA256BLAKE2s256DigestAlgorithm
import net.corda.core.internal.accessAvailableComponentHashes
import net.corda.core.internal.accessAvailableComponentNonces
import net.corda.core.internal.accessGroupMerkleRoots
import net.corda.core.serialization.serialize
import net.corda.core.transactions.ComponentGroup
import net.corda.core.transactions.WireTransaction

import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyState
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.core.dummyCommand
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals

class MerkleTreeAgilityTest {
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
    private val stateRef3 = StateRef(SecureHash.randomSHA256(), 2)
    private val stateRef4 = StateRef(SecureHash.randomSHA256(), 3)

    private val singleInput = listOf(stateRef1) // 1 elements.
    private val threeInputs = listOf(stateRef1, stateRef2, stateRef3) // 3 elements.
    private val fourInputs = listOf(stateRef1, stateRef2, stateRef3, stateRef4) // 4 elements.

    private val outputs = listOf(dummyOutState, dummyOutState.copy(notary = BOB)) // 2 elements.
    private val commands = listOf(dummyCommand(DUMMY_KEY_1.public, DUMMY_KEY_2.public)) // 1 element.
    private val notary = DUMMY_NOTARY
    private val timeWindow = TimeWindow.fromOnly(Instant.now())
    private val privacySalt: PrivacySalt = PrivacySalt()

    private val singleInputsGroup by lazy { ComponentGroup(INPUTS_GROUP.ordinal, singleInput.map { it.serialize() }) }
    private val threeInputsGroup by lazy { ComponentGroup(INPUTS_GROUP.ordinal, threeInputs.map { it.serialize() }) }
    private val fourInputsGroup by lazy { ComponentGroup(INPUTS_GROUP.ordinal, fourInputs.map { it.serialize() }) }
    private val outputGroup by lazy { ComponentGroup(OUTPUTS_GROUP.ordinal, outputs.map { it.serialize() }) }
    private val commandGroup by lazy { ComponentGroup(COMMANDS_GROUP.ordinal, commands.map { it.value.serialize() }) }
    private val notaryGroup by lazy { ComponentGroup(NOTARY_GROUP.ordinal, listOf(notary.serialize())) }
    private val timeWindowGroup by lazy { ComponentGroup(TIMEWINDOW_GROUP.ordinal, listOf(timeWindow.serialize())) }
    private val signersGroup by lazy { ComponentGroup(SIGNERS_GROUP.ordinal, commands.map { it.signers.serialize() }) }

    private val componentGroupsSingle by lazy {
        listOf(singleInputsGroup, outputGroup, commandGroup, notaryGroup, timeWindowGroup, signersGroup)
    }

    private val componentGroupsFourInputs by lazy {
        listOf(fourInputsGroup, outputGroup, commandGroup, notaryGroup, timeWindowGroup, signersGroup)
    }

    private val componentGroupsThreeInputs by lazy {
        listOf(threeInputsGroup, outputGroup, commandGroup, notaryGroup, timeWindowGroup, signersGroup)
    }

    private val defaultDigestService = DigestService.sha2_256
    private val customDigestService = DigestService("SHA256-BLAKE2S256-TEST")

    @Before
    fun before() {
        DigestAlgorithmFactory.registerClass(SHA256BLAKE2s256DigestAlgorithm::class.java.name)
    }

    @Test(timeout = 300_000)
    fun `component nonces are correct for custom preimage resistant hash algo`() {
        val wireTransaction = WireTransaction(componentGroups = componentGroupsFourInputs, privacySalt = privacySalt, digestService = customDigestService)
        val expected = componentGroupsFourInputs.associate {
            it.groupIndex to it.components.mapIndexed { componentIndexInGroup, _ ->
                customDigestService.computeNonce(privacySalt, it.groupIndex, componentIndexInGroup)
            }
        }

        assertEquals(expected, wireTransaction.accessAvailableComponentNonces())
    }

    @Test(timeout = 300_000)
    fun `component nonces are correct for default SHA256 hash algo`() {
        val wireTransaction = WireTransaction(componentGroups = componentGroupsFourInputs, privacySalt = privacySalt)
        val expected = componentGroupsFourInputs.associate {
            it.groupIndex to it.components.mapIndexed { componentIndexInGroup, componentBytes ->
                defaultDigestService.componentHash(componentBytes, privacySalt, it.groupIndex, componentIndexInGroup)
            }
        }

        assertEquals(expected, wireTransaction.accessAvailableComponentNonces())
    }

    @Test(timeout = 300_000)
    fun `custom algorithm transaction pads leaf in single component component group`() {
        val wtx = WireTransaction(componentGroups = componentGroupsSingle, privacySalt = privacySalt, digestService = customDigestService)

        val inputsTreeLeaves: List<SecureHash> = wtx.accessAvailableComponentHashes()[INPUTS_GROUP.ordinal]!!
        val expected = customDigestService.hash(inputsTreeLeaves[0].bytes + customDigestService.zeroHash.bytes)

        assertEquals(expected, wtx.accessGroupMerkleRoots()[INPUTS_GROUP.ordinal]!!)
    }

    @Test(timeout = 300_000)
    fun `default algorithm transaction does not pad leaf in single component component group`() {
        val wtx = WireTransaction(componentGroups = componentGroupsSingle, privacySalt = privacySalt, digestService = defaultDigestService)

        val inputsTreeLeaves: List<SecureHash> = wtx.accessAvailableComponentHashes()[INPUTS_GROUP.ordinal]!!
        val expected = inputsTreeLeaves[0]

        assertEquals(expected, wtx.accessGroupMerkleRoots()[INPUTS_GROUP.ordinal]!!)
    }

    @Test(timeout = 300_000)
    fun `custom algorithm transaction has expected root for four components component group tree`() {
        val wtx = WireTransaction(componentGroups = componentGroupsFourInputs, privacySalt = privacySalt, digestService = customDigestService)

        val inputsTreeLeaves: List<SecureHash> = wtx.accessAvailableComponentHashes()[INPUTS_GROUP.ordinal]!!
        val h1 = customDigestService.hash(inputsTreeLeaves[0].bytes + inputsTreeLeaves[1].bytes)
        val h2 = customDigestService.hash(inputsTreeLeaves[2].bytes + inputsTreeLeaves[3].bytes)
        val expected = customDigestService.hash(h1.bytes + h2.bytes)

        assertEquals(expected, wtx.accessGroupMerkleRoots()[INPUTS_GROUP.ordinal]!!)
    }

    @Test(timeout = 300_000)
    fun `default algorithm transaction has expected root for four components component group tree`() {
        val wtx = WireTransaction(componentGroups = componentGroupsFourInputs, privacySalt = privacySalt, digestService = defaultDigestService)

        val inputsTreeLeaves: List<SecureHash> = wtx.accessAvailableComponentHashes()[INPUTS_GROUP.ordinal]!!
        val h1 = defaultDigestService.hash(inputsTreeLeaves[0].bytes + inputsTreeLeaves[1].bytes)
        val h2 = defaultDigestService.hash(inputsTreeLeaves[2].bytes + inputsTreeLeaves[3].bytes)
        val expected = defaultDigestService.hash(h1.bytes + h2.bytes)

        assertEquals(expected, wtx.accessGroupMerkleRoots()[INPUTS_GROUP.ordinal]!!)
    }

    @Test(timeout = 300_000)
    fun `custom algorithm transaction has expected root for three components component group tree`() {
        val wtx = WireTransaction(componentGroups = componentGroupsThreeInputs, privacySalt = privacySalt, digestService = customDigestService)

        val inputsTreeLeaves: List<SecureHash> = wtx.accessAvailableComponentHashes()[INPUTS_GROUP.ordinal]!!
        val h1 = customDigestService.hash(inputsTreeLeaves[0].bytes + inputsTreeLeaves[1].bytes)
        val h2 = customDigestService.hash(inputsTreeLeaves[2].bytes + customDigestService.zeroHash.bytes)
        val expected = customDigestService.hash(h1.bytes + h2.bytes)

        assertEquals(expected, wtx.accessGroupMerkleRoots()[INPUTS_GROUP.ordinal]!!)
    }

    @Test(timeout = 300_000)
    fun `default algorithm transaction has expected root for three components component group tree`() {
        val wtx = WireTransaction(componentGroups = componentGroupsThreeInputs, privacySalt = privacySalt, digestService = defaultDigestService)

        val inputsTreeLeaves: List<SecureHash> = wtx.accessAvailableComponentHashes()[INPUTS_GROUP.ordinal]!!
        val h1 = defaultDigestService.hash(inputsTreeLeaves[0].bytes + inputsTreeLeaves[1].bytes)
        val h2 = defaultDigestService.hash(inputsTreeLeaves[2].bytes + defaultDigestService.zeroHash.bytes)
        val expected = defaultDigestService.hash(h1.bytes + h2.bytes)

        assertEquals(expected, wtx.accessGroupMerkleRoots()[INPUTS_GROUP.ordinal]!!)
    }
}