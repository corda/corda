package net.corda.networkbuilder.notaries

import net.corda.core.internal.div
import net.corda.networkbuilder.nodes.CopiedNode
import net.corda.networkbuilder.nodes.FoundNode
import net.corda.networkbuilder.nodes.NodeCopier
import org.slf4j.LoggerFactory
import java.io.File

class NotaryCopier(private val cacheDir: File) : NodeCopier(cacheDir) {

    fun copyNotary(foundNotary: FoundNode): CopiedNotary {
        val nodeCacheDir = File(cacheDir, foundNotary.baseDirectory.name)
        nodeCacheDir.deleteRecursively()
        LOG.info("copying: ${foundNotary.baseDirectory} to $nodeCacheDir")
        foundNotary.baseDirectory.copyRecursively(nodeCacheDir, overwrite = true)
        //docker-java lib doesn't copy an empty folder, so if it's empty add a dummy file
        ensureDirectoryIsNonEmpty(nodeCacheDir.toPath()/("cordapps"))
        copyNotaryBootstrapperFiles(nodeCacheDir)
        val configInCacheDir = File(nodeCacheDir, "node.conf")
        LOG.info("Applying precanned config $configInCacheDir")
        val rpcSettings = getDefaultRpcSettings()
        val sshSettings = getDefaultSshSettings()
        mergeConfigs(configInCacheDir, rpcSettings, sshSettings, Mode.NOTARY)
        val generatedNodeInfo = generateNodeInfo(nodeCacheDir)
        return CopiedNode(foundNotary, configInCacheDir, nodeCacheDir).toNotary(generatedNodeInfo)
    }

    fun generateNodeInfo(dirToGenerateFrom: File): File {
        val nodeInfoGeneratorProcess = ProcessBuilder()
                .command(listOf("java", "-jar", "corda.jar", "generate-node-info"))
                .directory(dirToGenerateFrom)
                .inheritIO()
                .start()

        val exitCode = nodeInfoGeneratorProcess.waitFor()
        if (exitCode != 0) {
            throw IllegalStateException("Failed to generate nodeInfo for notary: $dirToGenerateFrom")
        }
        return dirToGenerateFrom.listFiles().single { it.name.startsWith("nodeInfo") }
    }

    private fun copyNotaryBootstrapperFiles(nodeCacheDir: File) {
        this.javaClass.classLoader.getResourceAsStream("notary-Dockerfile").use { nodeDockerFileInStream ->
            val nodeDockerFile = File(nodeCacheDir, "Dockerfile")
            nodeDockerFile.outputStream().use { nodeDockerFileOutStream ->
                nodeDockerFileInStream.copyTo(nodeDockerFileOutStream)
            }
        }

        this.javaClass.classLoader.getResourceAsStream("run-corda-notary.sh").use { nodeRunScriptInStream ->
            val nodeRunScriptFile = File(nodeCacheDir, "run-corda.sh")
            nodeRunScriptFile.outputStream().use { nodeDockerFileOutStream ->
                nodeRunScriptInStream.copyTo(nodeDockerFileOutStream)
            }
        }

        this.javaClass.classLoader.getResourceAsStream("node_info_watcher.sh").use { nodeRunScriptInStream ->
            val nodeInfoWatcherFile = File(nodeCacheDir, "node_info_watcher.sh")
            nodeInfoWatcherFile.outputStream().use { nodeDockerFileOutStream ->
                nodeRunScriptInStream.copyTo(nodeDockerFileOutStream)
            }
        }
    }

    companion object {
        val LOG = LoggerFactory.getLogger(NotaryCopier::class.java)
    }
}