package net.corda.node.services.transactions

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.*
import net.corda.core.flows.NotaryError
import net.corda.core.flows.NotaryException
import net.corda.core.flows.NotaryFlow
import net.corda.core.identity.Party
import net.corda.core.internal.NotaryChangeTransactionBuilder
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.serialize
import net.corda.core.transactions.NotaryChangeWireTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.internal.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFailsWith

class NotaryServiceTests {
    private lateinit var mockNet: InternalMockNetwork
    private lateinit var notaryServices: ServiceHub
    private lateinit var aliceNode: TestStartedNode
    private lateinit var notary: Party
    private lateinit var alice: Party

    @Before
    fun setup() {
        mockNet = InternalMockNetwork(
                cordappsForAllNodes = listOf(DUMMY_CONTRACTS_CORDAPP),
                notarySpecs = listOf(MockNetworkNotarySpec(DUMMY_NOTARY_NAME, validating = false)),
                initialNetworkParameters = testNetworkParameters(minimumPlatformVersion = 4)
        )
        aliceNode = mockNet.createNode(InternalMockNodeParameters(legalName = ALICE_NAME))
        notaryServices = mockNet.defaultNotaryNode.services //TODO get rid of that
        notary = mockNet.defaultNotaryIdentity
        alice = aliceNode.services.myInfo.singleIdentity()
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `should reject a transaction with too many inputs`() {
        notariseWithTooManyInputs(aliceNode, alice, notary, mockNet)
    }

    @Test
    fun `should reject when network parameters component is not visible`() {
        val stx = generateTransaction(aliceNode, alice, notary, null, 13)
        val future = aliceNode.services.startFlow(DummyClientFlow(stx, notary)).resultFuture
        mockNet.runNetwork()
        val ex = assertFailsWith<NotaryException> { future.getOrThrow() }
        val notaryError = ex.error as NotaryError.TransactionInvalid
        assertThat(notaryError.cause).hasMessageContaining("Transaction for notarisation doesn't contain network parameters hash.")
    }

    @Test
    fun `should reject when parameters not current`() {
        val hash = SecureHash.randomSHA256()
        val stx = generateTransaction(aliceNode, alice, notary, hash, 13)
        val future = aliceNode.services.startFlow(DummyClientFlow(stx, notary)).resultFuture
        mockNet.runNetwork()
        val ex = assertFailsWith<NotaryException> { future.getOrThrow() }
        val notaryError = ex.error as NotaryError.TransactionInvalid
        assertThat(notaryError.cause).hasMessageContaining("Transaction for notarisation contains unknown parameters hash: $hash")
    }

    internal companion object {
        /** This is used by both [NotaryServiceTests] and [ValidatingNotaryServiceTests]. */
        fun notariseWithTooManyInputs(node: TestStartedNode, party: Party, notary: Party, network: InternalMockNetwork) {
            val stx = generateTransaction(node, party, notary)

            val future = node.services.startFlow(DummyClientFlow(stx, notary)).resultFuture
            network.runNetwork()
            assertFailsWith<NotaryException> { future.getOrThrow() }
        }

        private fun generateTransaction(node: TestStartedNode,
                                        party: Party, notary: Party,
                                        paramsHash: SecureHash? = node.services.networkParametersService.currentHash,
                                        numberOfInputs: Int = 10_005): SignedTransaction {
            val txHash = SecureHash.randomSHA256()
            val inputs = (1..numberOfInputs).map { StateRef(txHash, it) }
            val tx = if (paramsHash != null) {
                NotaryChangeTransactionBuilder(inputs, notary, party, paramsHash).build()
            } else {
                NotaryChangeWireTransaction(listOf(inputs, notary, party).map { it.serialize() })
            }

            return node.services.run {
                val myKey = myInfo.legalIdentities.first().owningKey
                val signableData = SignableData(tx.id, SignatureMetadata(myInfo.platformVersion, Crypto.findSignatureScheme(myKey).schemeNumberID))
                val mySignature = keyManagementService.sign(signableData, myKey)
                SignedTransaction(tx, listOf(mySignature))
            }
        }

        private class DummyClientFlow(stx: SignedTransaction, val notary: Party) : NotaryFlow.Client(stx) {
            @Suspendable
            override fun call(): List<TransactionSignature> {
                notarise(notary)
                throw UnsupportedOperationException()
            }
        }
    }
}
