package net.corda.testing.driver

import net.corda.core.utilities.Try
import net.corda.core.utilities.contextLogger
import net.corda.testing.node.PortAllocationRunner
import org.assertj.core.util.Files
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.core.IsNot.not
import org.hamcrest.number.OrderingComparison
import org.junit.Assert
import org.junit.Assume.assumeFalse
import org.junit.Test
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.util.concurrent.TimeUnit
import kotlin.streams.toList

class PortAllocationTest {

    companion object {
        val logger = contextLogger()
    }
    
    @Test
    fun `should allocate a port whilst cycling back round if exceeding start of ephemeral range`() {
        val startingPoint = PortAllocation.DEFAULT_START_PORT
        val portAllocator = PortAllocation.defaultAllocator

        var previous = portAllocator.nextPort()
        (0 until 50_000).forEach { _ ->
            val next = portAllocator.nextPort()
            Assert.assertThat(next, `is`(not(previous)))
            Assert.assertThat(next, `is`(OrderingComparison.lessThan(PortAllocation.FIRST_EPHEMERAL_PORT)))

            if (next == startingPoint) {
                Assert.assertThat(previous, `is`(PortAllocation.FIRST_EPHEMERAL_PORT - 1))
            } else {
                Assert.assertTrue(next >= previous + 1)
            }
            previous = next
        }
    }

    @Test(timeout = 120_000)
    fun `should support multiprocess port allocation`() {
        assumeFalse(System.getProperty("os.name").toLowerCase().contains("windows"))

        logger.info("Starting multiprocess port allocation test")
        val spinnerFile = Files.newTemporaryFile().also { it.deleteOnExit() }.absolutePath
        val iterCount = 8_000 // Default port range 10000-30000 since we will have 2 processes we want to make sure there is enough leg room
                              // If we rollover, we may well receive the ports that were already given to a different process
        val process1 = buildJvmProcess(spinnerFile, 1, iterCount)
        val process2 = buildJvmProcess(spinnerFile, 2, iterCount)

        logger.info("Started child processes")

        val processes = listOf(process1, process2)

        val spinnerBackingFile = RandomAccessFile(spinnerFile, "rw")
        logger.info("Mapped spinner file at: $spinnerFile")
        val spinnerBuffer = spinnerBackingFile.channel.map(FileChannel.MapMode.READ_WRITE, 0, 512)
        logger.info("Created spinner buffer")

        var timeWaited = 0L

        while (spinnerBuffer.getShort(1) != 10.toShort() && spinnerBuffer.getShort(2) != 10.toShort() && timeWaited < 60_000) {
            logger.info("Waiting to childProcesses to report back. waited ${timeWaited}ms")
            Thread.sleep(1000)
            timeWaited += 1000
        }

        //GO!
        logger.info("Instructing child processes to start allocating ports")
        spinnerBuffer.putShort(0, 8)
        logger.info("Waiting for child processes to terminate")
        val terminationStatuses = processes.parallelStream().map { if(it.waitFor(1, TimeUnit.MINUTES)) "OK" else "STILL RUNNING" }.toList()
        logger.info("child processes terminated: $terminationStatuses")

        fun List<String>.setOfPorts() : Set<Int> {
            // May include warnings when ports are busy
            return map { Try.on { Integer.parseInt(it)} }.filter { it.isSuccess }.map { it.getOrThrow() }.toSet()
        }

        val lines1 = process1.inputStream.reader().readLines()
        val portsAllocated1 = lines1.setOfPorts()
        val lines2 = process2.inputStream.reader().readLines()
        val portsAllocated2 = lines2.setOfPorts()

        logger.info("child process out captured")

        Assert.assertThat(lines1.joinToString(), portsAllocated1.size, `is`(iterCount))
        Assert.assertThat(lines2.joinToString(), portsAllocated2.size, `is`(iterCount))

        //there should be no overlap between the outputs as each process should have been allocated a unique set of ports
        val intersect = portsAllocated1.intersect(portsAllocated2)
        Assert.assertThat(intersect.joinToString(), intersect, `is`(emptySet()))
    }

    private fun buildJvmProcess(spinnerFile: String, reportingIndex: Int, iterCount: Int): Process {
        val separator = System.getProperty("file.separator")
        val classpath = System.getProperty("java.class.path")
        val path = (System.getProperty("java.home")
                + separator + "bin" + separator + "java")
        val processBuilder = ProcessBuilder(path, "-cp",
                classpath,
                PortAllocationRunner::class.java.name,
                spinnerFile,
                reportingIndex.toString(),
                iterCount.toString())

        return processBuilder.start()
    }
}