package net.corda.node.services.vault

import net.corda.core.crypto.Crypto
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.TransactionBuilder
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyState
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith

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

    private val alice = TestIdentity.fresh("ALICE")
    private val bob = TestIdentity.fresh("BOB")

    lateinit var services: MockServices
    lateinit var database: CordaPersistence

    @Before
    fun setUp() {
        val (db, mockServices) = MockServices.makeTestDatabaseAndPersistentServices(
                cordappPackages = cordapps,
                initialIdentity = myself,
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4),
                moreIdentities = setOf(notary.identity, alice.identity, bob.identity),
                moreKeys = emptySet()
        )
        services = mockServices
        database = db
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
            vaultService.queryBy<DummyState>(QueryCriteria.VaultQueryCriteria(externalIds = listOf(id))).states
        }
        assertEquals(setOf(dummyStateOne, dummyStateTwo), result.map { it.state.data }.toSet())

        // This query should return two states!
        val resultTwo = database.transaction {
            vaultService.queryBy<DummyState>(QueryCriteria.VaultQueryCriteria(externalIds = listOf(id))).states
        }
        assertEquals(setOf(dummyStateOne, dummyStateTwo), resultTwo.map { it.state.data }.toSet())
    }

    @Test
    fun `externalIds query criteria test`() {
        val vaultService = services.vaultService

        // Create new external ID and two keys mapped to it.
        val id = UUID.randomUUID()
        val idTwo = UUID.randomUUID()
        val keyOne = services.keyManagementService.freshKeyAndCert(myself.identity, false, id)
        val keyTwo = services.keyManagementService.freshKeyAndCert(myself.identity, false, id)
        val keyThree = services.keyManagementService.freshKeyAndCert(myself.identity, false, idTwo)

        // Create states with a public key assigned to the new external ID.
        val dummyStateOne = createDummyState(listOf(AnonymousParty(keyOne.owningKey)))
        val dummyStateTwo = createDummyState(listOf(AnonymousParty(keyTwo.owningKey)))
        val dummyStateThree = createDummyState(listOf(AnonymousParty(keyThree.owningKey)))

        // This query should return two states!
        val result = database.transaction {
            vaultService.queryBy<DummyState>(QueryCriteria.VaultQueryCriteria(externalIds = listOf(id))).states
        }
        assertEquals(setOf(dummyStateOne, dummyStateTwo), result.map { it.state.data }.toSet())

        // Should return nothing.
        val resultTwo = database.transaction {
            vaultService.queryBy<DummyState>(QueryCriteria.VaultQueryCriteria(externalIds = listOf(UUID.randomUUID()))).states
        }
        assertEquals(emptyList(), resultTwo)

        // Should return one state.
        val resultThree = database.transaction {
            vaultService.queryBy<DummyState>(QueryCriteria.VaultQueryCriteria(externalIds = listOf(idTwo))).states
        }
        assertEquals(setOf(dummyStateThree), resultThree.map { it.state.data }.toSet())

        // Should return all states.
        val resultFour = database.transaction {
            vaultService.queryBy<DummyState>(QueryCriteria.VaultQueryCriteria(externalIds = listOf())).states
        }
        assertEquals(setOf(dummyStateOne, dummyStateTwo, dummyStateThree), resultFour.map { it.state.data }.toSet())
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
            vaultService.queryBy<DummyState>(QueryCriteria.VaultQueryCriteria(externalIds = listOf(idOne, idTwo))).states
        }
        assertEquals(dummyState, result.single().state.data)
    }

    @Test
    fun `roger uber keys test`() {
        // IDs.
        val id = UUID.randomUUID()
        val idTwo = UUID.randomUUID()
        val idThree = UUID.randomUUID()
        val idFour = UUID.randomUUID()

        // Keys.
        val A = services.keyManagementService.freshKey(id)          // Automatically calls registerKeyToParty and registerKeyToExternalId
        val B = services.keyManagementService.freshKey(id)          // Automatically calls registerKeyToParty and registerKeyToExternalId
        val C = services.keyManagementService.freshKey(idTwo)       // Automatically calls registerKeyToParty and registerKeyToExternalId
        val D = services.keyManagementService.freshKey()            // Automatically calls registerKeyToParty and registerKeyToExternalId
        val E = Crypto.generateKeyPair().public
        val F = Crypto.generateKeyPair().public
        val G = Crypto.generateKeyPair().public

        // Check we can lookup the Party and external ID (if there is one).
        assertEquals(id, services.identityService.externalIdForPublicKey(A))
        assertEquals(id, services.identityService.externalIdForPublicKey(B))
        assertEquals(idTwo, services.identityService.externalIdForPublicKey(C))
        assertEquals(null, services.identityService.externalIdForPublicKey(D))
        assertEquals(myself.party, services.identityService.wellKnownPartyFromAnonymous(AnonymousParty(A)))
        assertEquals(myself.party, services.identityService.wellKnownPartyFromAnonymous(AnonymousParty(B)))
        assertEquals(myself.party, services.identityService.wellKnownPartyFromAnonymous(AnonymousParty(C)))
        assertEquals(myself.party, services.identityService.wellKnownPartyFromAnonymous(AnonymousParty(D)))

        // Register some keys generated on another node.
        services.identityService.registerKey(E, alice.party, idThree)
        services.identityService.registerKey(F, alice.party, idFour)
        services.identityService.registerKey(G, bob.party)

        // Try to override existing mappings.
        services.identityService.registerKey(A, myself.party)                       // Idempotent call.
        assertFailsWith<IllegalStateException> { services.identityService.registerKey(A, alice.party) }
        services.identityService.registerKey(B, myself.party, UUID.randomUUID())    // Idempotent call.
        assertFailsWith<IllegalStateException> { services.identityService.registerKey(B, alice.party) }
        assertFailsWith<IllegalStateException> { services.identityService.registerKey(C, bob.party, UUID.randomUUID()) }

        // Check the above calls didn't change anything.
        assertEquals(myself.party, services.identityService.wellKnownPartyFromAnonymous(AnonymousParty(A)))
        assertEquals(myself.party, services.identityService.wellKnownPartyFromAnonymous(AnonymousParty(B)))
        assertEquals(myself.party, services.identityService.wellKnownPartyFromAnonymous(AnonymousParty(C)))

        assertEquals(id, services.identityService.externalIdForPublicKey(A))
        assertEquals(id, services.identityService.externalIdForPublicKey(B))
        assertEquals(idTwo, services.identityService.externalIdForPublicKey(C))
        assertEquals(idThree, services.identityService.externalIdForPublicKey(E))
        assertEquals(idFour, services.identityService.externalIdForPublicKey(F))

        // ALICE and BOB PartyAndCertificates need to be present in the nameToCert table.
        assertEquals(alice.party, services.identityService.wellKnownPartyFromAnonymous(AnonymousParty(E)))
        assertEquals(alice.party, services.identityService.wellKnownPartyFromAnonymous(AnonymousParty(F)))
        assertEquals(bob.party, services.identityService.wellKnownPartyFromAnonymous(AnonymousParty(G)))

        // Check we can look up keys by external ID.
        assertEquals(setOf(A, B), services.identityService.publicKeysForExternalId(id).toSet())
        assertEquals(C, services.identityService.publicKeysForExternalId(idTwo).single())
        assertEquals(E, services.identityService.publicKeysForExternalId(idThree).single())
        assertEquals(F, services.identityService.publicKeysForExternalId(idFour).single())
        assertEquals(emptyList(), services.identityService.publicKeysForExternalId(UUID.randomUUID()))
    }

}