package net.corda.testing.node.internal

import com.typesafe.config.ConfigValueFactory
import net.corda.core.crypto.sha256
import net.corda.core.internal.*
import net.corda.core.utilities.debug
import net.corda.core.utilities.loggerFor
import net.corda.testing.core.JarSignatureTestUtils.signJar
import net.corda.testing.core.JarSignatureTestUtils.generateKey
import net.corda.testing.node.TestCordapp
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object TestCordappDirectories {
    private val logger = loggerFor<TestCordappDirectories>()

    private val whitespace = "\\s".toRegex()

    private val testCordappsCache = ConcurrentHashMap<TestCordappImpl, Path>()

    fun getJarDirectory(cordapp: TestCordapp, cordappsDirectory: Path = defaultCordappsDirectory, signJar: Boolean = false): Path {
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
            if (signJar) {
                val testKeystore = "_teststore"
                val alias = "Test"
                val pwd = "secret!"
                if (!(cordappsDirectory / testKeystore).exists()) {
                    cordappsDirectory.generateKey(alias, pwd, "O=Test Company Ltd,OU=Test,L=London,C=GB")
                }
                (cordappsDirectory / testKeystore).copyTo(cordappDir / testKeystore)
                cordappDir.signJar("$filename.jar", alias, pwd)
            }
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
