package net.corda.node.utilities

import com.google.common.collect.testing.MapTestSuiteBuilder
import com.google.common.collect.testing.SetTestSuiteBuilder
import com.google.common.collect.testing.TestStringMapGenerator
import com.google.common.collect.testing.TestStringSetGenerator
import com.google.common.collect.testing.features.CollectionFeature
import com.google.common.collect.testing.features.CollectionSize
import com.google.common.collect.testing.features.MapFeature
import com.google.common.collect.testing.features.SetFeature
import com.google.common.collect.testing.testers.*
import junit.framework.TestSuite
import net.corda.testing.node.makeTestDataSourceProperties
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.junit.*
import org.junit.runner.RunWith
import org.junit.runners.Suite
import java.sql.Connection
import java.util.*

@RunWith(Suite::class)
@Suite.SuiteClasses(
        JDBCHashMapTestSuite.MapLoadOnInitFalse::class,
        JDBCHashMapTestSuite.MapLoadOnInitTrue::class,
        JDBCHashMapTestSuite.MapConstrained::class,
        JDBCHashMapTestSuite.SetLoadOnInitFalse::class,
        JDBCHashMapTestSuite.SetLoadOnInitTrue::class,
        JDBCHashMapTestSuite.SetConstrained::class)
class JDBCHashMapTestSuite {
    companion object {
        lateinit var transaction: Transaction
        lateinit var database: CordaPersistence
        lateinit var loadOnInitFalseMap: JDBCHashMap<String, String>
        lateinit var memoryConstrainedMap: JDBCHashMap<String, String>
        lateinit var loadOnInitTrueMap: JDBCHashMap<String, String>
        lateinit var loadOnInitFalseSet: JDBCHashSet<String>
        lateinit var memoryConstrainedSet: JDBCHashSet<String>
        lateinit var loadOnInitTrueSet: JDBCHashSet<String>

        @JvmStatic
        @BeforeClass
        fun before() {
            database = configureDatabase(makeTestDataSourceProperties())
            setUpDatabaseTx()
            loadOnInitFalseMap = JDBCHashMap<String, String>("test_map_false", loadOnInit = false)
            memoryConstrainedMap = JDBCHashMap<String, String>("test_map_constrained", loadOnInit = false, maxBuckets = 1)
            loadOnInitTrueMap = JDBCHashMap<String, String>("test_map_true", loadOnInit = true)
            loadOnInitFalseSet = JDBCHashSet<String>("test_set_false", loadOnInit = false)
            memoryConstrainedSet = JDBCHashSet<String>("test_set_constrained", loadOnInit = false, maxBuckets = 1)
            loadOnInitTrueSet = JDBCHashSet<String>("test_set_true", loadOnInit = true)
        }

        @JvmStatic
        @AfterClass
        fun after() {
            closeDatabaseTx()
            database.close()
        }

        @JvmStatic
        fun createMapTestSuite(loadOnInit: Boolean, constrained: Boolean): TestSuite = MapTestSuiteBuilder
                .using(JDBCHashMapTestGenerator(loadOnInit = loadOnInit, constrained = constrained))
                .named("test JDBCHashMap with loadOnInit=$loadOnInit")
                .withFeatures(
                        CollectionSize.ANY,
                        MapFeature.ALLOWS_ANY_NULL_QUERIES,
                        MapFeature.GENERAL_PURPOSE,
                        CollectionFeature.SUPPORTS_ITERATOR_REMOVE,
                        CollectionFeature.KNOWN_ORDER
                )
                // putAll(null) not supported by Kotlin MutableMap interface
                .suppressing(MapPutAllTester::class.java.getMethod("testPutAll_nullCollectionReference"))
                // We suppress the following because of NotReallyMutableEntry
                .suppressing(MapReplaceAllTester::class.java.getMethod("testReplaceAllPreservesOrder"))
                .suppressing(MapReplaceAllTester::class.java.getMethod("testReplaceAllRotate"))
                .suppressing(MapEntrySetTester::class.java.getMethod("testSetValue"))
                .createTestSuite()

        @JvmStatic
        fun createSetTestSuite(loadOnInit: Boolean, constrained: Boolean): TestSuite = SetTestSuiteBuilder
                .using(JDBCHashSetTestGenerator(loadOnInit = loadOnInit, constrained = constrained))
                .named("test JDBCHashSet with loadOnInit=$loadOnInit")
                .withFeatures(
                        CollectionSize.ANY,
                        SetFeature.GENERAL_PURPOSE,
                        CollectionFeature.SUPPORTS_ITERATOR_REMOVE,
                        CollectionFeature.KNOWN_ORDER
                )
                // add/remove/retainAll(null) not supported by Kotlin MutableSet interface
                .suppressing(CollectionAddAllTester::class.java.getMethod("testAddAll_nullCollectionReference"))
                .suppressing(CollectionAddAllTester::class.java.getMethod("testAddAll_nullUnsupported"))
                .suppressing(CollectionAddTester::class.java.getMethod("testAdd_nullUnsupported"))
                .suppressing(CollectionCreationTester::class.java.getMethod("testCreateWithNull_unsupported"))
                .suppressing(CollectionRemoveAllTester::class.java.getMethod("testRemoveAll_nullCollectionReferenceNonEmptySubject"))
                .suppressing(CollectionRemoveAllTester::class.java.getMethod("testRemoveAll_nullCollectionReferenceEmptySubject"))
                .suppressing(CollectionRetainAllTester::class.java.getMethod("testRetainAll_nullCollectionReferenceNonEmptySubject"))
                .suppressing(CollectionRetainAllTester::class.java.getMethod("testRetainAll_nullCollectionReferenceEmptySubject"))
                .createTestSuite()

        private fun setUpDatabaseTx() {
            transaction = TransactionManager.currentOrNew(Connection.TRANSACTION_REPEATABLE_READ)
        }

        private fun closeDatabaseTx() {
            transaction.commit()
            transaction.close()
        }
    }

