package net.corda.core.internal

import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignableData
import net.corda.core.crypto.SignatureMetadata
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NotaryInfo
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.SerializationFactory
import net.corda.core.serialization.serialize
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.nodeapi.internal.createDevNetworkMapCa
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.internal.DUMMY_CONTRACTS_CORDAPP
import net.corda.testing.node.internal.cordappForClasses
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test

class NetworkParametersResolutionTest {
    private lateinit var params2: NetworkParameters
    private val certKeyPair: CertificateAndKeyPair = createDevNetworkMapCa()
    private lateinit var mockNet: MockNetwork
    private lateinit var notaryNode: StartedMockNode
    private lateinit var megaCorpNode: StartedMockNode
    private lateinit var miniCorpNode: StartedMockNode
    private lateinit var megaCorpParty: Party
    private lateinit var miniCorpParty: Party
    private lateinit var notaryParty: Party

    @Before
    fun setup() {
        mockNet = MockNetwork(MockNetworkParameters(
                cordappsForAllNodes = listOf(DUMMY_CONTRACTS_CORDAPP,  cordappForClasses(ResolveTransactionsFlowTest.TestFlow::class.java, ResolveTransactionsFlowTest.TestResponseFlow::class.java))))
        notaryNode = mockNet.defaultNotaryNode
        megaCorpNode = mockNet.createPartyNode(CordaX500Name("MegaCorp", "London", "GB"))
        miniCorpNode = mockNet.createPartyNode(CordaX500Name("MiniCorp", "London", "GB"))
        notaryParty = mockNet.defaultNotaryIdentity
        megaCorpParty = megaCorpNode.info.singleIdentity()
        miniCorpParty = miniCorpNode.info.singleIdentity()
        params2 = testNetworkParameters(epoch = 2, minimumPlatformVersion = 3, notaries = listOf((NotaryInfo(notaryParty, true))))
    }

    @After
    fun tearDown() {
        mockNet.stopNodes()
    }

    // This function is resolving and signing WireTransaction with special parameters.
    private fun TransactionBuilder.toSignedTransactionWithParameters(parameters: NetworkParameters?, services: ServiceHub): SignedTransaction {
        val wtx = toWireTransaction(services)
        val wtxWithHash = SerializationFactory.defaultFactory.withCurrentContext(null) {
            WireTransaction(
                    createComponentGroups(
                            wtx.inputs,
                            wtx.outputs,
                            wtx.commands,
                            wtx.attachments,
                            wtx.notary,
                            wtx.timeWindow,
                            wtx.references,
                            parameters?.serialize()?.hash),
                    wtx.privacySalt
            )
        }
        val publicKey = services.myInfo.singleIdentity().owningKey
        val signatureMetadata = SignatureMetadata(services.myInfo.platformVersion, Crypto.findSignatureScheme(publicKey).schemeNumberID)
        val signableData = SignableData(wtxWithHash.id, signatureMetadata)
        val sig = services.keyManagementService.sign(signableData, publicKey)
        return SignedTransaction(wtxWithHash, listOf(sig))
    }

    // Similar to ResolveTransactionsFlowTest but creates transactions with given network parameters
    // First transaction in pair is dependency of the second one.
    private fun makeTransactions(parameters1: NetworkParameters?, parameters2: NetworkParameters?): Pair<SignedTransaction, SignedTransaction> {
        // Make a chain of custody of dummy states and insert into node A.
        val dummy1: SignedTransaction = DummyContract.generateInitial(0, notaryParty, megaCorpParty.ref(1)).let {
            val ptx = it.toSignedTransactionWithParameters(parameters1, megaCorpNode.services)
            notaryNode.services.addSignature(ptx, notaryParty.owningKey)
        }
        val dummy2: SignedTransaction = DummyContract.move(dummy1.tx.outRef(0), miniCorpParty).let {
            val ptx = it.toSignedTransactionWithParameters(parameters2, megaCorpNode.services)
            notaryNode.services.addSignature(ptx, notaryParty.owningKey)
        }
        megaCorpNode.transaction {
            megaCorpNode.services.recordTransactions(dummy1, dummy2)
            // Record parameters too.
            with(megaCorpNode.services.networkParametersService as NetworkParametersStorage) {
                parameters1?.let { saveParameters(certKeyPair.sign(it)) }
                parameters2?.let { saveParameters(certKeyPair.sign(it)) }
            }
        }
        return Pair(dummy1, dummy2)
    }

    @Test
    fun `request parameters that are not in the storage`() {
        val params1 = miniCorpNode.services.networkParameters
        val hash1 = params1.serialize().hash
        val hash2 = params2.serialize().hash
        // Create two transactions on megaCorpNode
        val (stx1, stx2) = makeTransactions(params1, params2)
        assertThat(stx1.networkParametersHash).isEqualTo(hash1)
        assertThat(stx2.networkParametersHash).isEqualTo(hash2)
        miniCorpNode.transaction {
            assertThat(miniCorpNode.services.networkParametersService.lookup(hash1)).isNotNull()
            assertThat(miniCorpNode.services.networkParametersService.lookup(hash2)).isNull()
        }
        // miniCorpNode resolves the stx2 from megaCorpParty
        val p = ResolveTransactionsFlowTest.TestFlow(setOf(stx2.id), megaCorpParty)
        val future = miniCorpNode.startFlow(p)
        mockNet.runNetwork()
        future.getOrThrow()
        miniCorpNode.transaction {
            // Check that parameters were downloaded to the storage.
            assertThat(miniCorpNode.services.networkParametersService.lookup(hash1)).isEqualTo(params1)
            assertThat(miniCorpNode.services.networkParametersService.lookup(hash2)).isEqualTo(params2)
        }
    }
}