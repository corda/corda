package net.corda.testing.node.internal

import net.corda.core.internal.createDirectories
import net.corda.core.internal.deleteRecursively
import net.corda.core.internal.div
import net.corda.core.internal.exists
import net.corda.testing.driver.TestCorDapp
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

internal object TestCordappDirectories {

    private const val whitespace = " "
    private const val whitespaceReplacement = "_"

    private val cordappsCache: ConcurrentMap<List<String>, Path> = ConcurrentHashMap<List<String>, Path>()

    internal fun cached(cordapps: Iterable<TestCorDapp>, cordappsDirectory: Path = defaultCordappsDirectory): Iterable<Path> {

        cordappsDirectory.toFile().deleteOnExit()
        return cordapps.map { cached(it, cordappsDirectory) }
    }

    internal fun cached(cordapp: TestCorDapp, cordappsDirectory: Path = defaultCordappsDirectory): Path {

        cordappsDirectory.toFile().deleteOnExit()
        return cordappsCache.getOrPut(cordapp.resources.map { it.toExternalForm() }.sorted()) {

            val cordappDirectory = (cordappsDirectory / "${cordapp.name}_${cordapp.version}".replace(whitespace, whitespaceReplacement)).toAbsolutePath()
            cordappDirectory.createDirectories()
            cordappDirectory.toFile().deleteOnExit()
            cordapp.packageAsJarInDirectory(cordappDirectory)
            cordappDirectory
        }
    }

    internal fun forPackages(packages: Iterable<String>, cordappsDirectory: Path = defaultCordappsDirectory): Iterable<Path> {

        cordappsDirectory.toFile().deleteOnExit()
        val cordapps = simplifyScanPackages(packages).distinct().fold(emptySet<TestCorDapp>()) { all, packageName -> all + testCorDapp(packageName) }
        return cached(cordapps, cordappsDirectory)
    }

    private val defaultCordappsDirectory: Path by lazy {

        val cordappsDirectory = Paths.get("build") / "tmp" / getTimestampAsDirectoryName() / "generated-test-cordapps"
        if (cordappsDirectory.exists()) {
            cordappsDirectory.deleteRecursively()
        }
        cordappsDirectory.createDirectories()
        cordappsDirectory
    }
}