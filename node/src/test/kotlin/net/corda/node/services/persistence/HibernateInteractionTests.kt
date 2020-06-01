package net.corda.node.services.persistence

import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.TransactionState
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.NodeInfo
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.OpaqueBytes
import net.corda.finance.DOLLARS
import net.corda.finance.`issued by`
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.issuedBy
import net.corda.node.services.network.PersistentNetworkMapCache
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.internal.TestingNamedCacheFactory
import net.corda.testing.node.MockServices
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.annotations.Cascade
import org.hibernate.annotations.CascadeType
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.lang.IllegalArgumentException
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.OneToMany
import javax.persistence.Table
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import kotlin.test.assertEquals

/**
 * These tests cover the interactions between Corda and Hibernate with regards to flushing/detaching/cascading.
 */
class HibernateInteractionTests {

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private val cordapps = listOf("net.corda.finance", "net.corda.node.services.persistence")

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
                moreKeys = emptySet(),
                // forcing a cache size of zero, so that all requests lead to a cache miss and end up hitting the database
                cacheFactory = TestingNamedCacheFactory(0)
        )
        services = mockServices
        database = db
        // Store NodeInfo in database for identityService lookups
        val networkMapCache = PersistentNetworkMapCache(TestingNamedCacheFactory(0), database, mockServices.identityService)
        listOf(myself.identity, notary.identity).forEach {
            networkMapCache.addOrUpdateNode(NodeInfo(listOf(NetworkHostAndPort("localhost", 12345)), listOf(it), 1, 0))
        }
    }

    // AbstractPartyToX500NameAsStringConverter could cause circular flush of Hibernate session because it is invoked during flush, and a
    // cache miss was doing a flush.  This also checks that loading during flush does actually work.
    @Test(timeout=300_000)
	fun `issue some cash on a notary that exists only in the database to check cache loading works in our identity column converters during flush of vault update`() {
        val expected = 500.DOLLARS
        val ref = OpaqueBytes.of(0x01)

        val ourIdentity = services.myInfo.legalIdentities.first()
        val builder = TransactionBuilder(notary.party)
        val issuer = services.myInfo.legalIdentities.first().ref(ref)
        val signers = Cash().generateIssue(builder, expected.issuedBy(issuer), ourIdentity, notary.party)
        val tx: SignedTransaction = services.signInitialTransaction(builder, signers)
        services.recordTransactions(tx)

        val output = tx.tx.outputsOfType<Cash.State>().single()
        assertEquals(expected.`issued by`(ourIdentity.ref(ref)), output.amount)
    }

    @Test(timeout=300_000)
	fun `when a cascade is in progress (because of nested entities), the node avoids to flush & detach entities, since it's not allowed by Hibernate`() {
        val ourIdentity = services.myInfo.legalIdentities.first()

        val childEntities = listOf(SimpleContract.ChildState(ourIdentity))
        val parentEntity = SimpleContract.ParentState(childEntities)

        val builder = TransactionBuilder(notary.party)
                .addOutputState(TransactionState(parentEntity, SimpleContract::class.java.name, notary.party))
                .addCommand(SimpleContract.Issue(), listOf(ourIdentity.owningKey))
        val tx: SignedTransaction = services.signInitialTransaction(builder, listOf(ourIdentity.owningKey))
        services.recordTransactions(tx)

        val output = tx.tx.outputsOfType<SimpleContract.ParentState>().single()
        assertThat(output.children.single().member).isEqualTo(ourIdentity)
    }

    object PersistenceSchema: MappedSchema(PersistenceSchema::class.java, 1, listOf(Parent::class.java, Child::class.java)) {

        override val migrationResource: String?
            get() = "hibernate-interactions-tests-schema"

        @Entity(name = "parentstates")
        @Table
        class Parent: PersistentState() {

            @Cascade(CascadeType.ALL)
            @OneToMany(targetEntity = Child::class)
            val children: MutableCollection<Child> = mutableSetOf()

            fun addChild(child: Child) {
                children.add(child)
            }
        }

        @Entity(name = "childstates")
        class Child(
                @Id
                // Do not change this: this generation type is required in order to trigger the proper cascade ordering.
                @GeneratedValue(strategy = GenerationType.IDENTITY)
                val identifier: Int?,

                val member: AbstractParty?
        ) {
            constructor(member: AbstractParty): this(null, member)
        }

    }

    class SimpleContract: Contract {

        @BelongsToContract(SimpleContract::class)
        @CordaSerializable
        data class ParentState(val children: List<ChildState>): ContractState, QueryableState {
            override fun supportedSchemas(): Iterable<MappedSchema> = listOf(PersistenceSchema)

            override fun generateMappedObject(schema: MappedSchema): PersistentState {
                return when(schema) {
                    is PersistenceSchema -> {
                        val parent = PersistenceSchema.Parent()
                        children.forEach { parent.addChild(PersistenceSchema.Child(it.member)) }
                        parent
                    }
                    else -> throw IllegalArgumentException("Unrecognised schema $schema")
                }
            }

            override val participants: List<AbstractParty> = children.map { it.member }
        }

        @CordaSerializable
        data class ChildState(val member: AbstractParty)

        override fun verify(tx: LedgerTransaction) {}

        class Issue: TypeOnlyCommandData()
    }

}