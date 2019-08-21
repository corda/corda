package net.corda.networkbuilder.containers.push.docker

import net.corda.networkbuilder.containers.push.ContainerPusher
import net.corda.networkbuilder.docker.DockerUtils
import java.util.concurrent.CompletableFuture

class DockerContainerPusher : ContainerPusher {

    override fun pushContainerToImageRepository(localImageId: String, remoteImageName: String, networkName: String): CompletableFuture<String> {
        val dockerClient = DockerUtils.createLocalDockerClient()
        dockerClient.tagImageCmd(localImageId, remoteImageName, networkName).withForce().exec()
        return CompletableFuture.completedFuture("$remoteImageName:$networkName")
    }
}
