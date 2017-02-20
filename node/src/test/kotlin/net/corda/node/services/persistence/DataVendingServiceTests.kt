package net.corda.node.services.persistence

import co.paralleluniverse.fibers.Suspendable
import net.corda.contracts.asset.Cash
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Issued
import net.corda.core.contracts.TransactionType
import net.corda.core.contracts.USD
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Party
import net.corda.core.flows.FlowLogic
import net.corda.core.node.services.unconsumedStates
import net.corda.core.flows.FlowVersion
import net.corda.core.node.services.ServiceInfo
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.core.utilities.DUMMY_NOTARY_KEY
import net.corda.flows.BroadcastTransactionFlow.NotifyTxRequest
import net.corda.node.services.network.NetworkMapService
import net.corda.node.services.persistence.DataVending.Service.NotifyTransactionHandler
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.node.utilities.databaseTransaction
import net.corda.testing.MEGA_CORP
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetwork.MockNode
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Tests for the data vending service.
 */
class DataVendingServiceTests {
    lateinit var network: MockNetwork
    lateinit var vaultServiceNode: MockNetwork.MockNode
    lateinit var registerNode: MockNode
    lateinit var beneficiary: CompositeKey

    @Before
    fun setup() {
        network = MockNetwork()
        val notaryService = ServiceInfo(SimpleNotaryService.type)
        val notaryNode = network.createNode(
                legalName = DUMMY_NOTARY.name,
                overrideServices = mapOf(Pair(notaryService, DUMMY_NOTARY_KEY)),
                advertisedServices = *arrayOf(ServiceInfo(NetworkMapService.type), notaryService))
        vaultServiceNode = network.createPartyNode(notaryNode.info.address, start = false)
        registerNode = network.createPartyNode(notaryNode.info.address, start = false)
        vaultServiceNode.services.registerFlowInitiator(NotifyTxFlow::class, ::NotifyTransactionHandler)
        vaultServiceNode.start()
        registerNode.start()
        beneficiary = vaultServiceNode.info.legalIdentity.owningKey
    }

    @Test
    fun `notify of transaction`() {
        val deposit = registerNode.info.legalIdentity.ref(1)
        network.runNetwork()

        // Generate an issuance transaction
        val ptx = TransactionType.General.Builder(null)
        Cash().generateIssue(ptx, Amount(100, Issued(deposit, USD)), beneficiary, DUMMY_NOTARY)

        // Complete the cash transaction, and then manually relay it
        val registerKey = registerNode.services.legalIdentityKey
        ptx.signWith(registerKey)
        val tx = ptx.toSignedTransaction()
        databaseTransaction(vaultServiceNode.database) {
            assertThat(vaultServiceNode.services.vaultService.unconsumedStates<Cash.State>()).isEmpty()

            registerNode.sendNotifyTx(tx, vaultServiceNode)

            // Check the transaction is in the receiving node
            val actual = vaultServiceNode.services.vaultService.unconsumedStates<Cash.State>().singleOrNull()
            val expected = tx.tx.outRef<Cash.State>(0)
            assertEquals(expected, actual)
        }
    }

    /**
     * Test that invalid transactions are rejected.
     */
    @Test
    fun `notify failure`() {
        val deposit = MEGA_CORP.ref(1)
        network.runNetwork()

        // Generate an issuance transaction
        val ptx = TransactionType.General.Builder(DUMMY_NOTARY)
        Cash().generateIssue(ptx, Amount(100, Issued(deposit, USD)), beneficiary, DUMMY_NOTARY)

        // The transaction tries issuing MEGA_CORP cash, but we aren't the issuer, so it's invalid
        val registerKey = registerNode.services.legalIdentityKey
        ptx.signWith(registerKey)
        val tx = ptx.toSignedTransaction(false)
        databaseTransaction(vaultServiceNode.database) {
            assertThat(vaultServiceNode.services.vaultService.unconsumedStates<Cash.State>()).isEmpty()

            registerNode.sendNotifyTx(tx, vaultServiceNode)

            // Check the transaction is not in the receiving node
            assertThat(vaultServiceNode.services.vaultService.unconsumedStates<Cash.State>()).isEmpty()
        }
    }

    private fun MockNode.sendNotifyTx(tx: SignedTransaction, walletServiceNode: MockNode) {
//        walletServiceNode.services.registerFlowInitiator(NotifyTxFlow::class.java, ::NotifyTransactionHandler)
        services.startFlow(NotifyTxFlow(walletServiceNode.info.legalIdentity, tx))
        network.runNetwork()
    }

    @FlowVersion("1.0")
    private class NotifyTxFlow(val otherParty: Party, val stx: SignedTransaction) : FlowLogic<Unit>() {
        override fun getCounterpartyMarker(party: Party): String = "BroadcastTransactionFlow"
        @Suspendable
        override fun call() = send(otherParty, NotifyTxRequest(stx))
    }

}
