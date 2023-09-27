package net.corda.coretests.transactions

import net.corda.core.contracts.Command
import net.corda.core.contracts.HashAttachmentConstraint
import net.corda.core.contracts.PrivacySalt
import net.corda.core.contracts.SignatureAttachmentConstraint
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.contracts.TransactionState
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.crypto.DigestService
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.HashAgility
import net.corda.core.internal.PLATFORM_VERSION
import net.corda.core.internal.digestService
import net.corda.core.node.ZoneVersionTooLowException
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.serialization.internal._driverSerializationEnv
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyState
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.DummyCommandData
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.MockServices
import net.corda.testing.node.internal.cordappWithPackages
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import kotlin.test.assertFailsWith

class TransactionBuilderTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private val notary = TestIdentity(DUMMY_NOTARY_NAME).party
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
                .addCommand(DummyCommandData, notary.owningKey)
        val wtx = builder.toWireTransaction(services)
        assertThat(wtx.outputs).containsOnly(outputState)
        assertThat(wtx.commands).containsOnly(Command(DummyCommandData, notary.owningKey))
        assertThat(wtx.networkParametersHash).isEqualTo(services.networkParametersService.currentHash)
    }

    @Test(timeout=300_000)
	fun `automatic hash constraint`() {
        val outputState = TransactionState(data = DummyState(), contract = DummyContract.PROGRAM_ID, notary = notary)
        val builder = TransactionBuilder()
                .addOutputState(outputState)
                .addCommand(DummyCommandData, notary.owningKey)
        val wtx = builder.toWireTransaction(services)
        assertThat(wtx.outputs).containsOnly(outputState.copy(constraint = HashAttachmentConstraint(contractAttachmentId)))
    }

    @Test(timeout=300_000)
	fun `reference states`() {
        val referenceState = TransactionState(DummyState(), DummyContract.PROGRAM_ID, notary)
        val referenceStateRef = StateRef(SecureHash.randomSHA256(), 1)
        val builder = TransactionBuilder(notary)
                .addReferenceState(StateAndRef(referenceState, referenceStateRef).referenced())
                .addOutputState(TransactionState(DummyState(), DummyContract.PROGRAM_ID, notary))
                .addCommand(DummyCommandData, notary.owningKey)

        with(testNetworkParameters(minimumPlatformVersion = 3)) {
            val services = MockServices(listOf("net.corda.testing.contracts"), TestIdentity(ALICE_NAME), this)
            assertThatThrownBy { builder.toWireTransaction(services) }
                    .isInstanceOf(ZoneVersionTooLowException::class.java)
                    .hasMessageContaining("Reference states")
        }

        with(testNetworkParameters(minimumPlatformVersion = 4)) {
            val services = MockServices(listOf("net.corda.testing.contracts"), TestIdentity(ALICE_NAME), this)
            val wtx = builder.toWireTransaction(services)
            assertThat(wtx.references).containsOnly(referenceStateRef)
        }
    }

    @Test(timeout=300_000)
	fun `automatic signature constraint`() {
        // SerializationEnvironmentRule and MockNetwork don't work well together, so we temporarily clear out the driverSerializationEnv
        // for this test.
        val driverSerializationEnv = _driverSerializationEnv.get()
        _driverSerializationEnv.set(null)
        val mockNetwork = MockNetwork(
                MockNetworkParameters(
                        networkParameters = testNetworkParameters(minimumPlatformVersion = PLATFORM_VERSION),
                        // And we need the MockNetwork so that we can create a signed attachment
                        cordappsForAllNodes = listOf(cordappWithPackages("net.corda.testing.contracts").signed())
                )
        )

        val services = mockNetwork.notaryNodes[0].services

        val attachment = services.attachments.openAttachment(services.attachments.getLatestContractAttachments(DummyContract.PROGRAM_ID)[0])
        val attachmentSigner = attachment!!.signerKeys.single()

        val expectedConstraint = SignatureAttachmentConstraint(attachmentSigner)
        assertTrue(expectedConstraint.isSatisfiedBy(attachment))

        try {
            val outputState = TransactionState(data = DummyState(), contract = DummyContract.PROGRAM_ID, notary = notary)
            val builder = TransactionBuilder()
                    .addOutputState(outputState)
                    .addCommand(DummyCommandData, notary.owningKey)
            val wtx = builder.toWireTransaction(services)

            assertThat(wtx.outputs).containsOnly(outputState.copy(constraint = expectedConstraint))
        } finally {
            mockNetwork.stopNodes()
            _driverSerializationEnv.set(driverSerializationEnv)
        }
    }

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

    @Ignore
    @Test(timeout=300_000, expected = TransactionVerificationException.UnsupportedHashTypeException::class)
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
                    .addCommand(DummyCommandData, notary.owningKey)

            builder.toWireTransaction(services)
        } finally {
            HashAgility.init()
        }
    }

    @Test(timeout=300_000, expected = Test.None::class)
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
                    .addCommand(DummyCommandData, notary.owningKey)

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
                .addCommand(DummyCommandData, notary.owningKey)

        val schemeId = 7
        assertFailsWith<UnsupportedOperationException>("Could not find custom serialization scheme with SchemeId = $schemeId.") {
            builder.toWireTransaction(services, schemeId)
       }
    }
}
