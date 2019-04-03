package net.corda.node.services.persistence

import net.corda.core.schemas.MappedSchema
import net.corda.node.services.schema.NodeSchemaService
import net.corda.node.utilities.AppendOnlyPersistentMap
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.internal.TestingNamedCacheFactory
import net.corda.testing.internal.configureDatabase
import net.corda.testing.node.MockServices
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id

class AppendOnlyPersistentMapNonConcurrentTest {

    private val database = configureDatabase(MockServices.makeTestDataSourceProperties(),
            DatabaseConfig(),
            { null }, { null },
            NodeSchemaService(setOf(MappedSchema(AppendOnlyPersistentMapTest::class.java, 1, listOf(AppendOnlyPersistentMapNonConcurrentTest.PersistentMapEntry::class.java)))))

    @Entity
    @javax.persistence.Table(name = "persist_map_test")
    class PersistentMapEntry(
            @Id
            @Column(name = "key")
            var key: Long = -1,

            @Column(name = "value", length = 16)
            var value: String = ""
    )

    class TestMap(cacheSize: Long) : AppendOnlyPersistentMap<Long, String, PersistentMapEntry, Long>(
            cacheFactory = TestingNamedCacheFactory(cacheSize),
            name = "ApoendOnlyPersistentMap_test",
            toPersistentEntityKey = { it },
            fromPersistentEntity = { Pair(it.key, it.value) },
            toPersistentEntity = { key: Long, value: String ->
                PersistentMapEntry().apply {
                    this.key = key
                    this.value = value
                }
            },
            persistentEntityClass = PersistentMapEntry::class.java
    )

    private fun createMap(cacheSize: Long) = TestMap(cacheSize)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `map prevents duplicates, when key has been evicted from cache, but present in database`() {
        val map = createMap(1)

        database.transaction {
            map.addWithDuplicatesAllowed(1, "1")
            map.addWithDuplicatesAllowed(3, "3")
        }

        database.transaction {
            map.addWithDuplicatesAllowed(1, "2")
        }

        val result = database.transaction {
            map[1]
        }

        assertThat(result).isEqualTo("1")
    }

}