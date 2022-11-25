package net.corda.node.utilities

import com.google.common.util.concurrent.SettableFuture
import net.corda.node.services.schema.NodeSchemaService
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.internal.TestingNamedCacheFactory
import net.corda.testing.internal.configureDatabase
import net.corda.testing.node.MockServices
import org.junit.After
import org.junit.Test
import java.util.concurrent.Phaser
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

    @Test(timeout = 300_000)
    fun `invalidate outside transaction should not hang`() {
        cache.invalidate("Fred")
    }

    @Test(timeout=300_000)
	fun `get from empty cache returns result of loader`() {
        database.transaction {
            // This will cache "1"
            val result = cache.get("foo") {
                1
            }
            assertEquals(1, result)
        }
    }

    @Test(timeout=300_000)
	fun `getIfPresent from empty cache returns null`() {
        database.transaction {
            val result = cache.getIfPresent("foo")
            assertNull(result)
        }
    }

    @Test(timeout=300_000)
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

    @Test(timeout=300_000)
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

    @Test(timeout=300_000)
	fun `getIfPresent after get from empty cache returns result of first loader`() {
        database.transaction {
            // This will cache "2"
            cache.get("foo") {
                2
            }
            val result = cache.getIfPresent("foo")
            assertEquals(2, result)
        }
    }

    @Test(timeout=300_000)
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

    @Test(timeout=300_000)
	fun `getIfPresent after get from empty cache with invalidate in the middle returns null`() {
        database.transaction {
            // This will cache "2"
            cache.get("foo") {
                2
            }
            cache.invalidate("foo")
            val result = cache.getIfPresent("foo")
            assertNull(result)
        }
    }

    @Test(timeout=300_000)
	fun `second get from empty cache with invalidate and flush in the middle returns result of third loader`() {
        database.transaction {
            // This will cache "2"
            cache.get("foo") {
                3
            }
            cache.invalidate("foo")
            cache.flushCache()
            cache.get("foo") {
                2
            }
            val result = cache.get("foo") {
                1
            }
            assertEquals(1, result)
        }
    }

    @Test(timeout=300_000)
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

    @Test(timeout=300_000)
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

    @Test(timeout=300_000)
	fun `getIfPresent outside first transaction from empty cache with invalidate in the middle returns result of third loader`() {
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
            val result = cache.getIfPresent("foo")
            assertNull(result)
        }
    }

    @Test(timeout=300_000)
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

    @Test(timeout=300_000)
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

    @Test(timeout=300_000)
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

    @Test(timeout=300_000)
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

    @Test(timeout=300_000)
	fun `transaction started before invalidating thread commits does not cache until after the other thread commits`() {
        database.transaction {
            // This will cache "2"
            cache.get("foo") {
                2
            }
        }
        val phaser = invalidateInOtherThreadWhenISay("foo")
        // Wait for other thread to start their transaction.
        phaser.arriveAndAwaitAdvance()
        // Tell other thread to call invalidate
        phaser.arriveAndAwaitAdvance()
        // Wait for the other thread to call invalidate
        phaser.arriveAndAwaitAdvance()
        database.transaction {
            // This should not get cached, as the transaction that invalidated it in the other thread has completed but we might
            // not see the new value in our transaction since it started first.
            val result1 = cache.get("foo") {
                1
            }
            assertEquals(1, result1)
            val result2 = cache.get("foo") {
                3
            }
            assertEquals(3, result2)

            // Now allow other thread to commit transaction
            phaser.arriveAndAwaitAdvance()
            // and wait for commit to be complete
            phaser.arriveAndAwaitAdvance()

            // This should get cached, as the transaction that invalidated it in the other thread has completed but we might
            // not see the new value in our transaction since it started first.
            val result3 = cache.get("foo") {
                3
            }
            assertEquals(3, result3)
            val result4 = cache.get("foo") {
                4
            }
            assertEquals(4, result4)

        }
        // This can now get cached, as the transaction that invalidated is complete.
        database.transaction {
            val result = cache.get("foo") {
                5
            }
            assertEquals(5, result)
            val result2 = cache.get("foo") {
                6
            }
            assertEquals(5, result2)
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

    private fun invalidateInOtherThreadWhenISay(key: String): Phaser {
        val phaser = Phaser()
        phaser.bulkRegister(2)
        thread {
            database.transaction {
                // Wait for caller and tell them we have started a transaction.
                phaser.arriveAndAwaitAdvance()
                // Wait for caller to say it's okay to invalidate.
                phaser.arriveAndAwaitAdvance()
                cache.invalidate(key)
                // Tell caller we have invalidated.
                phaser.arriveAndAwaitAdvance()
                // Wait for caller to allow us to commit transaction.
                phaser.arriveAndAwaitAdvance()
            }
            // Tell caller we have committed.
            phaser.arriveAndAwaitAdvance()
        }
        return phaser
    }
}