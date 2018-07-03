package net.corda.bootstrapper.notaries

import net.corda.bootstrapper.nodes.CopiedNode
import net.corda.bootstrapper.nodes.FoundNode
import net.corda.bootstrapper.nodes.NodeCopier
import net.corda.bootstrapper.useAndClose
import org.slf4j.LoggerFactory
import java.io.File

class NotaryCopier(val cacheDir: File) : NodeCopier(cacheDir) {

    fun copyNotary(foundNotary: FoundNode): CopiedNotary {
        val nodeCacheDir = File(cacheDir, foundNotary.baseDirectory.name)
        nodeCacheDir.deleteRecursively()
        LOG.info("copying: ${foundNotary.baseDirectory} to $nodeCacheDir")
        foundNotary.baseDirectory.copyRecursively(nodeCacheDir, overwrite = true)
        copyNotaryBootstrapperFiles(nodeCacheDir)
        val configInCacheDir = File(nodeCacheDir, "node.conf")
        LOG.info("Applying precanned config " + configInCacheDir)
        val rpcSettings = getDefaultRpcSettings()
        val sshSettings = getDefaultSshSettings();
        mergeConfigs(configInCacheDir, rpcSettings, sshSettings, Mode.NOTARY)
        val generatedNodeInfo = generateNodeInfo(nodeCacheDir)
        return CopiedNode(foundNotary, configInCacheDir, nodeCacheDir).toNotary(generatedNodeInfo)
    }

    fun generateNodeInfo(dirToGenerateFrom: File): File {
        val nodeInfoGeneratorProcess = ProcessBuilder()
                .command(listOf("java", "-jar", "corda.jar", "--just-generate-node-info"))
                .directory(dirToGenerateFrom)
                .inheritIO()
                .start()

        val exitCode = nodeInfoGeneratorProcess.waitFor()
        if (exitCode != 0) {
            throw IllegalStateException("Failed to generate nodeInfo for notary: " + dirToGenerateFrom)
        }
        val nodeInfoFile = dirToGenerateFrom.listFiles().filter { it.name.startsWith("nodeInfo") }.single()
        return nodeInfoFile;
    }

    private fun copyNotaryBootstrapperFiles(nodeCacheDir: File) {
        this.javaClass.classLoader.getResourceAsStream("notary-Dockerfile").useAndClose { nodeDockerFileInStream ->
            val nodeDockerFile = File(nodeCacheDir, "Dockerfile")
            nodeDockerFile.outputStream().useAndClose { nodeDockerFileOutStream ->
                nodeDockerFileInStream.copyTo(nodeDockerFileOutStream)
            }
        }

        this.javaClass.classLoader.getResourceAsStream("run-corda-notary.sh").useAndClose { nodeRunScriptInStream ->
            val nodeRunScriptFile = File(nodeCacheDir, "run-corda.sh")
            nodeRunScriptFile.outputStream().useAndClose { nodeDockerFileOutStream ->
                nodeRunScriptInStream.copyTo(nodeDockerFileOutStream)
            }
        }

        this.javaClass.classLoader.getResourceAsStream("node_info_watcher.sh").useAndClose { nodeRunScriptInStream ->
            val nodeInfoWatcherFile = File(nodeCacheDir, "node_info_watcher.sh")
            nodeInfoWatcherFile.outputStream().useAndClose { nodeDockerFileOutStream ->
                nodeRunScriptInStream.copyTo(nodeDockerFileOutStream)
            }
        }
    }

    companion object {
        val LOG = LoggerFactory.getLogger(NotaryCopier::class.java)
    }


}