package net.corda.coretests.serialization

import com.nhaarman.mockito_kotlin.mock
import net.corda.core.contracts.*
import net.corda.core.crypto.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.NotaryInfo
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.transactions.*
import net.corda.core.utilities.seconds
import net.corda.finance.POUNDS
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.internal.TEST_TX_TIME
import net.corda.testing.internal.rigorousMock
import net.corda.testing.node.MockServices
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.security.SignatureException
import java.util.*
import kotlin.reflect.jvm.javaField
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TransactionSerializationTests {
    private companion object {
        val dummyNotary = TestIdentity(DUMMY_NOTARY_NAME, 20)
        val megaCorp = TestIdentity(CordaX500Name("MegaCorp", "London", "GB"))
        val MINI_CORP = TestIdentity(CordaX500Name("MiniCorp", "London", "GB")).party
        val DUMMY_NOTARY get() = dummyNotary.party
        val DUMMY_NOTARY_KEY get() = dummyNotary.keyPair
        val MEGA_CORP get() = megaCorp.party
        val MEGA_CORP_KEY get() = megaCorp.keyPair
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()
    private val TEST_CASH_PROGRAM_ID = "net.corda.coretests.serialization.TransactionSerializationTests\$TestCash"

    class TestCash : Contract {
        override fun verify(tx: LedgerTransaction) {
        }

        data class State(
                val deposit: PartyAndReference,
                val amount: Amount<Currency>,
                override val owner: AbstractParty) : OwnableState {
            override val participants: List<AbstractParty>
                get() = listOf(owner)

            override fun withNewOwner(newOwner: AbstractParty) = CommandAndState(Commands.Move(), copy(owner = newOwner))
        }

        interface Commands : CommandData {
            class Move : TypeOnlyCommandData(), Commands
            class Issue : TypeOnlyCommandData(), Commands
        }
    }

    // Simple TX that takes 1000 pounds from me and sends 600 to someone else (with 400 change).
    // It refers to a fake TX/state that we don't bother creating here.
    val depositRef = MINI_CORP.ref(1)
    val signatures = listOf(TransactionSignature(ByteArray(1), MEGA_CORP_KEY.public, SignatureMetadata(1, Crypto.findSignatureScheme(MEGA_CORP_KEY.public).schemeNumberID)))

    lateinit var inputState : StateAndRef<ContractState>
    val outputState = TransactionState(TestCash.State(depositRef, 600.POUNDS, MEGA_CORP), TEST_CASH_PROGRAM_ID, DUMMY_NOTARY)
    val changeState = TransactionState(TestCash.State(depositRef, 400.POUNDS, MEGA_CORP), TEST_CASH_PROGRAM_ID, DUMMY_NOTARY)

    val megaCorpServices = object : MockServices(listOf("net.corda.coretests.serialization"), MEGA_CORP.name, mock(), testNetworkParameters(notaries = listOf(NotaryInfo(DUMMY_NOTARY, true))), MEGA_CORP_KEY) {
        //override mock implementation with a real one
        override fun loadContractAttachment(stateRef: StateRef): Attachment = servicesForResolution.loadContractAttachment(stateRef)
    }
    val notaryServices = MockServices(listOf("net.corda.coretests.serialization"), DUMMY_NOTARY.name, rigorousMock(), DUMMY_NOTARY_KEY)
    lateinit var tx: TransactionBuilder

    @Before
    fun setup() {
        //record fake transaction which created inputState
        val fakeTx = megaCorpServices.signInitialTransaction(TransactionBuilder(DUMMY_NOTARY).withItems(outputState, Command(TestCash.Commands.Issue(), arrayListOf(MEGA_CORP.owningKey))))
        megaCorpServices.recordTransactions(fakeTx)
        val fakeStateRef = StateRef(fakeTx.id,0)
        inputState = StateAndRef(TransactionState(TestCash.State(depositRef, 100.POUNDS, MEGA_CORP), TEST_CASH_PROGRAM_ID, DUMMY_NOTARY, constraint = AlwaysAcceptAttachmentConstraint), fakeStateRef)
        tx = TransactionBuilder(DUMMY_NOTARY).withItems(inputState, outputState, changeState, Command(TestCash.Commands.Move(), arrayListOf(MEGA_CORP.owningKey)))
    }

    @Test
    fun signWireTX() {
        val ptx = megaCorpServices.signInitialTransaction(tx)
        val stx = notaryServices.addSignature(ptx)

        // Now check that the signature we just made verifies.
        stx.verifyRequiredSignatures()

        // Corrupt the data and ensure the signature catches the problem.
        val bytesField = stx.id::bytes.javaField?.apply { isAccessible = true }
        val bytes = bytesField?.get(stx.id) as ByteArray
        bytes[5] = bytes[5].inc()

        assertFailsWith(SignatureException::class) {
            stx.verifyRequiredSignatures()
        }
    }

    @Test
    fun wrongKeys() {
        val ptx = megaCorpServices.signInitialTransaction(tx)
        val stx = notaryServices.addSignature(ptx)

        // Cannot construct with an empty sigs list.
        assertFailsWith(IllegalArgumentException::class) {
            stx.copy(sigs = emptyList())
        }
        val DUMMY_KEY_2 = generateKeyPair()
        // If the signature was replaced in transit, we don't like it.
        assertFailsWith(SignatureException::class) {
            val tx2 = TransactionBuilder(DUMMY_NOTARY).withItems(inputState, outputState, changeState,
                    Command(TestCash.Commands.Move(), DUMMY_KEY_2.public))

            val ptx2 = notaryServices.signInitialTransaction(tx2)
            val dummyServices = MockServices(emptyList(), MEGA_CORP.name, rigorousMock(), DUMMY_KEY_2)
            val stx2 = dummyServices.addSignature(ptx2)

            stx.copy(sigs = stx2.sigs).verifyRequiredSignatures()
        }
    }

    @Test
    fun timeWindow() {
        tx.setTimeWindow(TEST_TX_TIME, 30.seconds)
        val ptx = megaCorpServices.signInitialTransaction(tx)
        val stx = notaryServices.addSignature(ptx)
        assertEquals(TEST_TX_TIME, stx.tx.timeWindow?.midpoint)
    }

    @Test
    fun storeAndLoadWhenSigning() {
        val ptx = megaCorpServices.signInitialTransaction(tx)
        ptx.verifySignaturesExcept(DUMMY_NOTARY_KEY.public)
        val stored = ptx.serialize()
        val loaded = stored.deserialize()

        assertEquals(loaded, ptx)

        val final = notaryServices.addSignature(loaded)
        final.verifyRequiredSignatures()
    }
}
