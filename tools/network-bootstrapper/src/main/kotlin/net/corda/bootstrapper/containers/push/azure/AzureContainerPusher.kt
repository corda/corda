package net.corda.bootstrapper.containers.push.azure

import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.PushResponseItem
import com.microsoft.azure.management.Azure
import com.microsoft.azure.management.containerregistry.Registry
import net.corda.bootstrapper.containers.push.ContainerPusher
import net.corda.bootstrapper.docker.DockerUtils
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.util.concurrent.CompletableFuture


class AzureContainerPusher(private val azure: Azure, private val azureRegistry: Registry) : ContainerPusher {


    override fun pushContainerToImageRepository(localImageId: String,
                                                remoteImageName: String,
                                                networkName: String): CompletableFuture<String> {


        val (registryUser, registryPassword) = azureRegistry.parseCredentials()
        val dockerClient = DockerUtils.createDockerClient(
                azureRegistry.loginServerUrl(),
                registryUser,
                registryPassword)

        val privateRepoUrl = "${azureRegistry.loginServerUrl()}/$remoteImageName".toLowerCase()
        dockerClient.tagImageCmd(localImageId, privateRepoUrl, networkName).exec()
        val result = CompletableFuture<String>()
        dockerClient.pushImageCmd("$privateRepoUrl:$networkName")
                .withAuthConfig(dockerClient.authConfig())
                .exec(object : ResultCallback<PushResponseItem> {
                    override fun onComplete() {
                        LOG.info("completed PUSH image: $localImageId to registryURL: $privateRepoUrl:$networkName")
                        result.complete("$privateRepoUrl:$networkName")
                    }

                    override fun close() {
                    }

                    override fun onNext(`object`: PushResponseItem) {
                    }

                    override fun onError(throwable: Throwable?) {
                        result.completeExceptionally(throwable)
                    }

                    override fun onStart(closeable: Closeable?) {
                        LOG.info("starting PUSH image: $localImageId to registryURL: $privateRepoUrl:$networkName")
                    }
                })
        return result
    }

    companion object {
        val LOG = LoggerFactory.getLogger(AzureContainerPusher::class.java)
    }

}

