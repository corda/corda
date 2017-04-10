package net.corda.node.services

import net.corda.core.contracts.*
import net.corda.core.crypto.AbstractParty
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Party
import net.corda.core.crypto.SecureHash
import net.corda.core.node.services.Vault
import net.corda.core.node.services.VaultService
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.LogHelper
import net.corda.node.services.api.SchemaService
import net.corda.node.services.schema.HibernateObserver
import net.corda.node.utilities.configureDatabase
import net.corda.node.utilities.databaseTransaction
import net.corda.testing.MEGA_CORP
import net.corda.testing.node.makeTestDataSourceProperties
import org.hibernate.annotations.Cascade
import org.hibernate.annotations.CascadeType
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.junit.After
import org.junit.Before
import org.junit.Test
import rx.Observable
import rx.subjects.PublishSubject
import java.io.Closeable
import java.util.*
import javax.persistence.*
import kotlin.test.assertEquals


class HibernateObserverTests {
    lateinit var dataSource: Closeable
    lateinit var database: Database

    @Before
    fun setUp() {
        LogHelper.setLevel(HibernateObserver::class)
        val dataSourceAndDatabase = configureDatabase(makeTestDataSourceProperties())
        dataSource = dataSourceAndDatabase.first
        database = dataSourceAndDatabase.second
    }

    @After
    fun cleanUp() {
        dataSource.close()
        LogHelper.reset(HibernateObserver::class)
    }

    class SchemaFamily

    @Entity
    @Table(name = "Parents")
    class Parent : PersistentState() {
        @OneToMany(fetch = FetchType.LAZY)
        @JoinColumns(JoinColumn(name = "transaction_id"), JoinColumn(name = "output_index"))
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
        @JoinColumns(JoinColumn(name = "transaction_id"), JoinColumn(name = "output_index"))
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

        override val participants: List<CompositeKey>
            get() = throw UnsupportedOperationException()
    }

    // This method does not use back quotes for a nice name since it seems to kill the kotlin compiler.
    @Test
    fun testChildObjectsArePersisted() {
        val testSchema = object : MappedSchema(SchemaFamily::class.java, 1, setOf(Parent::class.java, Child::class.java)) {}
        val rawUpdatesPublisher = PublishSubject.create<Vault.Update>()
        val vaultService = object : VaultService {
            override val rawUpdates: Observable<Vault.Update> = rawUpdatesPublisher

            override val updates: Observable<Vault.Update>
                get() = throw UnsupportedOperationException()

            override val cashBalances: Map<Currency, Amount<Currency>>
                get() = throw UnsupportedOperationException()

            override fun track(): Pair<Vault<ContractState>, Observable<Vault.Update>> {
                throw UnsupportedOperationException()
            }

            override fun statesForRefs(refs: List<StateRef>): Map<StateRef, TransactionState<*>?> {
                throw UnsupportedOperationException()
            }

            override fun notifyAll(txns: Iterable<WireTransaction>) {
                throw UnsupportedOperationException()
            }

            override fun getAuthorisedContractUpgrade(ref: StateRef): Class<out UpgradedContract<*, *>>? {
                throw UnsupportedOperationException()
            }

            override fun authoriseContractUpgrade(stateAndRef: StateAndRef<*>, upgradedContractClass: Class<out UpgradedContract<*, *>>) {
                throw UnsupportedOperationException()
            }

            override fun deauthoriseContractUpgrade(stateAndRef: StateAndRef<*>) {
                throw UnsupportedOperationException()
            }

            override fun addNoteToTransaction(txnId: SecureHash, noteText: String) {
                throw UnsupportedOperationException()
            }

            override fun getTransactionNotes(txnId: SecureHash): Iterable<String> {
                throw UnsupportedOperationException()
            }

            override fun generateSpend(tx: TransactionBuilder, amount: Amount<Currency>, to: CompositeKey, onlyFromParties: Set<AbstractParty>?): Pair<TransactionBuilder, List<CompositeKey>> {
                throw UnsupportedOperationException()
            }

            override fun <T : ContractState> states(clazzes: Set<Class<T>>, statuses: EnumSet<Vault.StateStatus>, includeSoftLockedStates: Boolean): Iterable<StateAndRef<T>> {
                throw UnsupportedOperationException()
            }

            override fun softLockReserve(lockId: UUID, stateRefs: Set<StateRef>) {
                throw UnsupportedOperationException()
            }

            override fun softLockRelease(lockId: UUID, stateRefs: Set<StateRef>?) {
                throw UnsupportedOperationException()
            }

            override fun <T : ContractState> softLockedStates(lockId: UUID?): List<StateAndRef<T>> {
                throw UnsupportedOperationException()
            }

            override fun <T : ContractState> unconsumedStatesForSpending(amount: Amount<Currency>, onlyFromIssuerParties: Set<AbstractParty>?, notary: Party?, lockId: UUID, withIssuerRefs: Set<OpaqueBytes>?): List<StateAndRef<T>> {
                throw UnsupportedOperationException()
            }
        }
        val schemaService = object : SchemaService {
            override val schemaOptions: Map<MappedSchema, SchemaService.SchemaOptions> = emptyMap()

            override fun selectSchemas(state: QueryableState): Iterable<MappedSchema> = setOf(testSchema)

            override fun generateMappedObject(state: QueryableState, schema: MappedSchema): PersistentState {
                val parent = Parent()
                parent.children.add(Child())
                parent.children.add(Child())
                return parent
            }
        }

        @Suppress("UNUSED_VARIABLE")
        val observer = HibernateObserver(vaultService, schemaService)
        databaseTransaction(database) {
            rawUpdatesPublisher.onNext(Vault.Update(emptySet(), setOf(StateAndRef(TransactionState(TestState(), MEGA_CORP), StateRef(SecureHash.sha256("dummy"), 0)))))
            val parentRowCountResult = TransactionManager.current().connection.prepareStatement("select count(*) from contract_Parents").executeQuery()
            parentRowCountResult.next()
            val parentRows = parentRowCountResult.getInt(1)
            parentRowCountResult.close()
            val childrenRowCountResult = TransactionManager.current().connection.prepareStatement("select count(*) from contract_Children").executeQuery()
            childrenRowCountResult.next()
            val childrenRows = childrenRowCountResult.getInt(1)
            childrenRowCountResult.close()
            assertEquals(1, parentRows, "Expected one parent")
            assertEquals(2, childrenRows, "Expected two children")
        }
    }
}