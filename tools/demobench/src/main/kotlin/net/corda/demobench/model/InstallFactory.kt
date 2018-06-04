/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.demobench.model

import com.typesafe.config.Config
import net.corda.core.internal.deleteRecursively
import net.corda.core.internal.div
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.config.UnknownConfigKeysPolicy
import net.corda.nodeapi.internal.config.parseAs
import tornadofx.*
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class InstallFactory : Controller() {
    private val nodeController by inject<NodeController>()

    @Throws(IOException::class)
    fun toInstallConfig(config: Config, baseDir: Path): InstallConfig {
        fun NetworkHostAndPort.checkPort() {
            require(nodeController.isPortValid(port)) { "Invalid port $port" }
        }

        val nodeConfig = config.parseAs<NodeConfig>(UnknownConfigKeysPolicy.IGNORE::handle)
        nodeConfig.p2pAddress.checkPort()
        nodeConfig.rpcAddress.checkPort()
        nodeConfig.webAddress.checkPort()

        val tempDir = Files.createTempDirectory(baseDir, ".node")

        return InstallConfig(tempDir, NodeConfigWrapper(tempDir, nodeConfig))
    }
}

/**
 * Wraps the configuration information for a Node
 * which isn't ready to be instantiated yet.
 */
class InstallConfig internal constructor(val baseDir: Path, private val config: NodeConfigWrapper) : HasCordapps {
    val key = config.key
    override val cordappsDir: Path = baseDir / "cordapps"

    fun deleteBaseDir(): Unit = baseDir.deleteRecursively()
    fun installTo(installDir: Path) = config.copy(baseDir = installDir)
}
