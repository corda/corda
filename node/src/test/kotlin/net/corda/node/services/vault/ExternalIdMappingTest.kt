package net.corda.node.services.vault

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.transactions.TransactionBuilder
import net.corda.node.VersionInfo
import net.corda.node.services.api.IdentityServiceInternal
import net.corda.node.services.identity.PersistentIdentityService
import net.corda.node.services.keys.BasicHSMKeyManagementService
import net.corda.node.services.keys.PublicKeyHashToExternalId
import net.corda.node.services.persistence.ExposeJpaToFlowsTests
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyState
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.internal.DEV_ROOT_CA
import net.corda.testing.internal.TestingNamedCacheFactory
import net.corda.testing.internal.rigorousMock
import net.corda.testing.node.MockServices
import net.corda.testing.node.internal.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.security.PublicKey
import java.util.*
import javax.persistence.criteria.CriteriaQuery
import javax.persistence.criteria.Root
import kotlin.test.assertEquals

class ExternalIdMappingTest {

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private val cordapps = listOf(
            "net.corda.node.services.persistence",
            "net.corda.testing.contracts"
    )

    private val myself = TestIdentity(CordaX500Name("Me", "London", "GB"))
    private val notary = TestIdentity(CordaX500Name("NotaryService", "London", "GB"), 1337L)

    lateinit var services: MockServices
    lateinit var database: CordaPersistence

    @Before
    fun setUp() {
        val persistentIdentityService = PersistentIdentityService(TestingNamedCacheFactory())
        val (db, mockServices) = MockServices.makeTestDatabaseAndMockServices(
                cordappPackages = cordapps,
                identityService = persistentIdentityService,
                initialIdentity = myself,
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4)
        )
        services = mockServices
        database = db
        persistentIdentityService.database = database
        persistentIdentityService.ourNames = setOf(myself.name)
        persistentIdentityService.start(DEV_ROOT_CA.certificate)
        database.transaction { persistentIdentityService.loadIdentities(listOf(myself.identity, notary.identity)) }
        services.keyManagementService
    }

    private fun createDummyState(participants: List<AbstractParty>): DummyState {
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
        val vaultService = services.vaultService
        // Create new external ID and two keys mapped to it.
        val id = UUID.randomUUID()
        val keyOne = services.keyManagementService.freshKeyAndCert(myself.identity, false, id)
        val keyTwo = services.keyManagementService.freshKeyAndCert(myself.identity, false, id)
        // Create states with a public key assigned to the new external ID.
        val dummyStateOne = createDummyState(listOf(AnonymousParty(keyOne.owningKey)))
        val dummyStateTwo = createDummyState(listOf(AnonymousParty(keyTwo.owningKey)))
        // This query should return two states!
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
        val vaultService = services.vaultService
        // Create new external ID.
        val idOne = UUID.randomUUID()
        val keyOne = services.keyManagementService.freshKeyAndCert(myself.identity, false, idOne)
        val idTwo = UUID.randomUUID()
        val keyTwo = services.keyManagementService.freshKeyAndCert(myself.identity, false, idTwo)
        // Create state with a public key assigned to the new external ID.
        val dummyState = createDummyState(listOf(AnonymousParty(keyOne.owningKey), AnonymousParty(keyTwo.owningKey)))
        // This query should return one state!
        val result = database.transaction {
            val externalId = builder { VaultSchemaV1.StateToExternalId::externalId.`in`(listOf(idOne, idTwo)) }
            val queryCriteria = QueryCriteria.VaultCustomQueryCriteria(externalId)
            vaultService.queryBy<DummyState>(queryCriteria).states
        }
        assertEquals(dummyState, result.single().state.data)
    }

}