    /**
     * Guava test suite generator for JDBCHashMap(loadOnInit=false, constrained = false).
     */
    class MapLoadOnInitFalse {
        companion object {
            @JvmStatic
            fun suite(): TestSuite = createMapTestSuite(false, false)
        }
    }

    /**
     * Guava test suite generator for JDBCHashMap(loadOnInit=false, constrained = true).
     */
    class MapConstrained {
        companion object {
            @JvmStatic
            fun suite(): TestSuite = createMapTestSuite(false, true)
        }
    }

    /**
     * Guava test suite generator for JDBCHashMap(loadOnInit=true, constrained = false).
     */
    class MapLoadOnInitTrue {
        companion object {
            @JvmStatic
            fun suite(): TestSuite = createMapTestSuite(true, false)
        }
    }

    /**
     * Generator of map instances needed for testing.
     */
    class JDBCHashMapTestGenerator(val loadOnInit: Boolean, val constrained: Boolean) : TestStringMapGenerator() {
        override fun create(elements: Array<Map.Entry<String, String>>): Map<String, String> {
            val map = if (loadOnInit) loadOnInitTrueMap else if (constrained) memoryConstrainedMap else loadOnInitFalseMap
            map.clear()
            map.putAll(elements.associate { Pair(it.key, it.value) })
            return map
        }
    }

    /**
     * Guava test suite generator for JDBCHashSet(loadOnInit=false, constrained = false).
     */
    class SetLoadOnInitFalse {
        companion object {
            @JvmStatic
            fun suite(): TestSuite = createSetTestSuite(false, false)
        }
    }

    /**
     * Guava test suite generator for JDBCHashSet(loadOnInit=false, constrained = true).
     */
    class SetConstrained {
        companion object {
            @JvmStatic
            fun suite(): TestSuite = createSetTestSuite(false, true)
        }
    }

    /**
     * Guava test suite generator for JDBCHashSet(loadOnInit=true, constrained = false).
     */
    class SetLoadOnInitTrue {
        companion object {
            @JvmStatic
            fun suite(): TestSuite = createSetTestSuite(true, false)
        }
    }

    /**
     * Generator of set instances needed for testing.
     */
    class JDBCHashSetTestGenerator(val loadOnInit: Boolean, val constrained: Boolean) : TestStringSetGenerator() {
        override fun create(elements: Array<String>): Set<String> {
            val set = if (loadOnInit) loadOnInitTrueSet else if (constrained) memoryConstrainedSet else loadOnInitFalseSet
            set.clear()
            set.addAll(elements)
            return set
        }
    }

    /**
     * Test that the contents of a map can be reloaded from the database.
     *
     * If the Map reloads, then so will the Set as it just delegates.
     */
    class MapCanBeReloaded {
        private val ops = listOf(Triple(AddOrRemove.ADD, "A", "1"),
                Triple(AddOrRemove.ADD, "B", "2"),
                Triple(AddOrRemove.ADD, "C", "3"),
                Triple(AddOrRemove.ADD, "D", "4"),
                Triple(AddOrRemove.ADD, "E", "5"),
                Triple(AddOrRemove.REMOVE, "A", "6"),
                Triple(AddOrRemove.ADD, "G", "7"),
                Triple(AddOrRemove.ADD, "H", "8"),
                Triple(AddOrRemove.REMOVE, "D", "9"),
                Triple(AddOrRemove.ADD, "C", "10"))

        private fun applyOpsToMap(map: MutableMap<String, String>): MutableMap<String, String> {
            for (op in ops) {
                if (op.first == AddOrRemove.ADD) {
                    map[op.second] = op.third
                } else {
                    map.remove(op.second)
                }
            }
            return map
        }

        private val transientMapForComparison = applyOpsToMap(LinkedHashMap())

        lateinit var database: CordaPersistence

        @Before
        fun before() {
            database = configureDatabase(makeTestDataSourceProperties())
        }

        @After
        fun after() {
            database.close()
        }


        @Test
        fun `fill map and check content after reconstruction`() {
            database.transaction {
                val persistentMap = JDBCHashMap<String, String>("the_table")
                // Populate map the first time.
                applyOpsToMap(persistentMap)
                assertThat(persistentMap.entries).containsExactly(*transientMapForComparison.entries.toTypedArray())
            }
            database.transaction {
                val persistentMap = JDBCHashMap<String, String>("the_table", loadOnInit = false)
                assertThat(persistentMap.entries).containsExactly(*transientMapForComparison.entries.toTypedArray())
            }
            database.transaction {
                val persistentMap = JDBCHashMap<String, String>("the_table", loadOnInit = true)
                assertThat(persistentMap.entries).containsExactly(*transientMapForComparison.entries.toTypedArray())
            }
        }
    }
}
