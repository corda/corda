package net.corda.networkbuilder.nodes

import com.github.dockerjava.api.model.BuildResponseItem
import com.github.dockerjava.core.async.ResultCallbackTemplate
import com.github.dockerjava.core.command.BuildImageResultCallback
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.networkbuilder.docker.DockerUtils
import net.corda.common.configuration.parsing.internal.Configuration
import net.corda.common.validation.internal.Validated
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.parseAsNodeConfiguration
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.CompletableFuture

open class NodeBuilder {

    companion object {
        val LOG = LoggerFactory.getLogger(NodeBuilder::class.java)
    }

    fun buildNode(copiedNode: CopiedNode): CompletableFuture<BuiltNode> {

        val future: CompletableFuture<BuiltNode> = CompletableFuture()

        val localDockerClient = DockerUtils.createLocalDockerClient()
        val copiedNodeConfig = copiedNode.copiedNodeConfig
        val nodeDir = copiedNodeConfig.parentFile
        if (!copiedNodeConfig.exists()) {
            throw IllegalStateException("There is no nodeConfig for dir: $copiedNodeConfig")
        }
        val nodeConfig = ConfigFactory.parseFile(copiedNodeConfig)
        LOG.info("starting to build docker image for: $nodeDir")
        localDockerClient.buildImageCmd()
                .withDockerfile(File(nodeDir, "Dockerfile"))
                .withBaseDirectory(nodeDir).exec(object : ResultCallbackTemplate<BuildImageResultCallback, BuildResponseItem>() {
                    var result: BuildResponseItem? = null
                    override fun onNext(`object`: BuildResponseItem?) {
                        this.result = `object`
                    }

                    override fun onError(throwable: Throwable?) {
                        future.completeExceptionally(throwable)
                    }

                    override fun onComplete() {
                        super.onComplete()
                        LOG.info("finished building docker image for: $nodeDir with id: ${result?.imageId}")
                        val config = nodeConfig.parseAsNodeConfigWithFallback(ConfigFactory.parseFile(copiedNode.configFile)).value()
                        future.complete(copiedNode.builtNode(config, result?.imageId!!))
                    }
                })
        return future
    }
}

fun Config.parseAsNodeConfigWithFallback(preCopyConfig: Config): Validated<NodeConfiguration, Configuration.Validation.Error> {
    val nodeConfig = this
            .withValue("baseDirectory", ConfigValueFactory.fromAnyRef(""))
            .withFallback(ConfigFactory.parseResources("reference.conf"))
            .withFallback(preCopyConfig)
            .resolve()
    return nodeConfig.parseAsNodeConfiguration()
}

