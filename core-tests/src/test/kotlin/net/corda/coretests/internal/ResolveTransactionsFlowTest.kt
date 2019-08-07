package net.corda.coretests.internal

import co.paralleluniverse.fibers.Suspendable
import junit.framework.TestCase.assertTrue
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignableData
import net.corda.core.crypto.SignatureMetadata
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.*
import net.corda.core.transactions.ContractUpgradeWireTransaction
import net.corda.core.transactions.NotaryChangeWireTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.NonEmptySet
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.sequence
import net.corda.core.utilities.unwrap
import net.corda.coretests.flows.TestNoSecurityDataVendingFlow
import net.corda.node.services.DbTransactionsResolver
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyContractV2
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.internal.DUMMY_CONTRACTS_CORDAPP
import net.corda.testing.node.internal.enclosedCordapp
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// DOCSTART 3
class ResolveTransactionsFlowTest {
    private lateinit var mockNet: MockNetwork
    private lateinit var notaryNode: StartedMockNode
    private lateinit var megaCorpNode: StartedMockNode
    private lateinit var miniCorpNode: StartedMockNode
    private lateinit var newNotaryNode: StartedMockNode
    private lateinit var megaCorp: Party
    private lateinit var miniCorp: Party
    private lateinit var notary: Party
    private lateinit var newNotary: Party

    @Before
    fun setup() {
        val mockNetworkParameters = MockNetworkParameters(
                cordappsForAllNodes = listOf(DUMMY_CONTRACTS_CORDAPP, enclosedCordapp()),
                notarySpecs = listOf(
                        MockNetworkNotarySpec(DUMMY_NOTARY_NAME),
                        MockNetworkNotarySpec(DUMMY_BANK_A_NAME)
                )
        )
        mockNet = MockNetwork(mockNetworkParameters)
        notaryNode = mockNet.notaryNodes.first()
        megaCorpNode = mockNet.createPartyNode(CordaX500Name("MegaCorp", "London", "GB"))
        miniCorpNode = mockNet.createPartyNode(CordaX500Name("MiniCorp", "London", "GB"))
        notary = notaryNode.info.singleIdentity()
        megaCorp = megaCorpNode.info.singleIdentity()
        miniCorp = miniCorpNode.info.singleIdentity()
        newNotaryNode = mockNet.notaryNodes[1]
        newNotary = mockNet.notaryNodes[1].info.singleIdentity()
    }

    @After
    fun tearDown() {
        mockNet.stopNodes()
        System.setProperty("net.corda.node.dbtransactionsresolver.InMemoryResolutionLimit", "0")
    }
    // DOCEND 3

    // DOCSTART 1
    @Test
    fun `resolve from two hashes`() {
        val (stx1, stx2) = makeTransactions()
        val p = TestFlow(setOf(stx2.id), megaCorp)
        val future = miniCorpNode.startFlow(p)
        mockNet.runNetwork()
        future.getOrThrow()
        miniCorpNode.transaction {
            assertEquals(stx1, miniCorpNode.services.validatedTransactions.getTransaction(stx1.id))
            assertEquals(stx2, miniCorpNode.services.validatedTransactions.getTransaction(stx2.id))
        }
    }
    // DOCEND 1

    @Test
    fun `dependency with an error`() {
        val stx = makeTransactions(signFirstTX = false).second
        val p = TestFlow(setOf(stx.id), megaCorp)
        val future = miniCorpNode.startFlow(p)
        mockNet.runNetwork()
        assertFailsWith(SignedTransaction.SignaturesMissingException::class) { future.getOrThrow() }
    }

    @Test
    fun `resolve from a signed transaction`() {
        val (stx1, stx2) = makeTransactions()
        val p = TestFlow(stx2, megaCorp)
        val future = miniCorpNode.startFlow(p)
        mockNet.runNetwork()
        future.getOrThrow()
        miniCorpNode.transaction {
            assertEquals(stx1, miniCorpNode.services.validatedTransactions.getTransaction(stx1.id))
            // But stx2 wasn't inserted, just stx1.
            assertNull(miniCorpNode.services.validatedTransactions.getTransaction(stx2.id))
        }
    }

