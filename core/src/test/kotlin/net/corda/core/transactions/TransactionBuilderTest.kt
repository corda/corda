package net.corda.core.transactions

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.contracts.*
import net.corda.core.cordapp.CordappProvider
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.internal.AbstractAttachment
import net.corda.core.node.ServicesForResolution
import net.corda.core.node.ZoneVersionTooLowException
import net.corda.core.node.services.AttachmentStorage
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyState
import net.corda.testing.core.*
import net.corda.testing.internal.rigorousMock
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TransactionBuilderTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private val notary = TestIdentity(DUMMY_NOTARY_NAME).party
    private val services = rigorousMock<ServicesForResolution>()
    private val contractAttachmentId = SecureHash.randomSHA256()
    private val attachments = rigorousMock<AttachmentStorage>()

    @Before
    fun setup() {
        val cordappProvider = rigorousMock<CordappProvider>()
        doReturn(cordappProvider).whenever(services).cordappProvider
        doReturn(contractAttachmentId).whenever(cordappProvider).getContractAttachmentID(DummyContract.PROGRAM_ID)
        doReturn(testNetworkParameters()).whenever(services).networkParameters
        doReturn(attachments).whenever(services).attachments
    }

    @Test
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
    }

    @Test
    fun `automatic hash constraint`() {
        doReturn(unsignedAttachment).whenever(attachments).openAttachment(contractAttachmentId)

        val outputState = TransactionState(data = DummyState(), contract = DummyContract.PROGRAM_ID, notary = notary)
        val builder = TransactionBuilder()
                .addOutputState(outputState)
                .addCommand(DummyCommandData, notary.owningKey)
        val wtx = builder.toWireTransaction(services)
        assertThat(wtx.outputs).containsOnly(outputState.copy(constraint = HashAttachmentConstraint(contractAttachmentId)))
    }

    @Test
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
        val wtx = builder.toWireTransaction(services)
        assertThat(wtx.references).containsOnly(referenceStateRef)
    }

    @Test
    fun `automatic signature constraint`() {
        val aliceParty = TestIdentity(ALICE_NAME).party
        val bobParty = TestIdentity(BOB_NAME).party
        val compositeKey = CompositeKey.Builder().addKeys(aliceParty.owningKey, bobParty.owningKey).build()

        doReturn(signedAttachment(aliceParty, bobParty)).whenever(attachments).openAttachment(contractAttachmentId)

        val outputState = TransactionState(data = DummyState(), contract = DummyContract.PROGRAM_ID, notary = notary)
        val builder = TransactionBuilder()
                .addOutputState(outputState)
                .addCommand(DummyCommandData, notary.owningKey)
        val wtx = builder.toWireTransaction(services)

        assertThat(wtx.outputs).containsOnly(
                outputState.copy(constraint = SignatureAttachmentConstraint(compositeKey)))
    }


    private val unsignedAttachment = object : AbstractAttachment({ byteArrayOf() }) {
        override val id: SecureHash get() = throw UnsupportedOperationException()

        override val signers: List<Party> get() = emptyList()
    }

    private fun signedAttachment(vararg parties: Party) = object : AbstractAttachment({ byteArrayOf() }) {
        override val id: SecureHash get() = throw UnsupportedOperationException()
           
        override val signers: List<Party> get() = parties.toList()
    }
}
