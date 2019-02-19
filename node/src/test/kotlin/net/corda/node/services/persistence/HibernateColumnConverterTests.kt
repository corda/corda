package net.corda.node.services.persistence

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.services.KeyManagementService
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.`issued by`
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.AbstractCashFlow
import net.corda.finance.issuedBy
import net.corda.node.migration.VaultStateMigrationTest.Companion.bankOfCorda
import net.corda.node.services.identity.PersistentIdentityService
import net.corda.node.services.keys.BasicHSMKeyManagementService
import net.corda.node.services.keys.E2ETestKeyManagementService
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.BOC_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.internal.TestingNamedCacheFactory
import net.corda.testing.node.InMemoryMessagingNetwork.ServicePeerAllocationStrategy.RoundRobin
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.MockServices
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.internal.FINANCE_CORDAPPS
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.TestStartedNode
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

class HibernateColumnConverterTests {

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private val cordapps = listOf("net.corda.finance")

    private val myself = TestIdentity(CordaX500Name("Me", "London", "GB"))
    private val notary = TestIdentity(CordaX500Name("NotaryService", "London", "GB"), 1337L)

    lateinit var services: MockServices
    lateinit var database: CordaPersistence

    @Before
    fun setUp() {
        val (db, mockServices) = MockServices.makeTestDatabaseAndPersistentServices(
                cordappPackages = cordapps,
                initialIdentity = myself,
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4),
                moreIdentities = setOf(notary.identity),
                moreKeys = emptySet()
        )
        services = mockServices
        database = db
    }

    // AbstractPartyToX500NameAsStringConverter could cause circular flush of Hibernate session because it is invoked during flush, and a
    // cache miss was doing a flush.  This also checks that loading during flush does actually work.
    @Test
    fun `issue some cash on a notary that exists only in the database to check cache loading works in our identity column converters during flush of vault update`() {
        val expected = 500.DOLLARS
        val ref = OpaqueBytes.of(0x01)

        // Create parallel set of key and identity services so that the values are not cached, forcing the node caches to do a lookup.
        val identityService = PersistentIdentityService(TestingNamedCacheFactory())
        val originalIdentityService: PersistentIdentityService = services.identityService as PersistentIdentityService
        identityService.database = originalIdentityService.database
        identityService.start(originalIdentityService.trustRoot)
        val keyService = E2ETestKeyManagementService(identityService)
        keyService.start(setOf(myself.keyPair))

        // New identity for a notary (doesn't matter that it's for Bank Of Corda... since not going to use it as an actual notary etc).
        val newKeyAndCert = keyService.freshKeyAndCert(services.myInfo.legalIdentitiesAndCerts[0], false)
        val randomNotary = Party(myself.name, newKeyAndCert.owningKey)

        val ourIdentity = services.myInfo.legalIdentities.first()
        val builder = TransactionBuilder(notary.party)
        val issuer = services.myInfo.legalIdentities.first().ref(ref)
        val signers = Cash().generateIssue(builder, expected.issuedBy(issuer), ourIdentity, randomNotary)
        val tx: SignedTransaction = services.signInitialTransaction(builder, signers)
        services.recordTransactions(tx)

        val output = tx.tx.outputsOfType<Cash.State>().single()
        assertEquals(expected.`issued by`(ourIdentity.ref(ref)), output.amount)
    }
}