    @Test
    fun `triangle of transactions resolves fine`() {
        val stx1 = makeTransactions().first

        val stx2 = DummyContract.move(stx1.tx.outRef(0), miniCorp).let { builder ->
            val ptx = megaCorpNode.services.signInitialTransaction(builder)
            notaryNode.services.addSignature(ptx, notary.owningKey)
        }

        megaCorpNode.transaction {
            megaCorpNode.services.recordTransactions(stx2)
        }

        val stx3 = DummyContract.move(listOf(stx1.tx.outRef(0), stx2.tx.outRef(0)), miniCorp).let { builder ->
            val ptx = megaCorpNode.services.signInitialTransaction(builder)
            notaryNode.services.addSignature(ptx, notary.owningKey)
        }

        megaCorpNode.transaction {
            megaCorpNode.services.recordTransactions(stx3)
        }

        val p = TestFlow(setOf(stx3.id), megaCorp)
        val future = miniCorpNode.startFlow(p)
        mockNet.runNetwork()
        future.getOrThrow()
    }

    @Test
    fun attachment() {
        fun makeJar(): InputStream {
            val bs = ByteArrayOutputStream()
            val jar = JarOutputStream(bs)
            jar.putNextEntry(JarEntry("TEST"))
            jar.write("Some test file".toByteArray())
            jar.closeEntry()
            jar.close()
            return bs.toByteArray().sequence().open()
        }
        // TODO: this operation should not require an explicit transaction
        val id = megaCorpNode.transaction {
            megaCorpNode.services.attachments.importAttachment(makeJar(), TESTDSL_UPLOADER, null)
        }
        val stx2 = makeTransactions(withAttachment = id).second
        val p = TestFlow(stx2, megaCorp)
        val future = miniCorpNode.startFlow(p)
        mockNet.runNetwork()
        future.getOrThrow()

        // TODO: this operation should not require an explicit transaction
        miniCorpNode.transaction {
            assertNotNull(miniCorpNode.services.attachments.openAttachment(id))
        }
    }

    @Test
    fun `Requesting a transaction while having the right to see it succeeds`() {
        val (_, stx2) = makeTransactions()
        val p = TestNoRightsVendingFlow(miniCorp, toVend = stx2, toRequest = stx2)
        val future = megaCorpNode.startFlow(p)
        mockNet.runNetwork()
        future.getOrThrow()
    }

    @Test
    fun `Requesting a transaction without having the right to see it results in exception`() {
        val (_, stx2) = makeTransactions()
        val (_, stx3) = makeTransactions()
        val p = TestNoRightsVendingFlow(miniCorp, toVend = stx2, toRequest = stx3)
        val future = megaCorpNode.startFlow(p)
        mockNet.runNetwork()
        assertFailsWith<FetchDataFlow.IllegalTransactionRequest> { future.getOrThrow() }
    }

    @Test
    fun `Requesting a transaction twice results in exception`() {
        val (_, stx2) = makeTransactions()
        val p = TestResolveTwiceVendingFlow(miniCorp, stx2)
        val future = megaCorpNode.startFlow(p)
        mockNet.runNetwork()
        assertFailsWith<FetchDataFlow.IllegalTransactionRequest> { future.getOrThrow() }
    }

    @Test
    fun `Switches between checkpoint and DB based resolution correctly`() {
        System.setProperty("${DbTransactionsResolver::class.java.name}.max-checkpoint-resolution", "20")
        var numTransactions = 0
        megaCorpNode.services.validatedTransactions.updates.subscribe {
            numTransactions++
        }
        val txToResolve = makeLargeTransactionChain(50)
        var numUpdates = 0
        miniCorpNode.services.validatedTransactions.updates.subscribe {
            numUpdates++
        }
        val p = TestFlow(txToResolve, megaCorp)
        val future = miniCorpNode.startFlow(p)
        mockNet.runNetwork()
        future.getOrThrow()
        // ResolveTransactionsFlow only stores transaction dependencies and not the requested transaction, so there will be one fewer
        // transaction stored on the receiving node than on the sending one.
        assertEquals(numTransactions - 1, numUpdates)
    }

