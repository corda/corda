package net.corda.coretests.transactions

import net.corda.core.contracts.HashAttachmentConstraint
import net.corda.core.contracts.NotaryInstruction
import net.corda.core.contracts.PrivacySalt
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.contracts.TransactionState
import net.corda.core.contracts.TransactionVerificationException.UnsupportedHashTypeException
import net.corda.core.crypto.DigestService
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.keyrotation.crossprovider.KeyRotationProof
import net.corda.core.crypto.keyrotation.crossprovider.KeyRotationProofChain
import net.corda.core.crypto.secureRandomBytes
import net.corda.core.internal.HashAgility
import net.corda.core.internal.PLATFORM_VERSION
import net.corda.core.internal.PlatformVersionSwitches.CROSS_PROVIDER_KEY_ROTATION
import net.corda.core.internal.RPC_UPLOADER
import net.corda.core.internal.digestService
import net.corda.core.node.ZoneVersionTooLowException
import net.corda.core.transactions.TransactionBuilder
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyState
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.core.dummyCommand
import net.corda.testing.node.MockServices
import net.corda.testing.node.internal.cordappWithPackages
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.time.Instant
import kotlin.io.path.inputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@Suppress("INVISIBLE_MEMBER")
class TransactionBuilderWithKeyRotationProofTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private val notary = TestIdentity(DUMMY_NOTARY_NAME).party
    private val dummyKeyRotationProof = KeyRotationProofChain(listOf(KeyRotationProof(notary.owningKey, notary.owningKey, secureRandomBytes(32))))
    private val dummyKeyRotationProof2 = KeyRotationProofChain(listOf(KeyRotationProof(notary.owningKey, notary.owningKey, secureRandomBytes(32))))
    private val dummyKeyRotationMap = mapOf(notary.owningKey to dummyKeyRotationProof)
    private val dummyKeyRotationMap2 = mapOf(notary.owningKey to dummyKeyRotationProof2)
    private val dummyCommand = dummyCommand( notary.owningKey).copy(keyRotationProofChainMap = dummyKeyRotationMap)
    private val dummyCommand2 = dummyCommand( notary.owningKey).copy(keyRotationProofChainMap = dummyKeyRotationMap2)
    private val services = MockServices(
            listOf("net.corda.testing.contracts"),
            TestIdentity(ALICE_NAME),
            testNetworkParameters(minimumPlatformVersion = PLATFORM_VERSION)
    )
    private val contractAttachmentId = services.attachments.getLatestContractAttachments(DummyContract.PROGRAM_ID)[0]

    @Test(timeout=300_000)
	fun `bare minimum issuance tx`() {
        val outputState = TransactionState(
                data = DummyState(),
                contract = DummyContract.PROGRAM_ID,
                notary = notary,
                constraint = HashAttachmentConstraint(contractAttachmentId)
        )
        val builder = TransactionBuilder()
                .addOutputState(outputState)
                .addCommand(dummyCommand)
        val wtx = builder.toWireTransaction(services)
        assertThat(wtx.outputs).containsOnly(outputState)
        assertThat(wtx.commands).containsOnly(dummyCommand)
        assertThat(wtx.networkParametersHash).isEqualTo(services.networkParametersService.currentHash)
        // From 4.12 attachments are added to the new component group by default
        assertThat(wtx.nonLegacyAttachments).isNotEmpty
        assertThat(wtx.legacyAttachments).isEmpty()
        assertThat(wtx.notaryInstructions).isEmpty()
    }

    @Test(timeout=300_000)
	fun `automatic hash constraint`() {
        val outputState = TransactionState(data = DummyState(), contract = DummyContract.PROGRAM_ID, notary = notary)
        val builder = TransactionBuilder()
                .addOutputState(outputState)
                .addCommand(dummyCommand)
        val wtx = builder.toWireTransaction(services)
        assertThat(wtx.outputs).containsOnly(outputState.copy(constraint = HashAttachmentConstraint(contractAttachmentId)))
    }

    @Test(timeout=300_000)
	fun `compatibility zone`() {
        val referenceState = TransactionState(DummyState(), DummyContract.PROGRAM_ID, notary)
        val referenceStateRef = StateRef(SecureHash.randomSHA256(), 1)
        val builder = TransactionBuilder(notary)
                .addReferenceState(StateAndRef(referenceState, referenceStateRef).referenced())
                .addOutputState(TransactionState(DummyState(), DummyContract.PROGRAM_ID, notary))
                .addCommand(dummyCommand)

        with(testNetworkParameters(minimumPlatformVersion = 4)) {
            val services = MockServices(listOf("net.corda.testing.contracts"), TestIdentity(ALICE_NAME), this)
            assertThatThrownBy { builder.toWireTransaction(services) }
                    .isInstanceOf(ZoneVersionTooLowException::class.java)
                    .hasMessageContaining("Cross-Provider Key Rotation requires all nodes on the Corda compatibility zone")
        }

        with(testNetworkParameters(minimumPlatformVersion = CROSS_PROVIDER_KEY_ROTATION)) {
            val services = MockServices(listOf("net.corda.testing.contracts"), TestIdentity(ALICE_NAME), this)
            val wtx = builder.toWireTransaction(services)
            assertThat(wtx.references).containsOnly(referenceStateRef)
        }
    }

    @Test(timeout=300_000)
    fun `notary instructions`() {
        val notaryInstuction = FakeNotaryInstuction("1")
        val builder = TransactionBuilder(notary)
                .addOutputState(TransactionState(DummyState(), DummyContract.PROGRAM_ID, notary))
                .addCommand(dummyCommand)
                .addNotaryInstruction(notaryInstuction)
        val wtx = builder.toWireTransaction(services)
        assertThat(wtx.notaryInstructions).containsOnly(notaryInstuction)
    }

    @Test(timeout=300_000)
    fun `list accessors are mutable copies`() {
        val inputState1 = TransactionState(DummyState(), DummyContract.PROGRAM_ID, notary)
        val inputStateRef1 = StateRef(SecureHash.randomSHA256(), 0)
        val referenceState1 = TransactionState(DummyState(), DummyContract.PROGRAM_ID, notary)
        val referenceStateRef1 = StateRef(SecureHash.randomSHA256(), 1)
        val notaryInstruction1 = FakeNotaryInstuction("1")
        val notaryInstruction2 = FakeNotaryInstuction("2")
        val builder = TransactionBuilder(notary)
                .addInputState(StateAndRef(inputState1, inputStateRef1))
                .addAttachment(SecureHash.allOnesHash)
                .addOutputState(TransactionState(DummyState(), DummyContract.PROGRAM_ID, notary))
                .addCommand(dummyCommand)
                .addReferenceState(StateAndRef(referenceState1, referenceStateRef1).referenced())
                .addNotaryInstruction(notaryInstruction1)
        val inputStateRef2 = StateRef(SecureHash.randomSHA256(), 0)
        val referenceStateRef2 = StateRef(SecureHash.randomSHA256(), 1)

        // List accessors are mutable.
        assertThat((builder.inputStates() as ArrayList).also { it.add(inputStateRef2) }).hasSize(2)
        assertThat((builder.attachments() as ArrayList).also { it.add(SecureHash.zeroHash) }).hasSize(2)
        assertThat((builder.outputStates() as ArrayList).also { it.add(TransactionState(DummyState(), DummyContract.PROGRAM_ID, notary)) }).hasSize(2)
        assertThat((builder.commands() as ArrayList).also { it.add(dummyCommand2) }).hasSize(2)
        val copy = (builder.commands() as ArrayList).also { it.add(dummyCommand2) }
        assertEquals(copy.first(), dummyCommand )
        assertEquals(copy.last(), dummyCommand2 )
        assertThat((builder.referenceStates() as ArrayList).also { it.add(referenceStateRef2) }).hasSize(2)
        assertThat((builder.notaryInstructions() as ArrayList).also { it.add(notaryInstruction2) }).hasSize(2)

        // List accessors are copies.
        assertThat(builder.inputStates()).hasSize(1)
        assertThat(builder.attachments()).hasSize(1)
        assertThat(builder.outputStates()).hasSize(1)
        assertThat(builder.commands()).hasSize(1)
        assertEquals(dummyCommand, builder.commands().first())
        assertThat(builder.referenceStates()).hasSize(1)
        assertThat(builder.notaryInstructions()).hasSize(1)
    }

    @Test(timeout=300_000)
    fun `copy makes copy except lockId`() {
        val inputState = TransactionState(DummyState(), DummyContract.PROGRAM_ID, notary)
        val inputStateRef = StateRef(SecureHash.randomSHA256(), 0)
        val referenceState = TransactionState(DummyState(), DummyContract.PROGRAM_ID, notary)
        val referenceStateRef = StateRef(SecureHash.randomSHA256(), 1)
        val timeWindow = TimeWindow.untilOnly(Instant.now())
        val builder = TransactionBuilder(notary)
                .addInputState(StateAndRef(inputState, inputStateRef))
                .addAttachment(SecureHash.allOnesHash)
                .addOutputState(TransactionState(DummyState(), DummyContract.PROGRAM_ID, notary))
                .addCommand(dummyCommand)
                .setTimeWindow(timeWindow)
                .setPrivacySalt(PrivacySalt())
                .addReferenceState(StateAndRef(referenceState, referenceStateRef).referenced())
                .addNotaryInstruction(FakeNotaryInstuction("1"))
        val copy = builder.copy()

        assertThat(builder.notary).isEqualTo(copy.notary)
        assertThat(builder.lockId).isNotEqualTo(copy.lockId)
        assertThat(builder.inputStates()).isEqualTo(copy.inputStates())
        assertThat(builder.attachments()).isEqualTo(copy.attachments())
        assertThat(builder.outputStates()).isEqualTo(copy.outputStates())
        assertThat(builder.commands()).isEqualTo(copy.commands())
        assertEquals(dummyCommand, copy.commands().first())
//        assertThat(builder.timeWindow()).isEqualTo(copy.timeWindow())
//        assertThat(builder.privacySalt()).isEqualTo(copy.privacySalt())
        assertThat(builder.referenceStates()).isEqualTo(copy.referenceStates())
        assertThat(builder.notaryInstructions()).isEqualTo(copy.notaryInstructions())
    }

    @Test(timeout=300_000)
    fun `copy makes deep copy of lists`() {
        val inputState1 = TransactionState(DummyState(), DummyContract.PROGRAM_ID, notary)
        val inputStateRef1 = StateRef(SecureHash.randomSHA256(), 0)
        val referenceState1 = TransactionState(DummyState(), DummyContract.PROGRAM_ID, notary)
        val referenceStateRef1 = StateRef(SecureHash.randomSHA256(), 1)
        val builder = TransactionBuilder(notary)
                .addInputState(StateAndRef(inputState1, inputStateRef1))
                .addAttachment(SecureHash.allOnesHash)
                .addOutputState(TransactionState(DummyState(), DummyContract.PROGRAM_ID, notary))
                .addCommand(dummyCommand)
                .addReferenceState(StateAndRef(referenceState1, referenceStateRef1).referenced())
        val inputState2 = TransactionState(DummyState(), DummyContract.PROGRAM_ID, notary)
        val inputStateRef2 = StateRef(SecureHash.randomSHA256(), 0)
        val referenceState2 = TransactionState(DummyState(), DummyContract.PROGRAM_ID, notary)
        val referenceStateRef2 = StateRef(SecureHash.randomSHA256(), 1)
        val copy = builder.copy()
                .addInputState(StateAndRef(inputState2, inputStateRef2))
                .addAttachment(SecureHash.zeroHash)
                .addOutputState(TransactionState(DummyState(), DummyContract.PROGRAM_ID, notary))
                .addCommand(dummyCommand2)
                .addReferenceState(StateAndRef(referenceState2, referenceStateRef2).referenced())

        // Lists on the copy are longer
        assertThat(copy.inputStates()).hasSize(2)
        assertThat(copy.attachments()).hasSize(2)
        assertThat(copy.outputStates()).hasSize(2)
        assertThat(copy.commands()).hasSize(2)
        assertEquals(dummyCommand, copy.commands().first())
        assertEquals(dummyCommand2, copy.commands().last())
        assertThat(copy.referenceStates()).hasSize(2)

        // Lists on the original are unchanged
        assertThat(builder.inputStates()).hasSize(1)
        assertThat(builder.attachments()).hasSize(1)
        assertThat(builder.outputStates()).hasSize(1)
        assertThat(builder.commands()).hasSize(1)
        assertEquals(dummyCommand, builder.commands().first())
        assertThat(builder.referenceStates()).hasSize(1)
    }

    @Ignore
    @Test(timeout=300_000)
    fun `throws with non-default hash algorithm`() {
        HashAgility.init()
        try {
            val outputState = TransactionState(
                    data = DummyState(),
                    contract = DummyContract.PROGRAM_ID,
                    notary = notary,
                    constraint = HashAttachmentConstraint(contractAttachmentId)
            )
            val builder = TransactionBuilder(
                    //privacySalt = DigestService.sha2_384.privacySalt,
                    privacySalt = PrivacySalt.createFor(DigestService.sha2_384.hashAlgorithm))
                    .addOutputState(outputState)
                    .addCommand(dummyCommand)

            assertThatExceptionOfType(UnsupportedHashTypeException::class.java).isThrownBy {
                builder.toWireTransaction(services)
            }
        } finally {
            HashAgility.init()
        }
    }

    @Test(timeout=300_000)
    fun `allows non-default hash algorithm`() {
        HashAgility.init(txHashAlgoName = DigestService.sha2_384.hashAlgorithm)
        assertThat(services.digestService).isEqualTo(DigestService.sha2_384)
        try {
            val outputState = TransactionState(
                    data = DummyState(),
                    contract = DummyContract.PROGRAM_ID,
                    notary = notary,
                    constraint = HashAttachmentConstraint(contractAttachmentId)
            )
            val builder = TransactionBuilder(
                    //privacySalt = DigestService.sha2_384.privacySalt,
                    privacySalt = PrivacySalt.createFor(DigestService.sha2_384.hashAlgorithm))
                    .addOutputState(outputState)
                    .addCommand(dummyCommand)

            assertThat(builder.toWireTransaction(services).digestService).isEqualTo(DigestService.sha2_384)
        } finally {
            HashAgility.init()
        }
    }

    @Test(timeout=300_000)
    fun `toWireTransaction fails if no scheme is registered with schemeId`() {
        val outputState = TransactionState(
                data = DummyState(),
                contract = DummyContract.PROGRAM_ID,
                notary = notary,
                constraint = HashAttachmentConstraint(contractAttachmentId)
        )
        val builder = TransactionBuilder()
                .addOutputState(outputState)
                .addCommand(dummyCommand)

        val schemeId = 7
        assertFailsWith<UnsupportedOperationException>("Could not find custom serialization scheme with SchemeId = $schemeId.") {
            builder.toWireTransaction(services, schemeId)
       }
    }

    @Test(timeout=300_000)
    fun `contract overlap in explicit attachments`() {
        val overlappingAttachmentId = cordappWithPackages("net.corda.testing").jarFile.inputStream().use {
            services.attachments.importAttachment(it, RPC_UPLOADER, null)
        }

        val outputState = TransactionState(
                data = DummyState(),
                contract = DummyContract.PROGRAM_ID,
                notary = notary
        )
        val builder = TransactionBuilder()
                .addAttachment(contractAttachmentId)
                .addAttachment(overlappingAttachmentId)
                .addOutputState(outputState)
                .addCommand(dummyCommand)
        assertThatIllegalArgumentException()
                .isThrownBy { builder.toWireTransaction(services) }
                .withMessageContaining("Multiple attachments specified for the same contract net.corda.testing.contracts.DummyContract")
    }

    private data class FakeNotaryInstuction(val instruct: String) : NotaryInstruction
}
