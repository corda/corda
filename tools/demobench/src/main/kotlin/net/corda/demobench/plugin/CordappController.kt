/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.demobench.plugin

import net.corda.core.internal.*
import net.corda.demobench.model.HasCordapps
import net.corda.demobench.model.JVMConfig
import net.corda.demobench.model.NodeConfig
import net.corda.demobench.model.NodeConfigWrapper
import tornadofx.*
import java.io.IOException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.streams.toList

class CordappController : Controller() {

    private val jvm by inject<JVMConfig>()
    private val cordappDir: Path = jvm.applicationDir.resolve(NodeConfig.cordappDirName)
    private val finance: Path = cordappDir.resolve("corda-finance.jar")

    /**
     * Install any built-in cordapps that this node requires.
     */
    @Throws(IOException::class)
    fun populate(config: NodeConfigWrapper) {
        if (!config.cordappsDir.exists()) {
            config.cordappsDir.createDirectories()
        }
        if (finance.exists()) {
            finance.copyToDirectory(config.cordappsDir, StandardCopyOption.REPLACE_EXISTING)
            log.info("Installed 'Finance' cordapp")
        }
    }

    /**
     * Generates a stream of a node's non-built-in cordapps.
     */
    @Throws(IOException::class)
    fun useCordappsFor(config: HasCordapps): List<Path> {
        if (!config.cordappsDir.isDirectory()) return emptyList()
        return config.cordappsDir.walk(1) { paths ->
            paths.filter(Path::isCordapp)
                 .filter { !finance.endsWith(it.fileName) }
                 .toList()
        }
    }
}

val Path.isCordapp: Boolean get() = this.isReadable && this.fileName.toString().endsWith(".jar")
val Path.inCordappsDir: Boolean get() = (this.parent != null) && this.parent.endsWith("cordapps/")
