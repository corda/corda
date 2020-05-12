package net.corda.coretests.transactions

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.contracts.*
import net.corda.core.cordapp.CordappProvider
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.internal.AbstractAttachment
import net.corda.core.internal.PLATFORM_VERSION
import net.corda.core.node.ServicesForResolution
import net.corda.core.node.ZoneVersionTooLowException
import net.corda.core.node.services.AttachmentStorage
import net.corda.core.node.services.NetworkParametersService
import net.corda.core.serialization.serialize
import net.corda.core.transactions.TransactionBuilder
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyState
import net.corda.testing.core.*
import net.corda.coretesting.internal.rigorousMock
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.security.PublicKey
import java.time.Instant

class TransactionBuilderTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private val notary = TestIdentity(DUMMY_NOTARY_NAME).party
    private val services = rigorousMock<ServicesForResolution>()
    private val contractAttachmentId = SecureHash.randomSHA256()
    private val attachments = rigorousMock<AttachmentStorage>()
    private val networkParametersService = mock<NetworkParametersService>()

    @Before
    fun setup() {
        val cordappProvider = rigorousMock<CordappProvider>()
        val networkParameters = testNetworkParameters(minimumPlatformVersion = PLATFORM_VERSION)
        doReturn(networkParametersService).whenever(services).networkParametersService
        doReturn(networkParameters.serialize().hash).whenever(networkParametersService).currentHash
        doReturn(cordappProvider).whenever(services).cordappProvider
        doReturn(contractAttachmentId).whenever(cordappProvider).getContractAttachmentID(DummyContract.PROGRAM_ID)
        doReturn(networkParameters).whenever(services).networkParameters

        val attachmentStorage = rigorousMock<AttachmentStorage>()
        doReturn(attachmentStorage).whenever(services).attachments
        val attachment = rigorousMock<ContractAttachment>()
        doReturn(attachment).whenever(attachmentStorage).openAttachment(contractAttachmentId)
        doReturn(contractAttachmentId).whenever(attachment).id
        doReturn(setOf(DummyContract.PROGRAM_ID)).whenever(attachment).allContracts
        doReturn("app").whenever(attachment).uploader
        doReturn(emptyList<Party>()).whenever(attachment).signerKeys
        doReturn(listOf(contractAttachmentId)).whenever(attachmentStorage)
                .getLatestContractAttachments("net.corda.testing.contracts.DummyContract")
    }

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
                .addCommand(DummyCommandData, notary.owningKey)
        val wtx = builder.toWireTransaction(services)
        assertThat(wtx.outputs).containsOnly(outputState)
        assertThat(wtx.commands).containsOnly(Command(DummyCommandData, notary.owningKey))
        assertThat(wtx.networkParametersHash).isEqualTo(networkParametersService.currentHash)
    }

    @Test(timeout=300_000)
	fun `automatic hash constraint`() {
        doReturn(unsignedAttachment).whenever(attachments).openAttachment(contractAttachmentId)

        val outputState = TransactionState(data = DummyState(), contract = DummyContract.PROGRAM_ID, notary = notary)
        val builder = TransactionBuilder()
                .addOutputState(outputState)
                .addCommand(DummyCommandData, notary.owningKey)
        val wtx = builder.toWireTransaction(services)
        assertThat(wtx.outputs).containsOnly(outputState.copy(constraint = HashAttachmentConstraint(contractAttachmentId)))
    }

    @Test(timeout=300_000)
	fun `reference states`() {
        doReturn(unsignedAttachment).whenever(attachments).openAttachment(contractAttachmentId)

        val referenceState = TransactionState(DummyState(), DummyContract.PROGRAM_ID, notary)
        val referenceStateRef = StateRef(SecureHash.randomSHA256(), 1)
        val builder = TransactionBuilder(notary)
                .addReferenceState(StateAndRef(referenceState, referenceStateRef).referenced())
                .addOutputState(TransactionState(DummyState(), DummyContract.PROGRAM_ID, notary))
                .addCommand(DummyCommandData, notary.owningKey)

        doReturn(testNetworkParameters(minimumPlatformVersion = 3)).whenever(services).networkParameters
        assertThatThrownBy { builder.toWireTransaction(services) }
                .isInstanceOf(ZoneVersionTooLowException::class.java)
                .hasMessageContaining("Reference states")

        doReturn(testNetworkParameters(minimumPlatformVersion = 4)).whenever(services).networkParameters
        doReturn(referenceState).whenever(services).loadState(referenceStateRef)
        val wtx = builder.toWireTransaction(services)
        assertThat(wtx.references).containsOnly(referenceStateRef)
    }

    @Test(timeout=300_000)
	fun `automatic signature constraint`() {
        val aliceParty = TestIdentity(ALICE_NAME).party
        val bobParty = TestIdentity(BOB_NAME).party
        val compositeKey = CompositeKey.Builder().addKeys(aliceParty.owningKey, bobParty.owningKey).build()
        val expectedConstraint = SignatureAttachmentConstraint(compositeKey)
        val signedAttachment = signedAttachment(aliceParty, bobParty)

        assertTrue(expectedConstraint.isSatisfiedBy(signedAttachment))
        assertFalse(expectedConstraint.isSatisfiedBy(unsignedAttachment))

        doReturn(attachments).whenever(services).attachments
        doReturn(signedAttachment).whenever(attachments).openAttachment(contractAttachmentId)
        doReturn(listOf(contractAttachmentId)).whenever(attachments)
                .getLatestContractAttachments("net.corda.testing.contracts.DummyContract")

        val outputState = TransactionState(data = DummyState(), contract = DummyContract.PROGRAM_ID, notary = notary)
        val builder = TransactionBuilder()
                .addOutputState(outputState)
                .addCommand(DummyCommandData, notary.owningKey)
        val wtx = builder.toWireTransaction(services)

        assertThat(wtx.outputs).containsOnly(outputState.copy(constraint = expectedConstraint))
    }

    private val unsignedAttachment = ContractAttachment(object : AbstractAttachment({ byteArrayOf() }, "test") {
        override val id: SecureHash get() = throw UnsupportedOperationException()

        override val signerKeys: List<PublicKey> get() = emptyList()
    }, DummyContract.PROGRAM_ID)

    private fun signedAttachment(vararg parties: Party) = ContractAttachment.create(object : AbstractAttachment({ byteArrayOf() }, "test") {
        override val id: SecureHash get() = contractAttachmentId

        override val signerKeys: List<PublicKey> get() = parties.map { it.owningKey }
    }, DummyContract.PROGRAM_ID, signerKeys = parties.map { it.owningKey })

    @Test(timeout=300_000)
    fun `list accessors are mutable copies`() {
        val inputState1 = TransactionState(DummyState(), DummyContract.PROGRAM_ID, notary)
        val inputStateRef1 = StateRef(SecureHash.randomSHA256(), 0)
        val referenceState1 = TransactionState(DummyState(), DummyContract.PROGRAM_ID, notary)
        val referenceStateRef1 = StateRef(SecureHash.randomSHA256(), 1)
        val builder = TransactionBuilder(notary)
                .addInputState(StateAndRef(inputState1, inputStateRef1))
                .addAttachment(SecureHash.allOnesHash)
                .addOutputState(TransactionState(DummyState(), DummyContract.PROGRAM_ID, notary))
                .addCommand(DummyCommandData, notary.owningKey)
                .addReferenceState(StateAndRef(referenceState1, referenceStateRef1).referenced())
        val inputStateRef2 = StateRef(SecureHash.randomSHA256(), 0)
        val referenceStateRef2 = StateRef(SecureHash.randomSHA256(), 1)

        // List accessors are mutable.
        assertThat((builder.inputStates() as ArrayList).also { it.add(inputStateRef2) }).hasSize(2)
        assertThat((builder.attachments() as ArrayList).also { it.add(SecureHash.zeroHash) }).hasSize(2)
        assertThat((builder.outputStates() as ArrayList).also { it.add(TransactionState(DummyState(), DummyContract.PROGRAM_ID, notary)) }).hasSize(2)
        assertThat((builder.commands() as ArrayList).also { it.add(Command(DummyCommandData, notary.owningKey)) }).hasSize(2)
        assertThat((builder.referenceStates() as ArrayList).also { it.add(referenceStateRef2) }).hasSize(2)

        // List accessors are copies.
        assertThat(builder.inputStates()).hasSize(1)
        assertThat(builder.attachments()).hasSize(1)
        assertThat(builder.outputStates()).hasSize(1)
        assertThat(builder.commands()).hasSize(1)
        assertThat(builder.referenceStates()).hasSize(1)
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
                .addCommand(DummyCommandData, notary.owningKey)
                .setTimeWindow(timeWindow)
                .setPrivacySalt(PrivacySalt())
                .addReferenceState(StateAndRef(referenceState, referenceStateRef).referenced())
        val copy = builder.copy()

        assertThat(builder.notary).isEqualTo(copy.notary)
        assertThat(builder.lockId).isNotEqualTo(copy.lockId)
        assertThat(builder.inputStates()).isEqualTo(copy.inputStates())
        assertThat(builder.attachments()).isEqualTo(copy.attachments())
        assertThat(builder.outputStates()).isEqualTo(copy.outputStates())
        assertThat(builder.commands()).isEqualTo(copy.commands())
//        assertThat(builder.timeWindow()).isEqualTo(copy.timeWindow())
//        assertThat(builder.privacySalt()).isEqualTo(copy.privacySalt())
        assertThat(builder.referenceStates()).isEqualTo(copy.referenceStates())
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
                .addCommand(DummyCommandData, notary.owningKey)
                .addReferenceState(StateAndRef(referenceState1, referenceStateRef1).referenced())
        val inputState2 = TransactionState(DummyState(), DummyContract.PROGRAM_ID, notary)
        val inputStateRef2 = StateRef(SecureHash.randomSHA256(), 0)
        val referenceState2 = TransactionState(DummyState(), DummyContract.PROGRAM_ID, notary)
        val referenceStateRef2 = StateRef(SecureHash.randomSHA256(), 1)
        val copy = builder.copy()
                .addInputState(StateAndRef(inputState2, inputStateRef2))
                .addAttachment(SecureHash.zeroHash)
                .addOutputState(TransactionState(DummyState(), DummyContract.PROGRAM_ID, notary))
                .addCommand(DummyCommandData, notary.owningKey)
                .addReferenceState(StateAndRef(referenceState2, referenceStateRef2).referenced())

        // Lists on the copy are longer
        assertThat(copy.inputStates()).hasSize(2)
        assertThat(copy.attachments()).hasSize(2)
        assertThat(copy.outputStates()).hasSize(2)
        assertThat(copy.commands()).hasSize(2)
        assertThat(copy.referenceStates()).hasSize(2)

        // Lists on the original are unchanged
        assertThat(builder.inputStates()).hasSize(1)
        assertThat(builder.attachments()).hasSize(1)
        assertThat(builder.outputStates()).hasSize(1)
        assertThat(builder.commands()).hasSize(1)
        assertThat(builder.referenceStates()).hasSize(1)
    }
}
