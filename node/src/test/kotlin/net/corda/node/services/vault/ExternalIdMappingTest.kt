package net.corda.node.services.vault

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.transactions.TransactionBuilder
import net.corda.node.services.api.IdentityServiceInternal
import net.corda.node.services.keys.PersistentKeyManagementService
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyState
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.internal.rigorousMock
import net.corda.testing.node.MockServices
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

    private val myself = TestIdentity(CordaX500Name("Me", "London", "GB"))
    private val notary = TestIdentity(CordaX500Name("NotaryService", "London", "GB"), 1337L)
    private val databaseAndServices = MockServices.makeTestDatabaseAndMockServices(
            cordappPackages = cordapps,
            identityService = rigorousMock<IdentityServiceInternal>().also {
                doReturn(notary.party).whenever(it).partyFromKey(notary.publicKey)
                doReturn(notary.party).whenever(it).wellKnownPartyFromAnonymous(notary.party)
                doReturn(notary.party).whenever(it).wellKnownPartyFromX500Name(notary.name)
            },
            initialIdentity = myself,
            networkParameters = testNetworkParameters(minimumPlatformVersion = 4)
    )

    private val services: MockServices = databaseAndServices.second
    private val database: CordaPersistence = databaseAndServices.first

    private fun freshKeyForExternalId(externalId: UUID): AnonymousParty {
        val anonymousParty = freshKey()
        database.transaction {
            services.withEntityManager {
                val mapping = PersistentKeyManagementService.PublicKeyHashToExternalId(externalId, anonymousParty.owningKey)
                persist(mapping)
                flush()
            }
        }
        return anonymousParty
    }

    private fun freshKey(): AnonymousParty {
        val key = services.keyManagementService.freshKey()
        val anonymousParty = AnonymousParty(key)
        // Add behaviour to the mock identity management service for dealing with the new key.
        // It won't be able to resolve it as it's just an anonymous key that is not linked to an identity.
        services.identityService.also { doReturn(null).whenever(it).wellKnownPartyFromAnonymous(anonymousParty) }
        return anonymousParty
    }

    private fun createDummyState(participant: AbstractParty): DummyState {
        val tx = TransactionBuilder(notary = notary.party).apply {
            addOutputState(DummyState(1, listOf(participant)), DummyContract.PROGRAM_ID)
            addCommand(DummyContract.Commands.Create(), listOf(participant.owningKey))
        }
        val stx = services.signInitialTransaction(tx)
        database.transaction { services.recordTransactions(stx) }
        return stx.tx.outputsOfType<DummyState>().single()
    }

    @Test
    fun `states can be queried by external ID`() {
        val vaultService = services.vaultService
        // Create new external ID.
        val newExternalId = UUID.randomUUID()
        // Create key for account.
        val anonymousMe = freshKeyForExternalId(newExternalId)
        // Create state with a public key assigned to the new external ID.
        val dummyStateForExternalId = createDummyState(anonymousMe)
        // Create a state with a public key not assigned to the new external ID.
        val anonymousMeTwo = freshKey()
        createDummyState(anonymousMeTwo)
        // Create another state linked to the external ID via the public key.
        val anonymousMeThree = freshKeyForExternalId(newExternalId)
        val dummyStateForExternalIdTwo = createDummyState(anonymousMeThree)
        // This query should return two states!
        database.transaction {
            services.withEntityManager {
                val builder = criteriaBuilder.createQuery(VaultSchemaV1.ExtIdToPubKeyView::class.java)
                builder.from(VaultSchemaV1.ExtIdToPubKeyView::class.java)
                val result = createQuery(builder).resultList.forEach {
                    println("${it.externalId} ${it.publicKeyHash}")
                }

            }
        }
        val result = database.transaction {
            val externalId = builder { VaultSchemaV1.ExtIdToPubKeyView::externalId.equal(newExternalId) }
            val queryCriteria = QueryCriteria.VaultCustomQueryCriteria(externalId)
            vaultService.queryBy<DummyState>(queryCriteria).states
        }
        assertEquals(setOf(dummyStateForExternalId, dummyStateForExternalIdTwo), result.map { it.state.data }.toSet())
    }

}