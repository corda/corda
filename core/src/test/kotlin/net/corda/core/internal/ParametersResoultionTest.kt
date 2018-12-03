package net.corda.core.internal

import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignableData
import net.corda.core.crypto.SignatureMetadata
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.NetworkParameters
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
import net.corda.testing.node.StartedMockNode
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class ParametersResoultionTest {
    // FetchParametersFlow is enabled if minimumPlatformVersion is >= 4
    private var params1: NetworkParameters = testNetworkParameters(epoch = 1, minimumPlatformVersion = 4)
    private var params2: NetworkParameters = testNetworkParameters(epoch = 2, minimumPlatformVersion = 4)
    private var params3: NetworkParameters = testNetworkParameters(epoch = 3, minimumPlatformVersion = 4)
    private var params4: NetworkParameters = testNetworkParameters(epoch = 4, minimumPlatformVersion = 4)

    private val certKeyPair: CertificateAndKeyPair = createDevNetworkMapCa()

    private lateinit var mockNet: MockNetwork
    private lateinit var notaryNode: StartedMockNode
    private lateinit var megaCorpNode: StartedMockNode
    private lateinit var miniCorpNode: StartedMockNode
    private lateinit var megaCorp: Party
    private lateinit var miniCorp: Party
    private lateinit var notary: Party

    @Before
    fun setup() {
        mockNet = MockNetwork(cordappPackages = listOf("net.corda.testing.contracts", "net.corda.core.internal"), networkParameters = params3)
        notaryNode = mockNet.defaultNotaryNode
        megaCorpNode = mockNet.createPartyNode(CordaX500Name("MegaCorp", "London", "GB"))
        miniCorpNode = mockNet.createPartyNode(CordaX500Name("MiniCorp", "London", "GB"))
        notary = mockNet.defaultNotaryIdentity
        megaCorp = megaCorpNode.info.singleIdentity()
        miniCorp = miniCorpNode.info.singleIdentity()
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
        val dummy1: SignedTransaction = DummyContract.generateInitial(0, notary, megaCorp.ref(1)).let {
            val ptx = it.toSignedTransactionWithParameters(parameters1, megaCorpNode.services)
            notaryNode.services.addSignature(ptx, notary.owningKey)
        }
        val dummy2: SignedTransaction = DummyContract.move(dummy1.tx.outRef(0), miniCorp).let {
            val ptx = it.toSignedTransactionWithParameters(parameters2, megaCorpNode.services)
            notaryNode.services.addSignature(ptx, notary.owningKey)
        }
        megaCorpNode.transaction {
            megaCorpNode.services.recordTransactions(dummy1, dummy2)
            // Record parameters too.
            with(megaCorpNode.services.networkParametersStorage) {
                parameters1?.let { saveParameters(certKeyPair.sign(it)) }
                parameters2?.let { saveParameters(certKeyPair.sign(it)) }
            }
        }
        return Pair(dummy1, dummy2)
    }

    @Test
    fun `parameters all null`() {
        val (stx1, stx2) = makeTransactions(null, null)
        assertThat(stx1.networkParametersHash).isNull()
        assertThat(stx2.networkParametersHash).isNull()
        val p = ResolveTransactionsFlowTest.TestFlow(setOf(stx2.id), megaCorp)
        val future = miniCorpNode.startFlow(p)
        mockNet.runNetwork()
        future.getOrThrow()
        miniCorpNode.transaction {
            assertEquals(stx1, miniCorpNode.services.validatedTransactions.getTransaction(stx1.id))
            assertEquals(stx2, miniCorpNode.services.validatedTransactions.getTransaction(stx2.id))
        }
    }

    @Test
    fun `transaction chain out of order parameters`() {
        val hash1 = params1.serialize().hash
        val hash2 = params2.serialize().hash
        val (stx1, stx2) = makeTransactions(params2, params1)
        assertThat(stx1.networkParametersHash).isEqualTo(hash2)
        assertThat(stx2.networkParametersHash).isEqualTo(hash1)
        miniCorpNode.transaction {
            assertThat(miniCorpNode.services.networkParametersStorage.lookup(hash1)).isNull()
            assertThat(miniCorpNode.services.networkParametersStorage.lookup(hash2)).isNull()
        }
        val p = ResolveTransactionsFlowTest.TestFlow(setOf(stx2.id), megaCorp)
        val future = miniCorpNode.startFlow(p)
        mockNet.runNetwork()
        Assertions.assertThatIllegalArgumentException().isThrownBy {
            future.getOrThrow()
        }.withMessageContaining("Network parameters are not ordered in the transaction graph for dependency transaction:" +
                " ${stx1.id} and parent parameters hash: $hash1")
        miniCorpNode.transaction {
            assertThat(miniCorpNode.services.validatedTransactions.getTransaction(stx1.id)).isNull()
            assertThat(miniCorpNode.services.validatedTransactions.getTransaction(stx2.id)).isNull()
            // Even though the resolution failed, we should still have downloaded the parameters to the storage.
            assertThat(miniCorpNode.services.networkParametersStorage.lookup(hash1)).isEqualTo(params1)
            assertThat(miniCorpNode.services.networkParametersStorage.lookup(hash2)).isEqualTo(params2)
        }
    }

    @Test
    fun `transaction chain out of order parameters with default`() {
        val defaultHash = megaCorpNode.services.networkParametersStorage.defaultHash
        val hash7 = params4.serialize().hash
        // stx1 with epoch 4 -> stx2 with default epoch, which is 3
        val (stx1, stx2) = makeTransactions(params4, null)
        assertThat(stx2.networkParametersHash).isNull()
        assertThat(stx1.networkParametersHash).isEqualTo(hash7)
        val p = ResolveTransactionsFlowTest.TestFlow(stx2, megaCorp)
        val future = miniCorpNode.startFlow(p)
        mockNet.runNetwork()
        Assertions.assertThatIllegalArgumentException().isThrownBy {
            future.getOrThrow()
        }.withMessageContaining("Network parameters are not ordered in the transaction graph for dependency transaction:" +
                " ${stx1.id} and parent parameters hash: $defaultHash")
        miniCorpNode.transaction {
            assertThat(miniCorpNode.services.validatedTransactions.getTransaction(stx1.id)).isNull()
            assertThat(miniCorpNode.services.validatedTransactions.getTransaction(stx2.id)).isNull()
            assertThat(miniCorpNode.services.networkParametersStorage.lookup(hash7)).isEqualTo(params4)
        }
    }

    @Test
    fun `resolve with current`() {
        // Epoch 2 and 4 but current is 3
        val currentHash = megaCorpNode.services.networkParametersStorage.currentHash
        val (stx1, stx2) = makeTransactions(params2, params4)
        // This version of flow will use current parameters for the network in root node.
        val p = ResolveTransactionsFlowTest.TestFlow(setOf(stx1.id, stx2.id), megaCorp)
        val future = miniCorpNode.startFlow(p)
        mockNet.runNetwork()
        Assertions.assertThatIllegalArgumentException().isThrownBy {
            future.getOrThrow()
        }.withMessageContaining("Network parameters are not ordered in the transaction graph for dependency transaction:" +
                " ${stx2.id} and parent parameters hash: $currentHash")
    }

    @Test
    fun `incorrect triangle of transactions`() {
        // stx1 with epoch 2, stx2 with epoch 2, stx3 with epoch 3
        // stx1 -> stx2, stx1 -> stx3, stx2 -> stx3
        val stx1 = makeTransactions(params2, null).first

        val stx2 = DummyContract.move(stx1.tx.outRef(0), miniCorp).let { builder ->
            val ptx = builder.toSignedTransactionWithParameters(params1, megaCorpNode.services)
            notaryNode.services.addSignature(ptx, notary.owningKey)
        }

        val stx3 = DummyContract.move(listOf(stx1.tx.outRef(0), stx2.tx.outRef(0)), miniCorp).let { builder ->
            val ptx = builder.toSignedTransactionWithParameters(params3, megaCorpNode.services)
            notaryNode.services.addSignature(ptx, notary.owningKey)
        }

        megaCorpNode.transaction {
            megaCorpNode.services.recordTransactions(stx2, stx3)
            megaCorpNode.services.networkParametersStorage.saveParameters(certKeyPair.sign(params1))
            megaCorpNode.services.networkParametersStorage.saveParameters(certKeyPair.sign(params3))
        }

        val p = ResolveTransactionsFlowTest.TestFlow(setOf(stx3.id), megaCorp)
        val future = miniCorpNode.startFlow(p)
        mockNet.runNetwork()
        Assertions.assertThatIllegalArgumentException().isThrownBy {
            future.getOrThrow()
        }.withMessageContaining("Network parameters are not ordered in the transaction graph for dependency transaction:" +
                " ${stx1.id} and parent parameters hash: ${stx2.networkParametersHash}")
    }
}