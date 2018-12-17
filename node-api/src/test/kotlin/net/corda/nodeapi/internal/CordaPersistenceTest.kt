package net.corda.nodeapi.internal

import net.corda.node.services.schema.NodeSchemaService
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.internal.configureDatabase
import net.corda.testing.node.MockServices
import org.junit.After
import org.junit.Test
import java.util.concurrent.Phaser
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.assertEquals

class CordaPersistenceTest {
    private val database = configureDatabase(MockServices.makeTestDataSourceProperties(),
            DatabaseConfig(),
            { null }, { null },
            NodeSchemaService(emptySet()))

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `onAllOpenTransactionsClosed with zero transactions calls back immediately`() {
        val counter = AtomicInteger(0)
        database.onAllOpenTransactionsClosed { counter.incrementAndGet() }
        assertEquals(1, counter.get())
    }

    @Test
    fun `onAllOpenTransactionsClosed with one transaction calls back after closing`() {
        val counter = AtomicInteger(0)
        database.transaction {
            database.onAllOpenTransactionsClosed { counter.incrementAndGet() }
            assertEquals(0, counter.get())
        }
        assertEquals(1, counter.get())
    }

    @Test
    fun `onAllOpenTransactionsClosed after one transaction has closed calls back immediately`() {
        val counter = AtomicInteger(0)
        database.transaction {
            database.onAllOpenTransactionsClosed { counter.incrementAndGet() }
            assertEquals(0, counter.get())
        }
        assertEquals(1, counter.get())
        database.onAllOpenTransactionsClosed { counter.incrementAndGet() }
        assertEquals(2, counter.get())
    }

    @Test
    fun `onAllOpenTransactionsClosed with two transactions calls back after closing both`() {
        val counter = AtomicInteger(0)
        val phaser = openTransactionInOtherThreadAndCloseWhenISay()
        // Wait for tx to be started.
        phaser.arriveAndAwaitAdvance()
        database.transaction {
            database.onAllOpenTransactionsClosed { counter.incrementAndGet() }
            assertEquals(0, counter.get())
        }
        assertEquals(0, counter.get())
        phaser.arriveAndAwaitAdvance()
        phaser.arriveAndAwaitAdvance()
        assertEquals(1, counter.get())
    }

    @Test
    fun `onAllOpenTransactionsClosed with two transactions calls back after closing both - instigator closes last`() {
        val counter = AtomicInteger(0)
        val phaser = openTransactionInOtherThreadAndCloseWhenISay()
        // Wait for tx to be started.
        phaser.arriveAndAwaitAdvance()
        database.transaction {
            database.onAllOpenTransactionsClosed { counter.incrementAndGet() }
            assertEquals(0, counter.get())
            phaser.arriveAndAwaitAdvance()
            phaser.arriveAndAwaitAdvance()
            assertEquals(0, counter.get())
        }
        assertEquals(1, counter.get())
    }

    private fun openTransactionInOtherThreadAndCloseWhenISay(): Phaser {
        val phaser = Phaser()
        phaser.bulkRegister(2)
        thread {
            database.transaction {
                phaser.arriveAndAwaitAdvance()
                phaser.arriveAndAwaitAdvance()
            }
            // Tell caller we have committed.
            phaser.arriveAndAwaitAdvance()
        }
        return phaser
    }
}