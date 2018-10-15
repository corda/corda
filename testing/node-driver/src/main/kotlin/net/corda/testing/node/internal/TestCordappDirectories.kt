package net.corda.testing.node.internal

import net.corda.core.crypto.sha256
import net.corda.core.internal.createDirectories
import net.corda.core.internal.deleteRecursively
import net.corda.core.internal.div
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
            val cordappDir = (cordappsDirectory / UUID.randomUUID().toString()).createDirectories()
            val uniqueScanString = if (cordapp.packages.size == 1 && cordapp.classes.isEmpty()) {
                cordapp.packages.first()
            } else {
                "${cordapp.packages}${cordapp.classes.joinToString { it.name }}".toByteArray().sha256().toString()
            }
            val jarFileName = cordapp.run { "${name}_${vendor}_${title}_${version}_${targetVersion}_$uniqueScanString.jar".replace(whitespace, "-") }
            val jarFile = cordappDir / jarFileName
            cordapp.packageAsJar(jarFile)
            logger.debug { "$cordapp packaged into $jarFile" }
            cordappDir
        }
    }

    private val defaultCordappsDirectory: Path by lazy {
        val cordappsDirectory = (Paths.get("build") / "tmp" / getTimestampAsDirectoryName() / "generated-test-cordapps").toAbsolutePath()
        logger.info("Initialising generated test CorDapps directory in $cordappsDirectory")
        cordappsDirectory.toFile().deleteOnExit()
        cordappsDirectory.deleteRecursively()
        cordappsDirectory.createDirectories()
    }
}
