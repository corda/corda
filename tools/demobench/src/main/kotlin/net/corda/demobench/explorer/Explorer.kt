/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.demobench.explorer

import net.corda.core.internal.copyTo
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.core.internal.list
import net.corda.core.utilities.contextLogger
import net.corda.demobench.model.JVMConfig
import net.corda.demobench.model.NodeConfig
import net.corda.demobench.model.NodeConfigWrapper
import net.corda.demobench.readErrorLines
import tornadofx.*
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.concurrent.Executors

class Explorer internal constructor(private val explorerController: ExplorerController) : AutoCloseable {
    private companion object {
        private val log = contextLogger()
    }

    private val executor = Executors.newSingleThreadExecutor()
    private var process: Process? = null

    @Throws(IOException::class)
    fun open(config: NodeConfigWrapper, onExit: (NodeConfigWrapper) -> Unit) {
        val explorerDir = config.explorerDir

        try {
            explorerDir.createDirectories()
        } catch (e: IOException) {
            log.warn("Failed to create working directory '{}'", explorerDir.toAbsolutePath())
            onExit(config)
            return
        }

        val legalName = config.nodeConfig.myLegalName
        try {
            installApps(config)

            val user = config.nodeConfig.rpcUsers[0]
            val p = explorerController.process(
                    "--host=localhost",
                    "--port=${config.nodeConfig.rpcSettings.address.port}",
                    "--username=${user.username}",
                    "--password=${user.password}")
                    .directory(explorerDir.toFile())
                    .start()
            process = p

            log.info("Launched Node Explorer for '{}'", legalName)

            // Close these streams because no-one is using them.
            safeClose(p.outputStream)
            safeClose(p.inputStream)

            executor.submit {
                val exitValue = p.waitFor()
                val errors = p.readErrorLines()
                process = null

                if (errors.isEmpty()) {
                    log.info("Node Explorer for '{}' has exited (value={})", legalName, exitValue)
                } else {
                    log.error("Node Explorer for '{}' has exited (value={}, {})", legalName, exitValue, errors)
                }

                onExit(config)
            }
        } catch (e: IOException) {
            log.error("Failed to launch Node Explorer for '{}': {}", legalName, e.message)
            onExit(config)
            throw e
        }
    }

    private fun installApps(config: NodeConfigWrapper) {
        // Make sure that the explorer has cordapps on its class path. This is only necessary because currently apps
        // require the original class files to deserialise states: Kryo serialisation doesn't let us write generic
        // tools that work with serialised data structures. But the AMQP serialisation revamp will fix this by
        // integrating the class carpenter, so, we can eventually get rid of this function.
        //
        // Note: does not copy dependencies because we should soon be making all apps fat jars and dependencies implicit.
        //
        // TODO: Remove this code when serialisation has been upgraded.
        val cordappsDir = config.explorerDir / NodeConfig.cordappDirName
        cordappsDir.createDirectories()
        config.cordappsDir.list {
            it.forEachOrdered { path ->
                val destPath = cordappsDir / path.fileName.toString()
                try {
                    // Try making a symlink to make things faster and use less disk space.
                    Files.createSymbolicLink(destPath, path)
                } catch (e: UnsupportedOperationException) {
                    // OS doesn't support symbolic links?
                    path.copyTo(destPath, REPLACE_EXISTING)
                } catch (e: java.nio.file.FileAlreadyExistsException) {
                    // OK, don't care ...
                } catch (e: IOException) {
                    // Windows 10 might not allow this user to create a symlink
                    log.warn("Failed to create symlink '{}' for '{}': {}", destPath, path, e.message)
                    path.copyTo(destPath, REPLACE_EXISTING)
                }
            }
        }
    }

    override fun close() {
        executor.shutdown()
        process?.destroy()
    }

    private fun safeClose(c: AutoCloseable) {
        try {
            c.close()
        } catch (e: Exception) {
            log.error("Failed to close stream: '{}'", e.message)
        }
    }

}

class ExplorerController : Controller() {
    private val jvm by inject<JVMConfig>()
    private val explorerPath: Path = jvm.applicationDir.resolve("explorer").resolve("node-explorer.jar")

    init {
        log.info("Explorer JAR: $explorerPath")
    }

    internal fun process(vararg args: String) = jvm.processFor(explorerPath, *args)

    fun explorer() = Explorer(this)
}