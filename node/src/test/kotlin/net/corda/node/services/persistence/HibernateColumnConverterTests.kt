package net.corda.node.services.persistence

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.`issued by`
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.AbstractCashFlow
import net.corda.finance.issuedBy
import net.corda.node.services.identity.PersistentIdentityService
import net.corda.node.services.keys.E2ETestKeyManagementService
import net.corda.testing.core.BOC_NAME
import net.corda.testing.internal.TestingNamedCacheFactory
import net.corda.testing.node.InMemoryMessagingNetwork.ServicePeerAllocationStrategy.RoundRobin
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.internal.FINANCE_CORDAPPS
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

class HibernateColumnConverterTests {
    private lateinit var mockNet: MockNetwork
    private lateinit var bankOfCordaNode: StartedMockNode
    private lateinit var bankOfCorda: Party
    private lateinit var notary: Party

    class TestCashIssueFlow(private val amount: Amount<Currency>,
                            private val issuerBankPartyRef: OpaqueBytes,
                            private val notary: Party) : AbstractCashFlow<AbstractCashFlow.Result>(tracker()) {
        @Suspendable
        override fun call(): AbstractCashFlow.Result {
            val builder = TransactionBuilder(notary)
            val issuer = ourIdentity.ref(issuerBankPartyRef)
            val signers = Cash().generateIssue(builder, amount.issuedBy(issuer), ourIdentity, notary)
            val tx = serviceHub.signInitialTransaction(builder, signers)
            serviceHub.recordTransactions(tx)
            return Result(tx, ourIdentity)
        }
    }
    
    @Before
    fun start() {
        mockNet = MockNetwork(MockNetworkParameters(servicePeerAllocationStrategy = RoundRobin(), cordappsForAllNodes = FINANCE_CORDAPPS))
        bankOfCordaNode = mockNet.createPartyNode(BOC_NAME)
        bankOfCorda = bankOfCordaNode.info.identityFromX500Name(BOC_NAME)
        notary = mockNet.defaultNotaryIdentity
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    // AbstractPartyToX500NameAsStringConverter could cause circular flush of Hibernate session because it is invoked during flush, and a
    // cache miss was doing a flush.  This also checks that loading during flush does actually work.
    @Test
    fun `issue some cash on a notary that exists only in the database to check cache loading works in our identity column converters during flush of vault update`() {
        val expected = 500.DOLLARS
        val ref = OpaqueBytes.of(0x01)

        // Create parallel set of key and identity services so that the values are not cached, forcing the node caches to do a lookup.
        val identityService = PersistentIdentityService(TestingNamedCacheFactory())
        val originalIdentityService: PersistentIdentityService = bankOfCordaNode.services.identityService as PersistentIdentityService
        identityService.database = originalIdentityService.database
        identityService.start(originalIdentityService.trustRoot)
        val keyService = E2ETestKeyManagementService(identityService)
        keyService.start((bankOfCordaNode.services.keyManagementService as E2ETestKeyManagementService).keyPairs)

        // New identity for a notary (doesn't matter that it's for Bank Of Corda... since not going to use it as an actual notary etc).
        val newKeyAndCert = keyService.freshKeyAndCert(bankOfCordaNode.info.legalIdentitiesAndCerts[0], false)
        val randomNotary = Party(BOC_NAME, newKeyAndCert.owningKey)

        val future = bankOfCordaNode.startFlow(TestCashIssueFlow(expected, ref, randomNotary))
        mockNet.runNetwork()
        val issueTx = future.getOrThrow().stx
        val output = issueTx.tx.outputsOfType<Cash.State>().single()
        assertEquals(expected.`issued by`(bankOfCorda.ref(ref)), output.amount)
    }
}