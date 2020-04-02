package net.corda.nodeapi.internal.persistence

import net.corda.core.schemas.MappedSchema
import net.corda.core.serialization.CordaSerializable
import net.corda.node.services.schema.NodeSchemaService
import net.corda.testing.internal.configureDatabase
import net.corda.testing.node.MockServices
import org.hibernate.Session
import org.junit.Test
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.LockModeType
import javax.persistence.Table
import kotlin.test.assertFailsWith

class RestrictedEntityManagerTest {
    private val database = configureDatabase(
        hikariProperties = MockServices.makeTestDataSourceProperties(),
        databaseConfig = DatabaseConfig(),
        wellKnownPartyFromX500Name = { null },
        wellKnownPartyFromAnonymous = { null },
        schemaService = NodeSchemaService(setOf(CustomMappedSchema))
    )

    private val entity = CustomTableEntity(1, "Boris Johnson", "Here is a picture of my zoom meeting id")
    private lateinit var restrictedEntityManager: RestrictedEntityManager

    @Test(timeout = 300_000)
    fun `can call clear`() {
        database.transaction {
            restrictedEntityManager = RestrictedEntityManager(entityManager)
            restrictedEntityManager.clear()
        }
    }

    @Test(timeout = 300_000)
    fun `can call detatch`() {
        database.transaction {
            restrictedEntityManager = RestrictedEntityManager(entityManager)
            restrictedEntityManager.persist(entity)
            restrictedEntityManager.detach(entity)
        }
    }

    @Test(timeout = 300_000)
    fun `cannot call close`() {
        database.transaction {
            restrictedEntityManager = RestrictedEntityManager(entityManager)
            assertFailsWith<UnsupportedOperationException> {
                restrictedEntityManager.close()
            }
        }
    }

    @Test(timeout = 300_000)
    fun `cannot call getTransaction`() {
        database.transaction {
            restrictedEntityManager = RestrictedEntityManager(entityManager)
            assertFailsWith<UnsupportedOperationException> {
                restrictedEntityManager.transaction
            }
        }
    }

    @Test(timeout = 300_000)
    fun `cannot call unwrap`() {
        database.transaction {
            restrictedEntityManager = RestrictedEntityManager(entityManager)
            assertFailsWith<UnsupportedOperationException> {
                restrictedEntityManager.unwrap(Session::class.java)
            }
        }
    }

    @Test(timeout = 300_000)
    fun `cannot call getDelete`() {
        database.transaction {
            restrictedEntityManager = RestrictedEntityManager(entityManager)
            assertFailsWith<UnsupportedOperationException> {
                restrictedEntityManager.delegate
            }
        }
    }

    @Test(timeout = 300_000)
    fun `cannot call setProperty`() {
        database.transaction {
            restrictedEntityManager = RestrictedEntityManager(entityManager)
            assertFailsWith<UnsupportedOperationException> {
                restrictedEntityManager.setProperty("key", "value")
            }
        }
    }

    @Test(timeout = 300_000)
    fun `can call persist on a normal session`() {
        database.transaction {
            restrictedEntityManager = RestrictedEntityManager(entityManager)
            restrictedEntityManager.persist(entity)
        }
    }

    @Test(timeout = 300_000)
    fun `cannot call persist on a session marked for rolled back`() {
        database.transaction {
            val manager = entityManager
            restrictedEntityManager = RestrictedEntityManager(manager)
            assertFailsWith<RolledBackDatabaseSessionException> {
                manager.transaction.setRollbackOnly()
                restrictedEntityManager.persist(entity)
            }
        }
    }

    @Test(timeout = 300_000)
    fun `cannot call merge on a session marked for rolled back`() {
        database.transaction {
            val manager = entityManager
            restrictedEntityManager = RestrictedEntityManager(manager)
            assertFailsWith<RolledBackDatabaseSessionException> {
                manager.transaction.setRollbackOnly()
                restrictedEntityManager.merge(entity)
            }
        }
    }

    @Test(timeout = 300_000)
    fun `cannot call remove on asession marked for rolled back`() {
        database.transaction {
            val manager = entityManager
            restrictedEntityManager = RestrictedEntityManager(manager)
            assertFailsWith<RolledBackDatabaseSessionException> {
                manager.transaction.setRollbackOnly()
                restrictedEntityManager.remove(entity)
            }
        }
    }

    @Test(timeout = 300_000)
    fun `cannot call find on a session marked for rollback`() {
        database.transaction {
            val manager = entityManager
            restrictedEntityManager = RestrictedEntityManager(manager)
            assertFailsWith<RolledBackDatabaseSessionException> {
                manager.transaction.setRollbackOnly()
                restrictedEntityManager.find(entity::class.java, entity.id)
            }
        }
    }

    @Test(timeout = 300_000)
    fun `cannot call refresh on a session marked for rollback`() {
        database.transaction {
            val manager = entityManager
            restrictedEntityManager = RestrictedEntityManager(manager)
            assertFailsWith<RolledBackDatabaseSessionException> {
                manager.transaction.setRollbackOnly()
                restrictedEntityManager.refresh(entity)
            }
        }
    }

    @Test(timeout = 300_000)
    fun `cannot call lock on a session marked for rollback`() {
        database.transaction {
            val manager = entityManager
            restrictedEntityManager = RestrictedEntityManager(manager)
            assertFailsWith<RolledBackDatabaseSessionException> {
                manager.transaction.setRollbackOnly()
                restrictedEntityManager.lock(entity, LockModeType.OPTIMISTIC)
            }
        }
    }

    @Entity
    @Table(name = "custom_table")
    @CordaSerializable
    data class CustomTableEntity constructor(
        @Id
        @Column(name = "id", nullable = false)
        var id: Int,
        @Column(name = "name", nullable = false)
        var name: String,
        @Column(name = "quote", nullable = false)
        var quote: String
    )

    object CustomSchema

    object CustomMappedSchema : MappedSchema(CustomSchema::class.java, 1, listOf(CustomTableEntity::class.java))
}