package net.corda.node.services.schema

import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty
import net.corda.core.node.services.Vault
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.testing.LogHelper
import net.corda.node.services.api.SchemaService
import net.corda.node.services.database.HibernateConfiguration
import net.corda.node.utilities.CordaPersistence
import net.corda.node.utilities.configureDatabase
import net.corda.testing.MEGA_CORP
import net.corda.testing.node.makeTestDataSourceProperties
import org.hibernate.annotations.Cascade
import org.hibernate.annotations.CascadeType
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.junit.After
import org.junit.Before
import org.junit.Test
import rx.subjects.PublishSubject
import javax.persistence.*
import kotlin.test.assertEquals


class HibernateObserverTests {
    lateinit var database: CordaPersistence

    @Before
    fun setUp() {
        LogHelper.setLevel(HibernateObserver::class)
        database = configureDatabase(makeTestDataSourceProperties())
    }

    @After
    fun cleanUp() {
        database.close()
        LogHelper.reset(HibernateObserver::class)
    }

    class SchemaFamily

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
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(name = "child_id", unique = true, nullable = false)
        var childId: Int? = null

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(JoinColumn(name = "transaction_id", referencedColumnName = "transaction_id"), JoinColumn(name = "output_index", referencedColumnName = "output_index"))
        var parent: Parent? = null
    }

    class TestState : QueryableState {
        override fun supportedSchemas(): Iterable<MappedSchema> {
            throw UnsupportedOperationException()
        }

        override fun generateMappedObject(schema: MappedSchema): PersistentState {
            throw UnsupportedOperationException()
        }

        override val contract: Contract
            get() = throw UnsupportedOperationException()

        override val participants: List<AbstractParty>
            get() = throw UnsupportedOperationException()
    }

    // This method does not use back quotes for a nice name since it seems to kill the kotlin compiler.
    @Test
    fun testChildObjectsArePersisted() {
        val testSchema = object : MappedSchema(SchemaFamily::class.java, 1, setOf(Parent::class.java, Child::class.java)) {}
        val rawUpdatesPublisher = PublishSubject.create<Vault.Update>()
        val schemaService = object : SchemaService {
            override val schemaOptions: Map<MappedSchema, SchemaService.SchemaOptions> = emptyMap()

            override fun selectSchemas(state: ContractState): Iterable<MappedSchema> = setOf(testSchema)

            override fun generateMappedObject(state: ContractState, schema: MappedSchema): PersistentState {
                val parent = Parent()
                parent.children.add(Child())
                parent.children.add(Child())
                return parent
            }
        }

        @Suppress("UNUSED_VARIABLE")
        val observer = HibernateObserver(rawUpdatesPublisher, HibernateConfiguration(schemaService))
        database.transaction {
            rawUpdatesPublisher.onNext(Vault.Update(emptySet(), setOf(StateAndRef(TransactionState(TestState(), MEGA_CORP), StateRef(SecureHash.sha256("dummy"), 0)))))
            val parentRowCountResult = TransactionManager.current().connection.prepareStatement("select count(*) from Parents").executeQuery()
            parentRowCountResult.next()
            val parentRows = parentRowCountResult.getInt(1)
            parentRowCountResult.close()
            val childrenRowCountResult = TransactionManager.current().connection.prepareStatement("select count(*) from Children").executeQuery()
            childrenRowCountResult.next()
            val childrenRows = childrenRowCountResult.getInt(1)
            childrenRowCountResult.close()
            assertEquals(1, parentRows, "Expected one parent")
            assertEquals(2, childrenRows, "Expected two children")
        }
    }
}