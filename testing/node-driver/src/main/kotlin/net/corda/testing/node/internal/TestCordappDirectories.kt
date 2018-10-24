package net.corda.testing.node.internal

import com.typesafe.config.ConfigValueFactory
import net.corda.core.crypto.sha256
import net.corda.core.internal.createDirectories
import net.corda.core.internal.deleteRecursively
import net.corda.core.internal.div
import net.corda.core.internal.writeText
import net.corda.core.utilities.debug
import net.corda.core.utilities.loggerFor
import net.corda.testing.node.TestCordapp
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object TestCordappDirectories {
    private val logger = loggerFor<TestCordappDirectories>()

    private val whitespace = "\\s".toRegex()

    private val testCordappsCache = ConcurrentHashMap<TestCordappImpl, Path>()

    fun getJarDirectory(cordapp: TestCordapp, cordappsDirectory: Path = defaultCordappsDirectory): Path {
        cordapp as TestCordappImpl
        return testCordappsCache.computeIfAbsent(cordapp) {
            val configString = ConfigValueFactory.fromMap(cordapp.config).toConfig().root().render()
            val filename = cordapp.run {
                val uniqueScanString = if (packages.size == 1 && classes.isEmpty() && config.isEmpty()) {
                    packages.first()
                } else {
                    "$packages$classes$configString".toByteArray().sha256().toString()
                }
                "${name}_${vendor}_${title}_${version}_${targetVersion}_$uniqueScanString".replace(whitespace, "-")
            }
            val cordappDir = cordappsDirectory / UUID.randomUUID().toString()
            val configDir = (cordappDir / "config").createDirectories()
            val jarFile = cordappDir / "$filename.jar"
            cordapp.packageAsJar(jarFile)
            (configDir / "$filename.conf").writeText(configString)
            logger.debug { "$cordapp packaged into $jarFile" }
            cordappDir
        }
    }

    private val defaultCordappsDirectory: Path by lazy {
        val cordappsDirectory = Paths.get("build").toAbsolutePath() / "generated-test-cordapps" / getTimestampAsDirectoryName()
        logger.info("Initialising generated test CorDapps directory in $cordappsDirectory")
        cordappsDirectory.deleteRecursively()
        cordappsDirectory.createDirectories()
    }
}