    @Test
    fun `resolution works when transaction in chain is already resolved`() {
        val (tx1, tx2) = makeTransactions()
        miniCorpNode.transaction {
            miniCorpNode.services.recordTransactions(tx1)
        }

        val p = TestFlow(tx2, megaCorp)
        val future = miniCorpNode.startFlow(p)
        mockNet.runNetwork()
        future.getOrThrow()
    }

    @Test
    fun `can resolve a chain of transactions containing a notary change transaction`() {
        val tx = notaryChangeChain()
        var numUpdates = 0
        var notaryChangeTxSeen = false
        miniCorpNode.services.validatedTransactions.updates.subscribe {
            numUpdates++
            notaryChangeTxSeen = it.coreTransaction is NotaryChangeWireTransaction || notaryChangeTxSeen
        }
        val p = TestFlow(tx, megaCorp)
        val future = miniCorpNode.startFlow(p)
        mockNet.runNetwork()
        future.getOrThrow()
        assertEquals(2, numUpdates)
        assertTrue(notaryChangeTxSeen)
    }

    @Test
    fun `can resolve a chain of transactions containing a contract upgrade transaction`() {
        val tx = contractUpgradeChain()
        var numUpdates = 0
        var upgradeTxSeen = false
        miniCorpNode.services.validatedTransactions.updates.subscribe {
            numUpdates++
            upgradeTxSeen = it.coreTransaction is ContractUpgradeWireTransaction || upgradeTxSeen
        }
        val p = TestFlow(tx, megaCorp)
        val future = miniCorpNode.startFlow(p)
        mockNet.runNetwork()
        future.getOrThrow()
        assertEquals(2, numUpdates)
        assertTrue(upgradeTxSeen)
    }

    // Used for checking larger chains resolve correctly. Note that this takes a long time to run, and so is not suitable for a CI gate.
    @Test
    @Ignore
    fun `Can resolve large chain of transactions`() {
        val txToResolve = makeLargeTransactionChain(2500)
        val p = TestFlow(txToResolve, megaCorp)
        val future = miniCorpNode.startFlow(p)
        mockNet.runNetwork()
        future.getOrThrow()
    }

    // DOCSTART 2
    private fun makeTransactions(signFirstTX: Boolean = true, withAttachment: SecureHash? = null): Pair<SignedTransaction, SignedTransaction> {
        // Make a chain of custody of dummy states and insert into node A.
        val dummy1: SignedTransaction = DummyContract.generateInitial(0, notary, megaCorp.ref(1)).let {
            if (withAttachment != null)
                it.addAttachment(withAttachment)
            when (signFirstTX) {
                true -> {
                    val ptx = megaCorpNode.services.signInitialTransaction(it)
                    notaryNode.services.addSignature(ptx, notary.owningKey)
                }
                false -> {
                    notaryNode.services.signInitialTransaction(it, notary.owningKey)
                }
            }
        }
        megaCorpNode.transaction {
            megaCorpNode.services.recordTransactions(dummy1)
        }
        val dummy2: SignedTransaction = DummyContract.move(dummy1.tx.outRef(0), miniCorp).let {
            val ptx = megaCorpNode.services.signInitialTransaction(it)
            notaryNode.services.addSignature(ptx, notary.owningKey)
        }
        megaCorpNode.transaction {
            megaCorpNode.services.recordTransactions(dummy2)
        }
        return Pair(dummy1, dummy2)
    }
    // DOCEND 2

