package net.corda.node.utilities

import com.google.common.util.concurrent.SettableFuture
import net.corda.node.services.schema.NodeSchemaService
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.internal.TestingNamedCacheFactory
import net.corda.testing.internal.configureDatabase
import net.corda.testing.node.MockServices
import org.junit.After
import org.junit.Test
import kotlin.concurrent.thread
import kotlin.test.assertEquals

class InfrequentlyMutatedCacheTest {
    private val cache = InfrequentlyMutatedCache<String, Int>("foo", TestingNamedCacheFactory())
    private val database = configureDatabase(MockServices.makeTestDataSourceProperties(),
            DatabaseConfig(),
            { null }, { null },
            NodeSchemaService(emptySet()))

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `get from empty cache returns result of loader`() {
        database.transaction {
            // This will cache "1"
            val result = cache.get("foo") {
                1
            }
            assertEquals(1, result)
        }
    }

    @Test
    fun `other thread get returns result of local thread loader`() {
        database.transaction {
            // This will cache "1"
            val result = cache.get("foo") {
                1
            }
            assertEquals(1, result)
            // Local thread get landed first.
            val otherResult = getInOtherThread("foo", 2)
            assertEquals(1, otherResult)
        }
    }

    @Test
    fun `second get from empty cache returns result of first loader`() {
        database.transaction {
            // This will cache "2"
            cache.get("foo") {
                2
            }
            val result = cache.get("foo") {
                1
            }
            assertEquals(2, result)
        }
    }

    @Test
    fun `second get from empty cache with invalidate in the middle returns result of second loader`() {
        database.transaction {
            // This will cache "2"
            cache.get("foo") {
                2
            }
            cache.invalidate("foo")
            val result = cache.get("foo") {
                1
            }
            assertEquals(1, result)
        }
    }

    @Test
    fun `other thread get with invalidate in the middle returns result of second loader`() {
        database.transaction {
            // This will cache "2"
            cache.get("foo") {
                2
            }
            cache.invalidate("foo")
            val result = cache.get("foo") {
                1
            }
            assertEquals(1, result)
            // Whilst inside transaction, invalidate prevents caching.
            val otherResult = getInOtherThread("foo", 3)
            assertEquals(3, otherResult)
        }
    }

    @Test
    fun `third get outside first transaction from empty cache with invalidate in the middle returns result of third loader`() {
        database.transaction {
            // This will cache "2"
            cache.get("foo") {
                2
            }
            cache.invalidate("foo")
            // This should not get cached, as the transaction that invalidated is still in-flight.
            val result = cache.get("foo") {
                1
            }
            assertEquals(1, result)
        }
        database.transaction {
            val result = cache.get("foo") {
                3
            }
            assertEquals(3, result)
        }
    }

    @Test
    fun `other thread get outside first transaction with invalidate in the middle returns result of other thread`() {
        database.transaction {
            // This will cache "2"
            cache.get("foo") {
                2
            }
            cache.invalidate("foo")
            // This should not get cached, as the transaction that invalidated is still in-flight.
            val result = cache.get("foo") {
                1
            }
            assertEquals(1, result)
        }
        // Now outside transaction that invalidated, caching can begin again.
        val otherResult = getInOtherThread("foo", 3)
        assertEquals(3, otherResult)
        database.transaction {
            val result = cache.get("foo") {
                4
            }
            assertEquals(3, result)
        }
    }

    @Test
    fun `fourth get outside first transaction from empty cache with invalidate in the middle returns result of third loader`() {
        database.transaction {
            // This will cache "2"
            cache.get("foo") {
                2
            }
            cache.invalidate("foo")
            // This should not get cached, as the transaction that invalidated is still in-flight.
            val result = cache.get("foo") {
                1
            }
            assertEquals(1, result)
        }
        // This can now get cached, as the transaction that invalidated is complete.
        database.transaction {
            val result = cache.get("foo") {
                3
            }
            assertEquals(3, result)
        }
        database.transaction {
            val result = cache.get("foo") {
                4
            }
            assertEquals(3, result)
        }
    }

    @Test
    fun `fourth get outside first transaction from empty cache with nested invalidate in the middle returns result of third loader`() {
        database.transaction {
            // This will cache "2"
            cache.get("foo") {
                2
            }
            cache.invalidate("foo")
            cache.invalidate("foo")
            // This should not get cached, as the transaction that invalidated is still in-flight.
            val result = cache.get("foo") {
                1
            }
            assertEquals(1, result)
        }
        // This can now get cached, as the transaction that invalidated is complete.
        database.transaction {
            val result = cache.get("foo") {
                3
            }
            assertEquals(3, result)
        }
        database.transaction {
            val result = cache.get("foo") {
                4
            }
            assertEquals(3, result)
        }
    }

    @Test
    fun `fourth get outside first transaction from empty cache with invalidate in other thread in the middle returns result of second loader`() {
        database.transaction {
            // This will cache "2"
            cache.get("foo") {
                2
            }
            invalidateInOtherThread("foo")
            // This should not get cached, as the transaction that invalidated it in the other thread has completed but we might
            // not see the new value in our transaction since it started first.
            val result = cache.get("foo") {
                1
            }
            assertEquals(1, result)
        }
        // This can now get cached, as the transaction that invalidated is complete.
        database.transaction {
            val result = cache.get("foo") {
                3
            }
            assertEquals(3, result)
        }
    }

    private fun getInOtherThread(key: String, loader: Int): Int {
        val futureValue = SettableFuture.create<Int>()
        thread {
            database.transaction {
                val result = cache.get(key) {
                    loader
                }
                futureValue.set(result)
            }
        }
        return futureValue.get()
    }

    private fun invalidateInOtherThread(key: String) {
        val futureValue = SettableFuture.create<Unit>()
        thread {
            database.transaction {
                cache.invalidate(key)
                futureValue.set(Unit)
            }
        }
        return futureValue.get()
    }
}