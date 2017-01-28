package net.corda.node.services

import net.corda.node.services.transactions.BFTSmartClient
import net.corda.node.services.transactions.BFTSmartServer
import net.corda.node.utilities.configureDatabase
import net.corda.testing.node.makeTestDataSourceProperties
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.Closeable
import kotlin.test.assertEquals
import kotlin.concurrent.thread

class DistributedImmutableBFTMapTests {

    // TODO: Setup Corda cluster instead of starting server threads (see DistributedImmutableMapTests).

    lateinit var dataSource: Closeable
    lateinit var database: Database

    @Before
    fun setup() {
        val dataSourceAndDatabase = configureDatabase(makeTestDataSourceProperties())
        dataSource = dataSourceAndDatabase.first
        database = dataSourceAndDatabase.second
    }

    @After
    fun tearDown() {
        dataSource.close()
    }

    @Test
    fun `stores entries correctly and detects conflicts`() {
        val threads = (0..3).map { i ->
            thread { BFTSmartServer<String, String>(i, database, "bft_notary_committed_states_$i") }.apply { Thread.sleep(500) }
        }

        Thread.sleep(3000) // TODO: Get notified when the servers are ready.

        val client = BFTSmartClient<String, String>(1001)

        val m = mapOf("a" to "b", "c" to "d", "e" to "f")

        val conflicts = client.put(m)
        assertEquals(mapOf(), conflicts)

        for ((k, v) in m) {
            val r = client.get(k)
            assertEquals(v, r)
        }

        val conflicts2 = client.put(mapOf("a" to "b2"))
        assertEquals(mapOf("a" to "b"), conflicts2)

        // Values are not mutated.
        for ((k, v) in m) {
            val r = client.get(k)
            assertEquals(v, r)
        }

        // Null response encodes 'not found'.
        val r = client.get("x")
        assertEquals(null, r)

        threads.forEach { t -> t.stop() }
    }
}
