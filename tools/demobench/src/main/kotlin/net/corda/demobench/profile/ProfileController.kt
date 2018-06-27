/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.demobench.profile

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import javafx.stage.FileChooser
import javafx.stage.FileChooser.ExtensionFilter
import net.corda.core.internal.*
import net.corda.demobench.model.*
import net.corda.demobench.plugin.CordappController
import net.corda.demobench.plugin.inCordappsDir
import net.corda.demobench.plugin.isCordapp
import tornadofx.*
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.function.BiPredicate
import java.util.logging.Level
import java.util.stream.StreamSupport

class ProfileController : Controller() {

    private val jvm by inject<JVMConfig>()
    private val baseDir: Path = jvm.dataHome
    private val nodeController by inject<NodeController>()
    private val cordappController by inject<CordappController>()
    private val installFactory by inject<InstallFactory>()
    private val chooser = FileChooser()

    init {
        chooser.title = "DemoBench Profiles"
        chooser.initialDirectory = baseDir.toFile()
        chooser.extensionFilters.add(ExtensionFilter("DemoBench profiles (*.profile)", "*.profile", "*.PROFILE"))
    }

    /**
     * Saves the active node configurations into a ZIP file, along with their Cordapps.
     */
    @Throws(IOException::class)
    fun saveProfile(): Boolean {
        val target = forceExtension(chooser.showSaveDialog(null) ?: return false, ".profile")
        log.info("Saving profile as: $target")

        val configs = nodeController.activeNodes

        // Delete the profile, if it already exists. The save
        // dialogue has already confirmed that this is OK.
        target.delete()

        // Write the profile as a ZIP file.
        try {
            FileSystems.newFileSystem(URI.create("jar:" + target.toURI()), mapOf("create" to "true")).use { fs ->
                configs.forEach { config ->
                    // Write the configuration file.
                    val nodeDir = fs.getPath(config.key).createDirectories()
                    val file = (nodeDir / "node.conf").writeText(config.nodeConfig.serialiseAsString())
                    log.info("Wrote: $file")

                    // Write all of the non-built-in cordapps.
                    val cordappDir = (nodeDir / NodeConfig.cordappDirName).createDirectory()
                    cordappController.useCordappsFor(config).forEach {
                        val cordapp = it.copyToDirectory(cordappDir)
                        log.info("Wrote: $cordapp")
                    }
                }
            }

            log.info("Profile saved.")
        } catch (e: IOException) {
            log.log(Level.SEVERE, "Failed to save profile '$target': '${e.message}'", e)
            target.delete()
            throw e
        }

        return true
    }

    private fun forceExtension(target: File, ext: String): File {
        return if (target.extension.isEmpty()) File(target.parent, target.name + ext) else target
    }

    /**
     * Parses a profile (ZIP) file.
     */
    @Throws(IOException::class)
    fun openProfile(): List<InstallConfig>? {
        val chosen = chooser.showOpenDialog(null) ?: return null
        log.info("Selected profile: $chosen")

        val configs = LinkedList<InstallConfig>()

        FileSystems.newFileSystem(chosen.toPath(), null).use { fs ->
            // Identify the nodes first...
            StreamSupport.stream(fs.rootDirectories.spliterator(), false)
                    .flatMap { Files.find(it, 2, BiPredicate { p, attr -> "node.conf" == p?.fileName.toString() && attr.isRegularFile }) }
                    .map { file ->
                        try {
                            val config = installFactory.toInstallConfig(parse(file), baseDir)
                            log.info("Loaded: $file")
                            config
                        } catch (e: Exception) {
                            log.log(Level.SEVERE, "Failed to parse '$file': ${e.message}", e)
                            throw e
                        }
                        // Java seems to "walk" through the ZIP file backwards.
                        // So add new config to the front of the list, so that
                        // our final list is ordered to match the file.
                    }.forEach { configs.addFirst(it) }

            val nodeIndex = configs.map { it.key to it }.toMap()

            // Now extract all of the plugins from the ZIP file,
            // and copy them to a temporary location.
            StreamSupport.stream(fs.rootDirectories.spliterator(), false)
                    .flatMap { Files.find(it, 3, BiPredicate { p, attr -> p.inCordappsDir && p.isCordapp && attr.isRegularFile }) }
                    .forEach { cordapp ->
                        val config = nodeIndex[cordapp.getName(0).toString()] ?: return@forEach

                        try {
                            val cordappDir = config.cordappsDir.createDirectories()
                            cordapp.copyToDirectory(cordappDir)
                            log.info("Loaded: $cordapp")
                        } catch (e: Exception) {
                            log.log(Level.SEVERE, "Failed to extract '$cordapp': ${e.message}", e)
                            configs.forEach { c -> c.deleteBaseDir() }
                            throw e
                        }
                    }
        }

        return configs
    }

    private fun parse(path: Path): Config = path.reader().use { ConfigFactory.parseReader(it) }
}
