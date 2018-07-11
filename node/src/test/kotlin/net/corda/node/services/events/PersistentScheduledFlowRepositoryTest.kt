package net.corda.node.services.events

import net.corda.core.contracts.ScheduledStateRef
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import net.corda.core.utilities.days
import net.corda.node.internal.configureDatabase
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.internal.rigorousMock
import net.corda.testing.node.MockServices
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PersistentScheduledFlowRepositoryTest {
    private val databaseConfig: DatabaseConfig = DatabaseConfig()
    private val mark = Instant.now()

    @Test
    fun `test that earliest item is returned`() {
        val laterTime = mark + 1.days
        val dataSourceProps = MockServices.makeTestDataSourceProperties()
        val database = configureDatabase(dataSourceProps, databaseConfig, { null }, { null })

        database.transaction {
            val repo = PersistentScheduledFlowRepository(database)
            val laterStateRef = StateRef(SecureHash.randomSHA256(), 0)
            val laterSsr = ScheduledStateRef(laterStateRef, laterTime)
            repo.merge(laterSsr)

            val earlierStateRef = StateRef(SecureHash.randomSHA256(), 0)
            val earlierSsr = ScheduledStateRef(earlierStateRef, mark)
            repo.merge(earlierSsr)

            val output = repo.getLatest(5).firstOrNull()
            assertEquals(output?.first, earlierStateRef)
            assertEquals(output?.second, earlierSsr)
        }
    }

    @Test
    fun `test that item is rescheduled`() {
        val laterTime = mark + 1.days
        val dataSourceProps = MockServices.makeTestDataSourceProperties()
        val database = configureDatabase(dataSourceProps, databaseConfig, { null }, { null })
        database.transaction {
            val repo = PersistentScheduledFlowRepository(database)
            val stateRef = StateRef(SecureHash.randomSHA256(), 0)
            val laterSsr = ScheduledStateRef(stateRef, laterTime)

            repo.merge(laterSsr)

            //Update the existing scheduled flow to an earlier time
            val updatedEarlierSsr = ScheduledStateRef(stateRef, mark)
            repo.merge(updatedEarlierSsr)

            val output = repo.getLatest(5).firstOrNull()
            assertEquals(output?.first, stateRef)
            assertEquals(output?.second, updatedEarlierSsr)

            repo.delete(output?.first!!)

            //There should be no more outputs
            val nextOutput = repo.getLatest(5).firstOrNull()
            assertNull(nextOutput)
        }
    }
}