package net.corda.services.messaging

import net.corda.node.driver.driver
import org.junit.Test
import java.nio.file.Paths
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class ArtemisMessagingServerTest {
    @Test
    fun `network map will work after restart`() {
        val dir = Paths.get("build", getTimestampAsDirectoryName())
        // Start the network map.
        driver(driverDirectory = dir) {
            arrayOf(startNode("NodeA"), startNode("NodeB"), startNode("Notary")).forEach { it.get() }
        }
        // Start the network map second time, this will restore message queues from the journal.
        // This will hang and fail prior the fix. https://github.com/corda/corda/issues/37
        driver(driverDirectory = dir) {
            arrayOf(startNode("NodeA"), startNode("NodeB"), startNode("Notary")).forEach { it.get(5, TimeUnit.MINUTES) }
        }
    }

    private fun getTimestampAsDirectoryName(): String {
        return DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC).format(Instant.now())
    }
}