package net.corda.node

import com.google.common.base.Stopwatch
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.driver.driver
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.internal.toDatabaseSchemaName
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Test
import java.util.*
import java.util.concurrent.TimeUnit

@Ignore("Only use locally")
class NodeStartupPerformanceTests : IntegrationTest() {
    companion object {
        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(*listOf(ALICE_NAME, BOB_NAME, DUMMY_BANK_A_NAME, DUMMY_NOTARY_NAME)
                .map { it.toDatabaseSchemaName() }.toTypedArray())
    }
    // Measure the startup time of nodes. Note that this includes an RPC roundtrip, which causes e.g. Kryo initialisation.
    @Test
    fun `single node startup time`() {
        driver {
            val times = ArrayList<Long>()
            for (i in 1..10) {
                val time = Stopwatch.createStarted().apply {
                    startNode().get()
                }.stop().elapsed(TimeUnit.MICROSECONDS)
                times.add(time)
            }
            println(times.map { it / 1_000_000.0 })
        }
    }
}
