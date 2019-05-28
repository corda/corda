package net.corda.node.services.vault

import net.corda.core.crypto.Crypto
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.transactions.TransactionBuilder
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyState
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

class ExternalIdMappingTest {

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private val cordapps = listOf(
            "net.corda.node.services.persistence",
            "net.corda.testing.contracts"
    )

//    lateinit var myself: TestIdentity
//    lateinit var notary: TestIdentity
//
//    lateinit var services: MockServices
//    lateinit var database: CordaPersistence

    private val bcProviderName = BouncyCastleProvider().name
//    private val bcProvider = Crypto.findProvider(bcProviderName)

    @Before
    fun setUp() {
    }

    private fun createDummyState(participants: List<AbstractParty>, notary: TestIdentity, services: MockServices, database: CordaPersistence): DummyState {
        val tx = TransactionBuilder(notary = notary.party).apply {
            addOutputState(DummyState(1, participants), DummyContract.PROGRAM_ID)
            addCommand(DummyContract.Commands.Create(), participants.map { it.owningKey })
        }
        val stx = services.signInitialTransaction(tx)
        database.transaction { services.recordTransactions(stx) }
        return stx.tx.outputsOfType<DummyState>().single()
    }

    @Test
    fun `Two states can be mapped to a single externalId`() {

        // BEGIN: Setup()
        println("Registering Crypto Providers ...")
        Crypto.registerProviders()
        println("#1 $bcProviderName identityHashCode = ${System.identityHashCode(Crypto.findProvider(bcProviderName))} (${Crypto.findProvider(bcProviderName).size})")

        val myself = TestIdentity(CordaX500Name("Me", "London", "GB"))
        println("#2 $bcProviderName identityHashCode = ${System.identityHashCode(Crypto.findProvider(bcProviderName))} (${Crypto.findProvider(bcProviderName).size})")

        val notary = TestIdentity(CordaX500Name("NotaryService", "London", "GB"), 1337L)
        println("#3 $bcProviderName identityHashCode = ${System.identityHashCode(Crypto.findProvider(bcProviderName))} (${Crypto.findProvider(bcProviderName).size})")

        val (db, mockServices) = MockServices.makeTestDatabaseAndPersistentServices(
                cordappPackages = cordapps,
                initialIdentity = myself,
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4),
                moreIdentities = setOf(notary.identity),
                moreKeys = emptySet()
        )
        val services = mockServices
        val database = db
        println("#4 $bcProviderName identityHashCode = ${System.identityHashCode(Crypto.findProvider(bcProviderName))} (${Crypto.findProvider(bcProviderName).size})")
        // END: SetUp()

        // Create new external ID and two keys mapped to it.
        val id = UUID.randomUUID()
        println("#5 $bcProviderName identityHashCode = ${System.identityHashCode(Crypto.findProvider(bcProviderName))} (${Crypto.findProvider(bcProviderName).size})")
        val keyOne = services.keyManagementService.freshKeyAndCert(myself.identity, false, id)

        println("#6 $bcProviderName identityHashCode = ${System.identityHashCode(Crypto.findProvider(bcProviderName))} (${Crypto.findProvider(bcProviderName).size})")
        val keyTwo = services.keyManagementService.freshKeyAndCert(myself.identity, false, id)

        println("#7 $bcProviderName identityHashCode = ${System.identityHashCode(Crypto.findProvider(bcProviderName))} (${Crypto.findProvider(bcProviderName).size})")
        // Create states with a public key assigned to the new external ID.
        val dummyStateOne = createDummyState(listOf(AnonymousParty(keyOne.owningKey)), notary, services, database)
        val dummyStateTwo = createDummyState(listOf(AnonymousParty(keyTwo.owningKey)), notary, services, database)
        // This query should return two states!
        val vaultService = services.vaultService
        val result = database.transaction {
            val externalId = builder { VaultSchemaV1.StateToExternalId::externalId.`in`(listOf(id)) }
            val queryCriteria = QueryCriteria.VaultCustomQueryCriteria(externalId)
            vaultService.queryBy<DummyState>(queryCriteria).states
        }
        assertEquals(setOf(dummyStateOne, dummyStateTwo), result.map { it.state.data }.toSet())

        // This query should return two states!
        val resultTwo = database.transaction {
            val externalId = builder { VaultSchemaV1.StateToExternalId::externalId.equal(id) }
            val queryCriteria = QueryCriteria.VaultCustomQueryCriteria(externalId)
            vaultService.queryBy<DummyState>(queryCriteria).states
        }
        assertEquals(setOf(dummyStateOne, dummyStateTwo), resultTwo.map { it.state.data }.toSet())
    }

    @Test
    fun `One state can be mapped to multiple externalIds`() {

        // BEGIN: Setup()
        println("Registering Crypto Providers ...")
        Crypto.registerProviders()
        println("#1 $bcProviderName identityHashCode = ${System.identityHashCode(Crypto.findProvider(bcProviderName))} (${Crypto.findProvider(bcProviderName).size})")

        val myself = TestIdentity(CordaX500Name("Me", "London", "GB"))
        println("#2 $bcProviderName identityHashCode = ${System.identityHashCode(Crypto.findProvider(bcProviderName))} (${Crypto.findProvider(bcProviderName).size})")

        val notary = TestIdentity(CordaX500Name("NotaryService", "London", "GB"), 1337L)
        println("#3 $bcProviderName identityHashCode = ${System.identityHashCode(Crypto.findProvider(bcProviderName))} (${Crypto.findProvider(bcProviderName).size})")

        val (db, mockServices) = MockServices.makeTestDatabaseAndPersistentServices(
                cordappPackages = cordapps,
                initialIdentity = myself,
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4),
                moreIdentities = setOf(notary.identity),
                moreKeys = emptySet()
        )
        val services = mockServices
        val database = db
        println("#4 $bcProviderName identityHashCode = ${System.identityHashCode(Crypto.findProvider(bcProviderName))} (${Crypto.findProvider(bcProviderName).size})")
        // END: SetUp()

        // Create new external ID.
        val idOne = UUID.randomUUID()
        println("#5 $bcProviderName identityHashCode = ${System.identityHashCode(Crypto.findProvider(bcProviderName))} (${Crypto.findProvider(bcProviderName).size})")
        val keyOne = services.keyManagementService.freshKeyAndCert(myself.identity, false, idOne)

        println("#6 $bcProviderName identityHashCode = ${System.identityHashCode(Crypto.findProvider(bcProviderName))} (${Crypto.findProvider(bcProviderName).size})")
        val idTwo = UUID.randomUUID()
        val keyTwo = services.keyManagementService.freshKeyAndCert(myself.identity, false, idTwo)

        println("#7 $bcProviderName identityHashCode = ${System.identityHashCode(Crypto.findProvider(bcProviderName))} (${Crypto.findProvider(bcProviderName).size})")
        // Create state with a public key assigned to the new external ID.
        val dummyState = createDummyState(listOf(AnonymousParty(keyOne.owningKey), AnonymousParty(keyTwo.owningKey)), notary, services, database)
        // This query should return one state!
        val vaultService = services.vaultService
        val result = database.transaction {
            val externalId = builder { VaultSchemaV1.StateToExternalId::externalId.`in`(listOf(idOne, idTwo)) }
            val queryCriteria = QueryCriteria.VaultCustomQueryCriteria(externalId)
            vaultService.queryBy<DummyState>(queryCriteria).states
        }
        assertEquals(dummyState, result.single().state.data)
    }
}