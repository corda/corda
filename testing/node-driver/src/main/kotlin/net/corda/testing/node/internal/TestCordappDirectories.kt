package net.corda.testing.node.internal

import net.corda.core.internal.createDirectories
import net.corda.core.internal.deleteRecursively
import net.corda.core.internal.div
import net.corda.core.internal.exists
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

internal object TestCordappDirectories {

    private val cache: ConcurrentMap<String, Path> = ConcurrentHashMap<String, Path>()

    internal fun forPackages(packages: Iterable<String>, cordappsDirectory: Path = defaultCordappsDirectory): Set<Path> {

        return simplifyScanPackages(packages).distinct().fold(emptySet()) { all, packageName -> all + testCorDappLocation(packageName, cordappsDirectory) }
    }

    internal fun forPackages(firstPackage: String, vararg otherPackages: String, rootDirectory: Path = defaultCordappsDirectory): Set<Path> {

        return forPackages(setOf(*otherPackages) + firstPackage, rootDirectory)
    }

    private fun testCorDappLocation(packageName: String, cordappsDirectory: Path): Path {

        return cache.getOrPut(packageName) {

            testCorDapp(packageName).packageAsJarInDirectory(cordappsDirectory)
        }
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