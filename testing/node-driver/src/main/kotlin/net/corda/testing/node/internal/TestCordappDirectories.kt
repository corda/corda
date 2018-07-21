package net.corda.testing.node.internal

import net.corda.core.internal.createDirectories
import net.corda.core.internal.deleteRecursively
import net.corda.core.internal.div
import net.corda.core.internal.exists
import net.corda.core.utilities.loggerFor
import net.corda.testing.driver.TestCorDapp
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

internal object TestCordappDirectories {

    private val logger = loggerFor<TestCordappDirectories>()

    private const val whitespace = " "
    private const val whitespaceReplacement = "_"

    private val cordappsCache: ConcurrentMap<List<String>, Path> = ConcurrentHashMap<List<String>, Path>()

    internal fun cached(cordapps: Iterable<TestCorDapp>, replaceExistingOnes: Boolean = false, cordappsDirectory: Path = defaultCordappsDirectory): Iterable<Path> {

        cordappsDirectory.toFile().deleteOnExit()
        return cordapps.map { cached(it, replaceExistingOnes, cordappsDirectory) }
    }

    internal fun cached(cordapp: TestCorDapp, replaceExistingOnes: Boolean = false, cordappsDirectory: Path = defaultCordappsDirectory): Path {

        cordappsDirectory.toFile().deleteOnExit()
        val cacheKey = cordapp.resources.map { it.toExternalForm() }.sorted()
        return if (replaceExistingOnes) {
            cordappsCache.remove(cacheKey)
            cordappsCache.getOrPut(cacheKey) {

                val cordappDirectory = (cordappsDirectory / "${cordapp.name}_${cordapp.version}".replace(whitespace, whitespaceReplacement)).toAbsolutePath()
                cordappDirectory.createDirectories()
                cordapp.packageAsJarInDirectory(cordappDirectory)
                cordappDirectory
            }
        } else {
            cordappsCache.getOrPut(cacheKey) {

                val cordappDirectory = (cordappsDirectory / "${cordapp.name}_${cordapp.version}".replace(whitespace, whitespaceReplacement)).toAbsolutePath()
                cordappDirectory.createDirectories()
                cordapp.packageAsJarInDirectory(cordappDirectory)
                cordappDirectory
            }
        }
    }

    internal fun forPackages(packages: Iterable<String>, replaceExistingOnes: Boolean = false, cordappsDirectory: Path = defaultCordappsDirectory): Iterable<Path> {

        cordappsDirectory.toFile().deleteOnExit()
        val cordapps = simplifyScanPackages(packages).distinct().fold(emptySet<TestCorDapp>()) { all, packageName -> all + testCorDapp(packageName) }
        return cached(cordapps, replaceExistingOnes, cordappsDirectory)
    }

    private val defaultCordappsDirectory: Path by lazy {

        val cordappsDirectory = (Paths.get("build") / "tmp" / getTimestampAsDirectoryName() / "generated-test-cordapps").toAbsolutePath()
        logger.info("Initialising generated test CorDapps directory in $cordappsDirectory")
        if (cordappsDirectory.exists()) {
            cordappsDirectory.deleteRecursively()
        }
        cordappsDirectory.createDirectories()
        cordappsDirectory
    }
}