    private fun makeLargeTransactionChain(chainLength: Int): SignedTransaction {
        var currentTx = DummyContract.generateInitial(0, notary, megaCorp.ref(1)).let {
            val ptx = megaCorpNode.services.signInitialTransaction(it)
            notaryNode.services.addSignature(ptx, notary.owningKey)
        }
        megaCorpNode.transaction {
            megaCorpNode.services.recordTransactions(currentTx)
        }

        for (i in 1 until chainLength) {
            currentTx = DummyContract.move(currentTx.tx.outRef(0), miniCorp).let {
                val ptx = megaCorpNode.services.signInitialTransaction(it)
                val ptx2 = miniCorpNode.services.addSignature(ptx, miniCorp.owningKey)
                notaryNode.services.addSignature(ptx2, notary.owningKey)
            }
            megaCorpNode.transaction {
                megaCorpNode.services.recordTransactions(currentTx)
            }
        }
        return currentTx
    }

    private fun createNotaryChangeTransaction(inputs: List<StateRef>): SignedTransaction {
        val notaryTx = NotaryChangeTransactionBuilder(inputs, notary, newNotary, notaryNode.services.networkParametersService.defaultHash).build()
        val notaryKey = notary.owningKey
        val signableData = SignableData(notaryTx.id, SignatureMetadata(4, Crypto.findSignatureScheme(notaryKey).schemeNumberID))
        val signature = notaryNode.services.keyManagementService.sign(signableData, notaryKey)
        val newNotarySig = newNotaryNode.services.keyManagementService.sign(signableData, newNotary.owningKey)
        val ownerSig = megaCorpNode.services.keyManagementService.sign(signableData, megaCorp.owningKey)
        return SignedTransaction(notaryTx, listOf(signature, newNotarySig, ownerSig))
    }

    private fun notaryChangeChain(): SignedTransaction {
        var currentTx = DummyContract.generateInitial(0, notary, megaCorp.ref(1)).let {
            val ptx = megaCorpNode.services.signInitialTransaction(it)
            notaryNode.services.addSignature(ptx, notary.owningKey)
        }
        megaCorpNode.transaction {
            megaCorpNode.services.recordTransactions(currentTx)
        }

        currentTx = createNotaryChangeTransaction(currentTx.tx.outRefsOfType<ContractState>().map { it.ref })
        megaCorpNode.transaction {
            megaCorpNode.services.recordTransactions(currentTx)
        }

        val ledgerTx = (currentTx.coreTransaction as NotaryChangeWireTransaction).resolve(megaCorpNode.services, currentTx.sigs)
        val outState = ledgerTx.outRef<DummyContract.SingleOwnerState>(0)

        currentTx = DummyContract.move(outState, miniCorp).let {
            val ptx = megaCorpNode.services.signInitialTransaction(it)
            val ptx2 = miniCorpNode.services.addSignature(ptx, miniCorp.owningKey)
            newNotaryNode.services.addSignature(ptx2, newNotary.owningKey)
        }
        megaCorpNode.transaction {
            megaCorpNode.services.recordTransactions(currentTx)
        }
        return currentTx
    }

    private fun createContractUpgradeTransaction(inputs: List<StateRef>, previousTx: SignedTransaction): SignedTransaction {
        val contractTx = ContractUpgradeTransactionBuilder(
                inputs,
                notary,
                previousTx.tx.attachments.first(),
                DummyContractV2.PROGRAM_ID,
                previousTx.tx.attachments.first(),
                networkParametersHash = notaryNode.services.networkParametersService.defaultHash
        ).build()
        val notaryKey = notary.owningKey
        val signableData = SignableData(contractTx.id, SignatureMetadata(4, Crypto.findSignatureScheme(notaryKey).schemeNumberID))
        val signature = notaryNode.services.keyManagementService.sign(signableData, notaryKey)
        val ownerSig = megaCorpNode.services.keyManagementService.sign(signableData, megaCorp.owningKey)
        return SignedTransaction(contractTx, listOf(signature, ownerSig))
    }

