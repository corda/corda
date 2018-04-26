package net.corda.bootstrapper.nodes

import com.github.dockerjava.core.command.BuildImageResultCallback
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.bootstrapper.docker.DockerUtils
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.parseAsNodeConfiguration
import org.slf4j.LoggerFactory
import java.io.File

open class NodeBuilder {

    companion object {
        val LOG = LoggerFactory.getLogger(NodeBuilder::class.java)
    }

    fun buildNode(copiedNode: CopiedNode): BuiltNode {
        val localDockerClient = DockerUtils.createLocalDockerClient()
        val copiedNodeConfig = copiedNode.copiedNodeConfig
        val nodeDir = copiedNodeConfig.parentFile
        if (!copiedNodeConfig.exists()) {
            throw IllegalStateException("There is no nodeConfig for dir: " + copiedNodeConfig)
        }
        val nodeConfig = ConfigFactory.parseFile(copiedNodeConfig)
        LOG.info("starting to build docker image for: " + nodeDir)
        val nodeImageId = localDockerClient.buildImageCmd()
                .withDockerfile(File(nodeDir, "Dockerfile"))
                .withBaseDirectory(nodeDir)
                .exec(BuildImageResultCallback()).awaitImageId()
        LOG.info("finished building docker image for: $nodeDir with id: $nodeImageId")
        val config = nodeConfig.parseAsNodeConfigWithFallback(ConfigFactory.parseFile(copiedNode.configFile))
        return copiedNode.builtNode(config, nodeImageId)
    }

}


fun Config.parseAsNodeConfigWithFallback(preCopyConfig: Config): NodeConfiguration {
    val nodeConfig = this
            .withValue("baseDirectory", ConfigValueFactory.fromAnyRef(""))
            .withFallback(ConfigFactory.parseResources("reference.conf"))
            .withFallback(preCopyConfig)
            .resolve()
    return nodeConfig.parseAsNodeConfiguration()
}

