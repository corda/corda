package net.corda.node.services.persistence

import net.corda.core.schemas.MappedSchema
import net.corda.core.utilities.loggerFor
import net.corda.node.internal.configureDatabase
import net.corda.node.services.schema.NodeSchemaService
import net.corda.node.utilities.AppendOnlyPersistentMap
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.internal.rigorousMock
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.Serializable
import java.util.concurrent.CountDownLatch
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.PersistenceException

@RunWith(Parameterized::class)
class AppendOnlyPersistentMapTest(var scenario: Scenario) {
    companion object {

        private val scenarios = arrayOf<Scenario>(
                Scenario(false, ReadOrWrite.Read, ReadOrWrite.Read, Outcome.Fail, Outcome.Fail),
                Scenario(false, ReadOrWrite.Write, ReadOrWrite.Read, Outcome.Success, Outcome.Fail, Outcome.Success),
                Scenario(false, ReadOrWrite.Read, ReadOrWrite.Write, Outcome.Fail, Outcome.Success),
                Scenario(false, ReadOrWrite.Write, ReadOrWrite.Write, Outcome.Success, Outcome.SuccessButErrorOnCommit),
                Scenario(false, ReadOrWrite.WriteDuplicateAllowed, ReadOrWrite.Read, Outcome.Success, Outcome.Fail, Outcome.Success),
                Scenario(false, ReadOrWrite.Read, ReadOrWrite.WriteDuplicateAllowed, Outcome.Fail, Outcome.Success),
                Scenario(false, ReadOrWrite.WriteDuplicateAllowed, ReadOrWrite.WriteDuplicateAllowed, Outcome.Success, Outcome.SuccessButErrorOnCommit, Outcome.Fail),
                Scenario(true, ReadOrWrite.Read, ReadOrWrite.Read, Outcome.Success, Outcome.Success),
                Scenario(true, ReadOrWrite.Write, ReadOrWrite.Read, Outcome.SuccessButErrorOnCommit, Outcome.Success),
                Scenario(true, ReadOrWrite.Read, ReadOrWrite.Write, Outcome.Success, Outcome.Fail),
                Scenario(true, ReadOrWrite.Write, ReadOrWrite.Write, Outcome.SuccessButErrorOnCommit, Outcome.SuccessButErrorOnCommit),
                Scenario(true, ReadOrWrite.WriteDuplicateAllowed, ReadOrWrite.Read, Outcome.Fail, Outcome.Success),
                Scenario(true, ReadOrWrite.Read, ReadOrWrite.WriteDuplicateAllowed, Outcome.Success, Outcome.Fail),
                Scenario(true, ReadOrWrite.WriteDuplicateAllowed, ReadOrWrite.WriteDuplicateAllowed, Outcome.Fail, Outcome.Fail)
        )

        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun data(): Array<Array<Scenario>> = scenarios.map { arrayOf(it) }.toTypedArray()
    }

    enum class ReadOrWrite { Read, Write, WriteDuplicateAllowed }
    enum class Outcome { Success, Fail, SuccessButErrorOnCommit }

    data class Scenario(val prePopulated: Boolean,
                        val a: ReadOrWrite,
                        val b: ReadOrWrite,
                        val aExpected: Outcome,
                        val bExpected: Outcome,
                        val bExpectedIfSingleThreaded: Outcome = bExpected)

    private val database = configureDatabase(makeTestDataSourceProperties(),
            DatabaseConfig(),
            rigorousMock(),
            NodeSchemaService(setOf(MappedSchema(AppendOnlyPersistentMapTest::class.java, 1, listOf(PersistentMapEntry::class.java)))))

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `concurrent test no purge between A and B`() {
        prepopulateIfRequired()
        val map = createMap()
        val a = TestThread("A", map).apply { start() }
        val b = TestThread("B", map).apply { start() }

        // Begin A
        a.phase1.countDown()
        a.await(a::phase2)

        // Begin B
        b.phase1.countDown()
        b.await(b::phase2)

        // Commit A
        a.phase3.countDown()
        a.await(a::phase4)

        // Commit B
        b.phase3.countDown()
        b.await(b::phase4)

        // End
        a.join()
        b.join()
        assertTrue(map.pendingKeysIsEmpty())
    }

    @Test
    fun `test no purge with only a single transaction`() {
        prepopulateIfRequired()
        val map = createMap()
        val a = TestThread("A", map, true).apply {
            phase1.countDown()
            phase3.countDown()
        }
        val b = TestThread("B", map, true).apply {
            phase1.countDown()
            phase3.countDown()
        }
        try {
            database.transaction {
                a.run()
                b.run()
            }
        } catch (t: PersistenceException) {
            // This only helps if thrown on commit, otherwise other latches not counted down.
            assertEquals(t.message, Outcome.SuccessButErrorOnCommit, a.outcome)
        }
        a.await(a::phase4)
        b.await(b::phase4)
        assertTrue(map.pendingKeysIsEmpty())
    }


