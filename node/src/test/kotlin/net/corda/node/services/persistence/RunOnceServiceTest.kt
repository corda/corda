package net.corda.node.services.persistence

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import net.corda.node.internal.configureDatabase
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import net.corda.testing.node.internal.makeTestDatabaseProperties
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.contrib.java.lang.system.ExpectedSystemExit
import java.time.LocalDateTime
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import javax.persistence.Query
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RunOnceServiceTest {

    @Rule
    @JvmField
    val exit: ExpectedSystemExit = ExpectedSystemExit.none()

    private lateinit var database: CordaPersistence
    private val selectQuery = "SELECT * FROM $TABLE WHERE $ID='X'"
    private val deleteQuery = "DELETE FROM $TABLE"
    private val updateMachineNameQuery = "UPDATE $TABLE SET $MACHINE_NAME='someOtherMachine'"
    private val updateMachinePidQuery = "UPDATE $TABLE SET $PID='999'"

    private lateinit var runOnceServiceMachine1: RunOnceService
    private lateinit var runOnceServiceMachine2: RunOnceService
    private val mockUpdateExecutor = mock<ScheduledExecutorService>()

    @Before
    fun setup() {
        database = configureDatabase(makeTestDataSourceProperties(), makeTestDatabaseProperties(), { null }, { null })
        runOnceServiceMachine1 = RunOnceService(database, "machine1", "123", 1, 2, mockUpdateExecutor)
        runOnceServiceMachine2 = RunOnceService(database, "machine2", "789", 1, 2, mockUpdateExecutor)
    }

    @After
    fun cleanUp() {
        database.close()
    }

    @Test
    fun `change of master node exits if failed to update row`() {
        runOnceServiceMachine1.start()

        val waitInterval = 20000000000000000

        val runOnceServiceLongWait = RunOnceService(database, "machineLongWait", "99999", 1,
                waitInterval, mockUpdateExecutor)
        // fails as didn't wait long enough, someone else could still be running
        assertFailsWith<RunOnceService.RunOnceServiceWaitIntervalSleepException> { runOnceServiceLongWait.start() }
    }

    @Test
    fun `row updated when change of master node`() {
        runOnceServiceMachine1.start()

        var secondTimestamp = LocalDateTime.now()
        var firstTimestamp = LocalDateTime.now()

        database.transaction {
            val query = session.createNativeQuery(selectQuery, RunOnceService.MutualExclusion::class.java)
            val result = machine1RowCheck(query)
            firstTimestamp = result.timestamp
        }

        // runOnceServiceMachine2 changes to master node if we have waited more than 2 millis
        Thread.sleep(3)

        runOnceServiceMachine2.start()

        database.transaction {
            val query = session.createNativeQuery(selectQuery, RunOnceService.MutualExclusion::class.java)
            val result = machine2RowCheck(query)
            secondTimestamp = result.timestamp
        }

        assertTrue(secondTimestamp.isAfter(firstTimestamp))
    }

    @Test
    fun `row created if none exist`() {
        database.transaction {
            val query = session.createNativeQuery(selectQuery, RunOnceService.MutualExclusion::class.java)
            assertEquals(0, query.resultList.size)
        }

        runOnceServiceMachine1.start()

        database.transaction {
            val query = session.createNativeQuery(selectQuery, RunOnceService.MutualExclusion::class.java)
            machine1RowCheck(query)
        }
    }

    @Test
    fun `row updated when last run was same machine`() {
        runOnceServiceMachine1.start()

        var secondTimestamp = LocalDateTime.now()
        var firstTimestamp = LocalDateTime.now()

        database.transaction {
            val query = session.createNativeQuery(selectQuery, RunOnceService.MutualExclusion::class.java)
            val result = machine1RowCheck(query)
            firstTimestamp = result.timestamp
        }

        // make sure to wait so secondTimestamp is after first
        Thread.sleep(1)

        runOnceServiceMachine1.start()

        database.transaction {
            val query = session.createNativeQuery(selectQuery, RunOnceService.MutualExclusion::class.java)
            val result = machine1RowCheck(query)
            secondTimestamp = result.timestamp
        }

        assertTrue(secondTimestamp.isAfter(firstTimestamp))
    }

    @Test
    fun `timer updates row`() {
        whenever(mockUpdateExecutor.scheduleAtFixedRate(any(), any(), any(), any())).thenAnswer { invocation ->
            val runnable = invocation.arguments[0] as Runnable

            var secondTimestamp = LocalDateTime.now()
            var firstTimestamp = LocalDateTime.now()
            var firstVersion = -1L
            var secondVersion = -1L

            database.transaction {
                val query = session.createNativeQuery(selectQuery, RunOnceService.MutualExclusion::class.java)
                val result = machine1RowCheck(query)
                firstTimestamp = result.timestamp
                firstVersion = result.version
            }

            runnable.run()

            database.transaction {
                val query = session.createNativeQuery(selectQuery, RunOnceService.MutualExclusion::class.java)
                val result = machine1RowCheck(query)
                secondTimestamp = result.timestamp
                secondVersion = result.version
            }

            assertTrue(secondTimestamp.isAfter(firstTimestamp))
            assertTrue(secondVersion > firstVersion)

            mock<ScheduledFuture<*>>()
        }

        runOnceServiceMachine1.start()

        verify(mockUpdateExecutor).scheduleAtFixedRate(any(), any(), any(), any())
    }

    @Test
    fun `timer exits if no row`() {
        exit.expectSystemExitWithStatus(1)

        whenever(mockUpdateExecutor.scheduleAtFixedRate(any(), any(), any(), any())).thenAnswer { invocation ->
            val runnable = invocation.arguments[0] as Runnable

            // delete row
            database.transaction {
                val query = session.createNativeQuery(deleteQuery, RunOnceService.MutualExclusion::class.java)
                query.executeUpdate()
            }

            runnable.run()
            mock<ScheduledFuture<*>>()
        }

        runOnceServiceMachine1.start()
    }

    @Test
    fun `timer exits if different machine name`() {
        exit.expectSystemExitWithStatus(1)

        whenever(mockUpdateExecutor.scheduleAtFixedRate(any(), any(), any(), any())).thenAnswer { invocation ->
            val runnable = invocation.arguments[0] as Runnable

            database.transaction {
                val query = session.createNativeQuery(updateMachineNameQuery, RunOnceService.MutualExclusion::class.java)
                query.executeUpdate()
            }

            runnable.run()
            mock<ScheduledFuture<*>>()
        }

        runOnceServiceMachine1.start()
    }

    @Test
    fun `timer exits if different machine pid`() {
        exit.expectSystemExitWithStatus(1)

        whenever(mockUpdateExecutor.scheduleAtFixedRate(any(), any(), any(), any())).thenAnswer { invocation ->
            val runnable = invocation.arguments[0] as Runnable

            database.transaction {
                val query = session.createNativeQuery(updateMachinePidQuery, RunOnceService.MutualExclusion::class.java)
                query.executeUpdate()
            }

            runnable.run()
            mock<ScheduledFuture<*>>()
        }

        runOnceServiceMachine1.start()
    }

    @Test(expected = RuntimeException::class)
    fun `wait interval greater than update interval`() {
        RunOnceService(database, "machine1", "123", 2, 2, mockUpdateExecutor)
    }

    private fun machine1RowCheck(query: Query): RunOnceService.MutualExclusion {
        assertEquals(1, query.resultList.size)
        val result = query.resultList[0] as RunOnceService.MutualExclusion
        assertEquals('X', result.id)
        assertEquals("machine1", result.machineName)
        assertEquals("123", result.pid)
        return result
    }

    private fun machine2RowCheck(query: Query): RunOnceService.MutualExclusion {
        assertEquals(1, query.resultList.size)
        val result = query.resultList[0] as RunOnceService.MutualExclusion
        assertEquals('X', result.id)
        assertEquals("machine2", result.machineName)
        assertEquals("789", result.pid)
        return result
    }
}

