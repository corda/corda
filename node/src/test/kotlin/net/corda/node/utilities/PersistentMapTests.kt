package net.corda.node.utilities

import net.corda.core.crypto.SecureHash
import net.corda.node.internal.configureDatabase
import net.corda.node.services.upgrade.ContractUpgradeServiceImpl
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.node.MockServices
import org.junit.Test
import kotlin.test.assertEquals

class PersistentMapTests {
    private val databaseConfig = DatabaseConfig()
    private val database get() = configureDatabase(dataSourceProps, databaseConfig, { null }, { null })
    private val dataSourceProps = MockServices.makeTestDataSourceProperties()

    //create a test map using an existing db table
    private fun createTestMap(): PersistentMap<String, String, ContractUpgradeServiceImpl.DBContractUpgrade, String> {
        return PersistentMap(
                toPersistentEntityKey = { it },
                fromPersistentEntity = { Pair(it.stateRef, it.upgradedContractClassName ?: "") },
                toPersistentEntity = { key: String, value: String ->
                    ContractUpgradeServiceImpl.DBContractUpgrade().apply {
                        stateRef = key
                        upgradedContractClassName = value
                    }
                },
                persistentEntityClass = ContractUpgradeServiceImpl.DBContractUpgrade::class.java
        ).apply { preload() }
    }

    @Test
    fun `make sure persistence works`() {
        val testHash = SecureHash.randomSHA256().toString()

        database.transaction {
            val map = createTestMap()
            map[testHash] = "test"
            assertEquals(map[testHash], "test")
        }

        database.transaction {
            val reloadedMap = createTestMap()
            assertEquals("test", reloadedMap[testHash])
        }
    }

    @Test
    fun `make sure persistence works using assignment operator`() {
        val testHash = SecureHash.randomSHA256().toString()

        database.transaction {
            val map = createTestMap()
            map[testHash] = "test"
            assertEquals("test", map[testHash])
        }

        database.transaction {
            val reloadedMap = createTestMap()
            assertEquals("test", reloadedMap[testHash])
        }
    }

    @Test
    fun `make sure updating works`() {
        val testHash = SecureHash.randomSHA256().toString()

        database.transaction {
            val map = createTestMap()
            map[testHash] = "test"

            map[testHash] = "updated"
            assertEquals("updated", map[testHash])
        }

        database.transaction {
            val reloadedMap = createTestMap()
            assertEquals("updated", reloadedMap[testHash])
        }
    }

    @Test
    fun `make sure updating works using assignment operator`() {
        val testHash = SecureHash.randomSHA256().toString()

        database.transaction {
            val map = createTestMap()
            map[testHash] = "test"

            map[testHash] = "updated"
            assertEquals("updated", map[testHash])
        }

        database.transaction {
            val reloadedMap = createTestMap()
            assertEquals("updated", reloadedMap[testHash])
        }
    }

    @Test
    fun `make sure removal works`() {
        val testHash = SecureHash.randomSHA256().toString()

        database.transaction {
            val map = createTestMap()
            map[testHash] = "test"
        }

        database.transaction {
            val reloadedMap = createTestMap()
            //check that the item was persisted
            assertEquals("test", reloadedMap[testHash])

            reloadedMap.remove(testHash)
            //check that the item was removed in the version of the map
            assertEquals(null, reloadedMap[testHash])
        }

        database.transaction {
            val reloadedMap = createTestMap()
            //check that the item was removed from the persistent store
            assertEquals(null, reloadedMap[testHash])
        }
    }

    @Test
    fun `make sure persistence works against base class`() {
        val testHash = SecureHash.randomSHA256().toString()

        database.transaction {
            val map = createTestMap()
            map[testHash] = "test"
            assertEquals(map[testHash], "test")
        }

        database.transaction {
            val reloadedMap = createTestMap()
            assertEquals("test", reloadedMap[testHash])
        }
    }

    @Test
    fun `make sure persistence works using assignment operator base class`() {
        val testHash = SecureHash.randomSHA256().toString()

        database.transaction {
            val map = createTestMap() as MutableMap<String, String>
            map[testHash] = "test"
            assertEquals("test", map[testHash])
        }

        database.transaction {
            val reloadedMap = createTestMap() as MutableMap<String, String>
            assertEquals("test", reloadedMap[testHash])
        }
    }
}