    private fun contractUpgradeChain(): SignedTransaction {
        var currentTx = DummyContract.generateInitial(0, notary, megaCorp.ref(1)).let {
            val ptx = megaCorpNode.services.signInitialTransaction(it)
            notaryNode.services.addSignature(ptx, notary.owningKey)
        }
        megaCorpNode.transaction {
            megaCorpNode.services.recordTransactions(currentTx)
        }

        currentTx = createContractUpgradeTransaction(currentTx.tx.outRefsOfType<ContractState>().map { it.ref }, currentTx)
        megaCorpNode.transaction {
            megaCorpNode.services.recordTransactions(currentTx)
        }

        val ledgerTx = (currentTx.coreTransaction as ContractUpgradeWireTransaction).resolve(megaCorpNode.services, currentTx.sigs)
        val outState = ledgerTx.outRef<DummyContractV2.State>(0)

        currentTx = DummyContractV2.move(outState, miniCorp).let {
            val ptx = megaCorpNode.services.signInitialTransaction(it)
            val ptx2 = miniCorpNode.services.addSignature(ptx, miniCorp.owningKey)
            notaryNode.services.addSignature(ptx2, notary.owningKey)
        }
        megaCorpNode.transaction {
            megaCorpNode.services.recordTransactions(currentTx)
        }
        return currentTx
    }

    @InitiatingFlow
    open class TestFlow(private val otherSide: Party, private val resolveTransactionsFlowFactory: (FlowSession) -> ResolveTransactionsFlow) : FlowLogic<Unit>() {
        constructor(txHashes: Set<SecureHash>, otherSide: Party) : this(otherSide, { ResolveTransactionsFlow(txHashes, it) })
        constructor(stx: SignedTransaction, otherSide: Party) : this(otherSide, { ResolveTransactionsFlow(stx, it) })

        @Suspendable
        override fun call() {
            val session = initiateFlow(otherSide)
            val resolveTransactionsFlow = resolveTransactionsFlowFactory(session)
            subFlow(resolveTransactionsFlow)
        }
    }
    @Suppress("unused")
    @InitiatedBy(TestFlow::class)
    class TestResponseFlow(private val otherSideSession: FlowSession) : FlowLogic<Void?>() {
        @Suspendable
        override fun call() = subFlow(TestNoSecurityDataVendingFlow(otherSideSession))
    }

    // Used by the no-rights test
    @InitiatingFlow
    private class TestNoRightsVendingFlow(val otherSide: Party, val toVend: SignedTransaction, val toRequest: SignedTransaction) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val session = initiateFlow(otherSide)
            session.send(toRequest)
            subFlow(DataVendingFlow(session, toVend))
        }
    }
    @Suppress("unused")
    @InitiatedBy(TestNoRightsVendingFlow::class)
    private open class TestResponseResolveNoRightsFlow(val otherSideSession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val noRightsTx = otherSideSession.receive<SignedTransaction>().unwrap { it }
            otherSideSession.receive<Any>().unwrap { it }
            otherSideSession.sendAndReceive<Any>(FetchDataFlow.Request.Data(NonEmptySet.of(noRightsTx.inputs.first().txhash), FetchDataFlow.DataType.TRANSACTION)).unwrap { it }
            otherSideSession.send(FetchDataFlow.Request.End)
        }
    }

    //Used by the resolve twice test
    @InitiatingFlow
    private class TestResolveTwiceVendingFlow(val otherSide: Party, val tx: SignedTransaction) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val session = initiateFlow(otherSide)
            subFlow(DataVendingFlow(session, tx))
        }
    }
    @Suppress("unused")
    @InitiatedBy(TestResolveTwiceVendingFlow::class)
    private open class TestResponseResolveTwiceFlow(val otherSideSession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val tx = otherSideSession.receive<SignedTransaction>().unwrap { it }
            val parent1 = tx.inputs.first().txhash
            otherSideSession.sendAndReceive<Any>(FetchDataFlow.Request.Data(NonEmptySet.of(parent1), FetchDataFlow.DataType.TRANSACTION)).unwrap { it }
            otherSideSession.sendAndReceive<Any>(FetchDataFlow.Request.Data(NonEmptySet.of(parent1), FetchDataFlow.DataType.TRANSACTION)).unwrap { it }
            otherSideSession.send(FetchDataFlow.Request.End)
        }
    }
}
