package com.r3corda.node.utilities

import com.r3corda.testing.node.makeTestDataSourceProperties
import junit.framework.TestSuite
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.runner.RunWith
import org.junit.runners.Suite
import java.io.Closeable
import java.sql.Connection

@RunWith(Suite::class)
@Suite.SuiteClasses(
        JDBCHashMapTestSuite.MapLoadOnInitFalse::class,
        JDBCHashMapTestSuite.MapLoadOnInitTrue::class,
        JDBCHashMapTestSuite.SetLoadOnInitFalse::class,
        JDBCHashMapTestSuite.SetLoadOnInitTrue::class)
class JDBCHashMapTestSuite {
    companion object {
        lateinit var dataSource: Closeable
        lateinit var transaction: Transaction

        @JvmStatic
        @BeforeClass
        fun before() {
            dataSource = configureDatabase(makeTestDataSourceProperties()).first
        }

        @JvmStatic
        @AfterClass
        fun after() {
            dataSource.close()
        }

        @JvmStatic
        fun createMapTestSuite(loadOnInit: Boolean): TestSuite = com.google.common.collect.testing.MapTestSuiteBuilder
                .using(JDBCHashMapTestGenerator(loadOnInit = loadOnInit))
                .named("test JDBCHashMap with loadOnInit=$loadOnInit")
                .withFeatures(
                        com.google.common.collect.testing.features.CollectionSize.ANY,
                        com.google.common.collect.testing.features.MapFeature.ALLOWS_ANY_NULL_QUERIES,
                        com.google.common.collect.testing.features.MapFeature.GENERAL_PURPOSE,
                        com.google.common.collect.testing.features.CollectionFeature.SUPPORTS_ITERATOR_REMOVE,
                        com.google.common.collect.testing.features.CollectionFeature.KNOWN_ORDER
                )
                // putAll(null) not supported by Kotlin MutableMap interface
                .suppressing(com.google.common.collect.testing.testers.MapPutAllTester::class.java.getMethod("testPutAll_nullCollectionReference"))
                .withSetUp { setUpDatabaseTx() }
                .withTearDown { closeDatabaseTx() }
                .createTestSuite()

        @JvmStatic
        fun createSetTestSuite(loadOnInit: Boolean): TestSuite = com.google.common.collect.testing.SetTestSuiteBuilder
                .using(JDBCHashSetTestGenerator(loadOnInit = loadOnInit))
                .named("test JDBCHashSet with loadOnInit=$loadOnInit")
                .withFeatures(
                        com.google.common.collect.testing.features.CollectionSize.ANY,
                        com.google.common.collect.testing.features.SetFeature.GENERAL_PURPOSE,
                        com.google.common.collect.testing.features.CollectionFeature.SUPPORTS_ITERATOR_REMOVE,
                        com.google.common.collect.testing.features.CollectionFeature.KNOWN_ORDER
                )
                // add/remove/retainAll(null) not supported by Kotlin MutableSet interface
                .suppressing(com.google.common.collect.testing.testers.CollectionAddAllTester::class.java.getMethod("testAddAll_nullCollectionReference"))
                .suppressing(com.google.common.collect.testing.testers.CollectionAddAllTester::class.java.getMethod("testAddAll_nullUnsupported"))
                .suppressing(com.google.common.collect.testing.testers.CollectionAddTester::class.java.getMethod("testAdd_nullUnsupported"))
                .suppressing(com.google.common.collect.testing.testers.CollectionCreationTester::class.java.getMethod("testCreateWithNull_unsupported"))
                .suppressing(com.google.common.collect.testing.testers.CollectionRemoveAllTester::class.java.getMethod("testRemoveAll_nullCollectionReferenceNonEmptySubject"))
                .suppressing(com.google.common.collect.testing.testers.CollectionRemoveAllTester::class.java.getMethod("testRemoveAll_nullCollectionReferenceEmptySubject"))
                .suppressing(com.google.common.collect.testing.testers.CollectionRetainAllTester::class.java.getMethod("testRetainAll_nullCollectionReferenceNonEmptySubject"))
                .suppressing(com.google.common.collect.testing.testers.CollectionRetainAllTester::class.java.getMethod("testRetainAll_nullCollectionReferenceEmptySubject"))
                .withSetUp { setUpDatabaseTx() }
                .withTearDown { closeDatabaseTx() }
                .createTestSuite()

        private fun setUpDatabaseTx() {
            transaction = TransactionManager.currentOrNew(Connection.TRANSACTION_REPEATABLE_READ)
        }

        private fun closeDatabaseTx() {
            transaction.commit()
        }
    }

    /**
     * Guava test suite generator for JDBCHashMap(loadOnInit=false).
     */
    class MapLoadOnInitFalse {
        companion object {
            @JvmStatic
            fun suite(): TestSuite = createMapTestSuite(false)
        }
    }

    /**
     * Guava test suite generator for JDBCHashMap(loadOnInit=true).
     */
    class MapLoadOnInitTrue {
        companion object {
            @JvmStatic
            fun suite(): TestSuite = createMapTestSuite(true)
        }
    }

    /**
     * Generator of map instances needed for testing.
     */
    class JDBCHashMapTestGenerator(val loadOnInit: Boolean) : com.google.common.collect.testing.TestStringMapGenerator() {
        override fun create(elements: Array<Map.Entry<String, String>>): Map<String, String> {
            val map = JDBCHashMap<String, String>("test_map_${System.nanoTime()}", loadOnInit = loadOnInit)
            map.putAll(elements.associate { Pair(it.key, it.value) })
            return map
        }
    }

    /**
     * Guava test suite generator for JDBCHashSet(loadOnInit=false).
     */
    class SetLoadOnInitFalse {
        companion object {
            @JvmStatic
            fun suite(): TestSuite = createSetTestSuite(false)
        }
    }

    /**
     * Guava test suite generator for JDBCHashSet(loadOnInit=true).
     */
    class SetLoadOnInitTrue {
        companion object {
            @JvmStatic
            fun suite(): TestSuite = createSetTestSuite(true)
        }
    }

    /**
     * Generator of set instances needed for testing.
     */
    class JDBCHashSetTestGenerator(val loadOnInit: Boolean) : com.google.common.collect.testing.TestStringSetGenerator() {
        override fun create(elements: Array<String>): Set<String> {
            val set = JDBCHashSet<String>("test_set_${System.nanoTime()}", loadOnInit = loadOnInit)
            set.addAll(elements)
            return set
        }
    }
}
