package net.corda.testing.node.internal

import io.github.classgraph.ClassGraph
import net.corda.core.internal.*
import net.corda.core.utilities.contextLogger
import net.corda.testing.node.TestCordapp
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProgressEvent
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.streams.toList

/**
 * Implementation of the public [TestCordapp] API.
 *
 * As described in [TestCordapp.findCordapp], this represents a single CorDapp jar on the current classpath. The [scanPackage] may
 * be for an external dependency to the project that's using this API, in which case that dependency jar is referenced as is. On the other hand,
 * the [scanPackage] may reference a gradle CorDapp project on the local system. In this scenerio the project's "jar" task is executed to
 * build the CorDapp jar. This allows us to inherit the CorDapp's MANIFEST information without having to do any extra processing.
 */
data class TestCordappImpl(val scanPackage: String, override val config: Map<String, Any>) : TestCordappInternal() {
    override fun withConfig(config: Map<String, Any>): TestCordappImpl = copy(config = config)

    override fun withOnlyJarContents(): TestCordappImpl = copy(config = emptyMap())

    override val jarFile: Path
        get() {
            val jars = TestCordappImpl.findJars(scanPackage)
            when (jars.size) {
                0 -> throw IllegalArgumentException("There are no CorDapps containing the package $scanPackage on the classpath. Make sure " +
                        "the package name is correct and that the CorDapp is added as a gradle dependency.")
                1 -> return jars.first()
                else -> throw IllegalArgumentException("There is more than one CorDapp containing the package $scanPackage on the classpath " +
                        "$jars. Specify a package name which is unique to the CorDapp.")
            }
        }

    companion object {
        private val packageToRootPaths = ConcurrentHashMap<String, Set<Path>>()
        private val projectRootToBuiltJar = ConcurrentHashMap<Path, Path>()
        private val log = contextLogger()

        fun findJars(scanPackage: String): Set<Path> {
            val rootPaths = findRootPaths(scanPackage)
            return if (rootPaths.all { it.toString().endsWith(".jar") }) {
                // We don't need to do anything more if all the root paths are jars
                rootPaths
            } else {
                // Otherwise we need to build those paths which are local projects and extract the built jar from them
                rootPaths.mapTo(HashSet()) { if (it.toString().endsWith(".jar")) it else buildCordappJar(it) }
            }
        }

        private fun findRootPaths(scanPackage: String): Set<Path> {
            return packageToRootPaths.computeIfAbsent(scanPackage) {
                val classGraph = ClassGraph().whitelistPaths(scanPackage.replace('.', '/'))
                classGraph.pooledScan().use {
                    it.allResources
                            .asSequence()
                            .map { it.classpathElementFile.toPath() }
                            .filterNot { it.toString().endsWith("-tests.jar") }
                            .map { if (it.toString().endsWith(".jar")) it else findProjectRoot(it) }
                            .toSet()
                }
            }
        }

        private fun findProjectRoot(path: Path): Path {
            var current = path
            while (true) {
                if ((current / "build.gradle").exists() || (current / "build.gradle.kts").exists()) {
                    return current
                }
                current = current.parent
            }
        }

        private fun buildCordappJar(projectRoot: Path): Path {
            val gradleLockFile = RandomAccessFile(File(System.getProperty("user.home"), "corda-gradle.lock"), "rw")
            return gradleLockFile.use {
                val lock = gradleLockFile.channel.lock()
                lock.use {
                    projectRootToBuiltJar.computeIfAbsent(projectRoot) {
                        log.info("Generating CorDapp jar from local project in $projectRoot ...")
                        runGradleBuild(projectRoot)

                        val libs = projectRoot / "build" / "libs"
                        val jars = libs.list { it.filter { it.toString().endsWith(".jar") }.toList() }
                                .sortedBy { it.attributes().creationTime() }
                        checkNotNull(jars.lastOrNull()) { "No jars were built in $libs" }
                    }
                }
            }
        }

        private fun runGradleBuild(projectRoot: Path) {
            val gradleConnector = GradleConnector.newConnector().apply {
                useBuildDistribution()
                forProjectDirectory(projectRoot.toFile())
            }

            val projectConnection = gradleConnector.connect()
            projectConnection.use {
                val build = projectConnection.newBuild().apply {
                    forTasks("jar")
                    addProgressListener { event: ProgressEvent ->
                        log.info(event.description)
                    }
                }
                // Blocks until the build is complete
                build.run()
            }
        }
    }
}
