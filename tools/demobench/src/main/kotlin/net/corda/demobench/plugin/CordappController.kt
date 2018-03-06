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

import net.corda.core.internal.copyToDirectory
import net.corda.core.internal.createDirectories
import net.corda.core.internal.exists
import net.corda.demobench.model.HasCordapps
import net.corda.demobench.model.JVMConfig
import net.corda.demobench.model.NodeConfig
import net.corda.demobench.model.NodeConfigWrapper
import tornadofx.*
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.stream.Stream

class CordappController : Controller() {

    private val jvm by inject<JVMConfig>()
    private val cordappDir: Path = jvm.applicationDir.resolve(NodeConfig.cordappDirName)
    private val bankOfCorda: Path = cordappDir.resolve("bank-of-corda.jar")
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
        // Nodes cannot issue cash unless they contain the "Bank of Corda" cordapp.
        if (config.nodeConfig.issuableCurrencies.isNotEmpty() && bankOfCorda.exists()) {
            bankOfCorda.copyToDirectory(config.cordappsDir, StandardCopyOption.REPLACE_EXISTING)
            log.info("Installed 'Bank of Corda' cordapp")
        }
    }

    /**
     * Generates a stream of a node's non-built-in cordapps.
     */
    @Throws(IOException::class)
    fun useCordappsFor(config: HasCordapps): Stream<Path> = walkCordapps(config.cordappsDir)
            .filter { !bankOfCorda.endsWith(it.fileName) }
            .filter { !finance.endsWith(it.fileName) }

    private fun walkCordapps(cordappsDir: Path): Stream<Path> {
        return if (Files.isDirectory(cordappsDir))
            Files.walk(cordappsDir, 1).filter(Path::isCordapp)
        else
            Stream.empty()
    }

}

fun Path.isCordapp(): Boolean = Files.isReadable(this) && this.fileName.toString().endsWith(".jar")
fun Path.inCordappsDir(): Boolean = (this.parent != null) && this.parent.endsWith("cordapps/")
