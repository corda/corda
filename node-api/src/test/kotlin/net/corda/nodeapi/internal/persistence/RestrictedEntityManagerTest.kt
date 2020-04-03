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
import javax.persistence.Table
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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
    fun `getTransaction returns a restricted transaction`() {
        database.transaction {
            restrictedEntityManager = RestrictedEntityManager(entityManager)
            assertTrue(restrictedEntityManager.transaction is RestrictedEntityTransaction)
        }
    }

    @Test(timeout = 300_000)
    fun `cannot call commit`() {
        database.transaction {
            restrictedEntityManager = RestrictedEntityManager(entityManager)
            assertFailsWith<UnsupportedOperationException> {
                restrictedEntityManager.transaction.commit()
            }
        }
    }

    @Test(timeout = 300_000)
    fun `cannot call rollback`() {
        database.transaction {
            restrictedEntityManager = RestrictedEntityManager(entityManager)
            assertFailsWith<UnsupportedOperationException> {
                restrictedEntityManager.transaction.rollback()
            }
        }
    }

    @Test(timeout = 300_000)
    fun `cannot call begin`() {
        database.transaction {
            restrictedEntityManager = RestrictedEntityManager(entityManager)
            assertFailsWith<UnsupportedOperationException> {
                restrictedEntityManager.transaction.begin()
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
    fun `can call find on a session marked for rollback`() {
        database.transaction {
            val manager = entityManager
            restrictedEntityManager = RestrictedEntityManager(manager)
            manager.persist(entity)
            manager.transaction.setRollbackOnly()
            assertEquals(entity, restrictedEntityManager.find(entity::class.java, entity.id))
        }
        database.transaction {
            val manager = entityManager
            restrictedEntityManager = RestrictedEntityManager(manager)
            assertEquals(null, restrictedEntityManager.find(entity::class.java, entity.id))
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