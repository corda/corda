package net.corda.testing.driver

import net.corda.testing.node.PortAllocationRunner
import org.assertj.core.util.Files
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.core.IsNot.not
import org.hamcrest.number.OrderingComparison
import org.junit.Assert
import org.junit.Test
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.util.concurrent.TimeUnit

class PortAllocationTest {

    @Test
    fun `should allocate a port whilst cycling back round if exceeding start of ephemeral range`() {
        val startingPoint = 10000
        val portAllocator = SharedMemoryPortAllocation.INSTANCE

        var previous = portAllocator.nextPort()
        (0 until 1000000).forEach { _ ->
            val next = portAllocator.nextPort()
            Assert.assertThat(next, `is`(not(previous)))
            Assert.assertThat(next, `is`(OrderingComparison.lessThan(SharedMemoryPortAllocation.FIRST_EPHEMERAL_PORT)))

            if (next == startingPoint) {
                Assert.assertThat(previous, `is`(SharedMemoryPortAllocation.FIRST_EPHEMERAL_PORT - 1))
            } else {
                Assert.assertThat(next, `is`(previous + 1))
            }
            previous = next
        }
    }

    @Test(timeout = 120_000)
    fun `should support multiprocess port allocation`() {

        println("Starting multiprocess port allocation test")
        val spinnerFile = Files.newTemporaryFile().also { it.deleteOnExit() }.absolutePath
        val process1 = buildJvmProcess(spinnerFile, 1)
        val process2 = buildJvmProcess(spinnerFile, 2)

        println("Started child processes")

        val processes = listOf(process1, process2)

        val spinnerBackingFile = RandomAccessFile(spinnerFile, "rw")
        println("Mapped spinner file")
        val spinnerBuffer = spinnerBackingFile.channel.map(FileChannel.MapMode.READ_WRITE, 0, 512)
        println("Created spinner buffer")

        var timeWaited = 0L

        while (spinnerBuffer.getShort(1) != 10.toShort() && spinnerBuffer.getShort(2) != 10.toShort() && timeWaited < 60_000) {
            println("Waiting to childProcesses to report back. waited ${timeWaited}ms")
            Thread.sleep(1000)
            timeWaited += 1000
        }

        //GO!
        println("Instructing child processes to start allocating ports")
        spinnerBuffer.putShort(0, 8)
        println("Waiting for child processes to terminate")
        processes.forEach { it.waitFor(1, TimeUnit.MINUTES) }
        println("child processes terminated")

        val process1Output = process1.inputStream.reader().readLines().toSet()
        val process2Output = process2.inputStream.reader().readLines().toSet()

        println("child process out captured")

        Assert.assertThat(process1Output.size, `is`(10_000))
        Assert.assertThat(process2Output.size, `is`(10_000))

        //there should be no overlap between the outputs as each process should have been allocated a unique set of ports
        Assert.assertThat(process1Output.intersect(process2Output), `is`(emptySet()))
    }

    private fun buildJvmProcess(spinnerFile: String, reportingIndex: Int): Process {
        val separator = System.getProperty("file.separator")
        val classpath = System.getProperty("java.class.path")
        val path = (System.getProperty("java.home")
                + separator + "bin" + separator + "java")
        val processBuilder = ProcessBuilder(path, "-cp",
                classpath,
                PortAllocationRunner::class.java.name,
                spinnerFile,
                reportingIndex.toString())

        return processBuilder.start()
    }
}



