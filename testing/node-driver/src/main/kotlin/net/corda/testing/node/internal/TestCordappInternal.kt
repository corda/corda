package net.corda.testing.node.internal

import com.typesafe.config.ConfigValueFactory
import net.corda.core.internal.copyToDirectory
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.core.internal.writeText
import net.corda.testing.node.TestCordapp
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Path

/**
 * Extends the public [TestCordapp] API with internal extensions for use within the testing framework and for internal testing of the platform.
 *
 * @property jarFile The jar file this CorDapp represents. Different CorDapps may point to the same file.
 */
abstract class TestCordappInternal : TestCordapp() {
    abstract val jarFile: Path

    /** Return a copy of this TestCordappInternal but without any metadata, such as configs and signing information. */
    abstract fun withOnlyJarContents(): TestCordappInternal

    companion object {
        fun installCordapps(baseDirectory: Path,
                            nodeSpecificCordapps: Set<TestCordappInternal>,
                            generalCordapps: Set<TestCordappInternal> = emptySet()) {
            val nodeSpecificCordappsWithoutMeta = checkNoConflicts(nodeSpecificCordapps)
            checkNoConflicts(generalCordapps)

            // Precedence is given to node-specific CorDapps
            val allCordapps = nodeSpecificCordapps + generalCordapps.filter { it.withOnlyJarContents() !in nodeSpecificCordappsWithoutMeta }
            // Ignore any duplicate jar files
            val jarToCordapp  = allCordapps.associateBy { it.jarFile }

            val cordappsDir = baseDirectory / "cordapps"
            val configDir = (cordappsDir / "config").createDirectories()

            jarToCordapp.forEach { jar, cordapp ->
                try {
                    jar.copyToDirectory(cordappsDir)
                } catch (e: FileAlreadyExistsException) {
                    // Ignore if the node already has the same CorDapp jar. This can happen if the node is being restarted.
                }
                val configString = ConfigValueFactory.fromMap(cordapp.config).toConfig().root().render()
                (configDir / "${jar.fileName.toString().removeSuffix(".jar")}.conf").writeText(configString)
            }
        }

        private fun checkNoConflicts(cordapps: Set<TestCordappInternal>): Set<TestCordappInternal> {
            val cordappsWithoutMeta = cordapps.groupBy { it.withOnlyJarContents() }
            cordappsWithoutMeta.forEach { require(it.value.size == 1) { "Conflicting CorDapps specified: ${it.value}" } }
            return cordappsWithoutMeta.keys
        }
    }
}
