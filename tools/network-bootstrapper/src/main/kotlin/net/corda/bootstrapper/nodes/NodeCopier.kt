package net.corda.bootstrapper.nodes

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValue
import org.slf4j.LoggerFactory
import java.io.File

open class NodeCopier(private val cacheDir: File) {


    fun copyNode(foundNode: FoundNode): CopiedNode {
        val nodeCacheDir = File(cacheDir, foundNode.baseDirectory.name)
        nodeCacheDir.deleteRecursively()
        LOG.info("copying: ${foundNode.baseDirectory} to $nodeCacheDir")
        foundNode.baseDirectory.copyRecursively(nodeCacheDir, overwrite = true)
        copyBootstrapperFiles(nodeCacheDir)
        val configInCacheDir = File(nodeCacheDir, "node.conf")
        LOG.info("Applying precanned config " + configInCacheDir)
        val rpcSettings = getDefaultRpcSettings()
        val sshSettings = getDefaultSshSettings();
        mergeConfigs(configInCacheDir, rpcSettings, sshSettings)
        return CopiedNode(foundNode, configInCacheDir, nodeCacheDir)
    }


    fun copyBootstrapperFiles(nodeCacheDir: File) {
        this.javaClass.classLoader.getResourceAsStream("node-Dockerfile").use { nodeDockerFileInStream ->
            val nodeDockerFile = File(nodeCacheDir, "Dockerfile")
            nodeDockerFile.outputStream().use { nodeDockerFileOutStream ->
                nodeDockerFileInStream.copyTo(nodeDockerFileOutStream)
            }
        }

        this.javaClass.classLoader.getResourceAsStream("run-corda-node.sh").use { nodeRunScriptInStream ->
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

    internal fun getDefaultRpcSettings(): ConfigValue {
        return javaClass
                .classLoader
                .getResourceAsStream("rpc-settings.conf")
                .reader().use {
            ConfigFactory.parseReader(it)
        }.getValue("rpcSettings")
    }

    internal fun getDefaultSshSettings(): ConfigValue {
        return javaClass
                .classLoader
                .getResourceAsStream("ssh.conf")
                .reader().use {
            ConfigFactory.parseReader(it)
        }.getValue("sshd")
    }

    internal fun mergeConfigs(configInCacheDir: File,
                              rpcSettings: ConfigValue,
                              sshSettings: ConfigValue,
                              mergeMode: Mode = Mode.NODE) {
        var trimmedConfig = ConfigFactory.parseFile(configInCacheDir)
                .withoutPath("compatibilityZoneURL")
                .withValue("rpcSettings", rpcSettings)
                .withValue("sshd", sshSettings)

        if (mergeMode == Mode.NODE) {
            trimmedConfig = trimmedConfig.withoutPath("p2pAddress")
        }

        configInCacheDir.outputStream().use {
            trimmedConfig.root().render(ConfigRenderOptions
                    .defaults()
                    .setOriginComments(false)
                    .setComments(false)
                    .setFormatted(true)
                    .setJson(false)).byteInputStream().copyTo(it)
        }
    }


    internal enum class Mode {
        NOTARY, NODE
    }


    companion object {
        val LOG = LoggerFactory.getLogger(NodeCopier::class.java)
    }
}

