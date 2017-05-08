package net.corda.node.internal

import net.corda.core.div
import net.corda.core.utilities.loggerFor
import java.io.RandomAccessFile
import java.lang.management.ManagementFactory
import java.nio.file.Path

/**
 * This class enforces that only a single node is running using the given [baseDirectory] by using a file lock.
 */
class EnforceSingleNodeIsRunning(val baseDirectory: Path) {
    private companion object {
        val log = loggerFor<EnforceSingleNodeIsRunning>()
    }

    fun start() {
        // Write out our process ID (which may or may not resemble a UNIX process id - to us it's just a string) to a
        // file that we'll do our best to delete on exit. But if we don't, it'll be overwritten next time. If it already
        // exists, we try to take the file lock first before replacing it and if that fails it means we're being started
        // twice with the same directory: that's a user error and we should bail out.
        val pidPath = baseDirectory / "process-id"
        val pidFile = pidPath.toFile()
        if (!pidFile.exists()) {
            pidFile.createNewFile()
        }
        pidFile.deleteOnExit()
        val pidFileRw = RandomAccessFile(pidFile, "rw")
        val pidFileLock = pidFileRw.channel.tryLock()
        if (pidFileLock == null) {
            log.error("It appears there is already a node running with the specified data directory $baseDirectory")
            log.error("Shut that other node down and try again. It may have process ID ${pidFile.readText()}")
            System.exit(1)
        }
        // Avoid the lock being garbage collected. We don't really need to release it as the OS will do so for us
        // when our process shuts down, but we try in stop() anyway just to be nice.
        Runtime.getRuntime().addShutdownHook(Thread {
            pidFileLock.release()
        })
        val ourProcessID: String = ManagementFactory.getRuntimeMXBean().name.split("@")[0]
        pidFileRw.setLength(0)
        pidFileRw.write(ourProcessID.toByteArray())
    }
}
