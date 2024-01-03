package net.corda.node.services.schema

import net.corda.core.contracts.AlwaysAcceptAttachmentConstraint
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.coretesting.internal.rigorousMock
import net.corda.node.services.api.SchemaService
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.nodeapi.internal.persistence.currentDBSession
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.TestIdentity
import net.corda.testing.internal.LogHelper
import net.corda.testing.internal.configureDatabase
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import org.hibernate.annotations.Cascade
import org.hibernate.annotations.CascadeType
import org.junit.After
import org.junit.Before
import org.junit.Test
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.JoinColumns
import javax.persistence.ManyToOne
import javax.persistence.OneToMany
import javax.persistence.OrderColumn
import javax.persistence.Table
import kotlin.test.assertEquals

class PersistentStateServiceTests {
    @Before
    fun setUp() {
        LogHelper.setLevel(PersistentStateService::class)
    }

    @After
    fun cleanUp() {
        LogHelper.reset(PersistentStateService::class)
    }

    class TestState : QueryableState {
        override fun supportedSchemas(): Iterable<MappedSchema> {
            throw UnsupportedOperationException()
        }

        override fun generateMappedObject(schema: MappedSchema): PersistentState {
            throw UnsupportedOperationException()
        }

        override val participants: List<AbstractParty>
            get() = throw UnsupportedOperationException()
    }

    @Test(timeout=300_000)
	fun `test child objects are persisted`() {
        val testSchema = TestSchema
        val schemaService = object : SchemaService {
            override val schemas: Set<MappedSchema> = setOf(testSchema)

            override fun selectSchemas(state: ContractState): Iterable<MappedSchema> = setOf(testSchema)

            override fun generateMappedObject(state: ContractState, schema: MappedSchema): PersistentState {
                val parent = TestSchema.Parent()
                parent.children.add(TestSchema.Child())
                parent.children.add(TestSchema.Child())
                return parent
            }
        }
        val database = configureDatabase(makeTestDataSourceProperties(), DatabaseConfig(), rigorousMock(), rigorousMock(), schemaService)
        val persistentStateService = PersistentStateService(schemaService)
        database.transaction {
            val MEGA_CORP = TestIdentity(CordaX500Name("MegaCorp", "London", "GB")).party
            persistentStateService.persist(setOf(StateAndRef(TransactionState(TestState(), DummyContract.PROGRAM_ID, MEGA_CORP, constraint = AlwaysAcceptAttachmentConstraint), StateRef(SecureHash.sha256("dummy"), 0))))
            currentDBSession().flush()
            val parentRowCountResult = connection.prepareStatement("select count(*) from Parents").executeQuery()
            parentRowCountResult.next()
            val parentRows = parentRowCountResult.getInt(1)
            parentRowCountResult.close()
            val childrenRowCountResult = connection.prepareStatement("select count(*) from Children").executeQuery()
            childrenRowCountResult.next()
            val childrenRows = childrenRowCountResult.getInt(1)
            childrenRowCountResult.close()
            assertEquals(1, parentRows, "Expected one parent")
            assertEquals(2, childrenRows, "Expected two children")
        }

        database.close()
    }

    class SchemaFamily

    object TestSchema : MappedSchema(SchemaFamily::class.java, 1, setOf(Parent::class.java, Child::class.java)) {
        @Entity
        @Table(name = "Parents")
        class Parent : PersistentState() {
            @OneToMany(fetch = FetchType.LAZY)
            @JoinColumns(JoinColumn(name = "transaction_id", referencedColumnName = "transaction_id"), JoinColumn(name = "output_index", referencedColumnName = "output_index"))
            @OrderColumn
            @Cascade(CascadeType.PERSIST)
            var children: MutableSet<Child> = mutableSetOf()
        }

        @Suppress("unused")
        @Entity
        @Table(name = "Children")
        class Child {
            @Id
            @GeneratedValue
            @Column(name = "child_id", unique = true, nullable = false)
            var childId: Int? = null

            @ManyToOne(fetch = FetchType.LAZY)
            @JoinColumns(JoinColumn(name = "transaction_id", referencedColumnName = "transaction_id"), JoinColumn(name = "output_index", referencedColumnName = "output_index"))
            var parent: Parent? = null
        }
    }
}