    @Test
    fun `concurrent test purge between A and B`() {
        // Writes intentionally do not check the database first, so purging between read and write changes behaviour
        val remapped = mapOf(Scenario(true, ReadOrWrite.Read, ReadOrWrite.Write, Outcome.Success, Outcome.Fail) to Scenario(true, ReadOrWrite.Read, ReadOrWrite.Write, Outcome.Success, Outcome.SuccessButErrorOnCommit))
        scenario = remapped[scenario] ?: scenario
        prepopulateIfRequired()
        val map = createMap()
        val a = TestThread("A", map).apply { start() }
        val b = TestThread("B", map).apply { start() }

        // Begin A
        a.phase1.countDown()
        a.await(a::phase2)

        map.invalidate()

        // Begin B
        b.phase1.countDown()
        b.await(b::phase2)

        // Commit A
        a.phase3.countDown()
        a.await(a::phase4)

        // Commit B
        b.phase3.countDown()
        b.await(b::phase4)

        // End
        a.join()
        b.join()
        assertTrue(map.pendingKeysIsEmpty())
    }

    @Test
    fun `test purge mid-way in a single transaction`() {
        // Writes intentionally do not check the database first, so purging between read and write changes behaviour
        val remapped = mapOf(Scenario(true, ReadOrWrite.Read, ReadOrWrite.Write, Outcome.Success, Outcome.Fail) to Scenario(true, ReadOrWrite.Read, ReadOrWrite.Write, Outcome.SuccessButErrorOnCommit, Outcome.SuccessButErrorOnCommit))
        scenario = remapped[scenario] ?: scenario
        prepopulateIfRequired()
        val map = createMap()
        val a = TestThread("A", map, true).apply {
            phase1.countDown()
            phase3.countDown()
        }
        val b = TestThread("B", map, true).apply {
            phase1.countDown()
            phase3.countDown()
        }
        try {
            database.transaction {
                a.run()
                map.invalidate()
                b.run()
            }
        } catch (t: PersistenceException) {
            // This only helps if thrown on commit, otherwise other latches not counted down.
            assertEquals(t.message, Outcome.SuccessButErrorOnCommit, a.outcome)
        }
        a.await(a::phase4)
        b.await(b::phase4)
        assertTrue(map.pendingKeysIsEmpty())
    }

    inner class TestThread(name: String, val map: AppendOnlyPersistentMap<Long, String, PersistentMapEntry, Long>, singleThreaded: Boolean = false) : Thread(name) {
        private val log = loggerFor<TestThread>()

        val readOrWrite = if (name == "A") scenario.a else scenario.b
        val outcome = if (name == "A") scenario.aExpected else if (singleThreaded) scenario.bExpectedIfSingleThreaded else scenario.bExpected

        val phase1 = latch()
        val phase2 = latch()
        val phase3 = latch()
        val phase4 = latch()

        override fun run() {
            try {
                database.transaction {
                    await(::phase1)
                    doActivity()
                    phase2.countDown()
                    await(::phase3)
                }
            } catch (t: PersistenceException) {
                // This only helps if thrown on commit, otherwise other latches not counted down.
                assertEquals(t.message, Outcome.SuccessButErrorOnCommit, outcome)
            }
            phase4.countDown()
        }

        private fun doActivity() {
            if (readOrWrite == ReadOrWrite.Read) {
                log.info("Reading")
                val value = map.get(1)
                log.info("Read $value")
                if (outcome == Outcome.Success || outcome == Outcome.SuccessButErrorOnCommit) {
                    assertEquals("X", value)
                } else {
                    assertNull(value)
                }
            } else if (readOrWrite == ReadOrWrite.Write) {
                log.info("Writing")
                val wasSet = map.set(1, "X")
                log.info("Write $wasSet")
                if (outcome == Outcome.Success || outcome == Outcome.SuccessButErrorOnCommit) {
                    assertEquals(true, wasSet)
                } else {
                    assertEquals(false, wasSet)
                }
            } else if (readOrWrite == ReadOrWrite.WriteDuplicateAllowed) {
                log.info("Writing with duplicates allowed")
                val wasSet = map.addWithDuplicatesAllowed(1, "X")
                log.info("Write with duplicates allowed $wasSet")
                if (outcome == Outcome.Success || outcome == Outcome.SuccessButErrorOnCommit) {
                    assertEquals(true, wasSet)
                } else {
                    assertEquals(false, wasSet)
                }
            }
        }

        private fun latch() = CountDownLatch(1)
        fun await(latch: () -> CountDownLatch) {
            log.info("Awaiting $latch")
            latch().await()
        }
    }

    private fun prepopulateIfRequired() {
        if (scenario.prePopulated) {
            database.transaction {
                val map = createMap()
                map.set(1, "X")
            }
        }
    }

    @Entity
    @javax.persistence.Table(name = "persist_map_test")
    class PersistentMapEntry(
            @Id
            @Column(name = "key")
            var key: Long = -1,

            @Column(name = "value", length = 16)
            var value: String = ""
    ) : Serializable

    class TestMap : AppendOnlyPersistentMap<Long, String, PersistentMapEntry, Long>(
            toPersistentEntityKey = { it },
            fromPersistentEntity = { Pair(it.key, it.value) },
            toPersistentEntity = { key: Long, value: String ->
                PersistentMapEntry().apply {
                    this.key = key
                    this.value = value
                }
            },
            persistentEntityClass = PersistentMapEntry::class.java
    ) {
        fun pendingKeysIsEmpty() = pendingKeys.isEmpty()

        fun invalidate() = cache.invalidateAll()
    }

    fun createMap() = TestMap()
}