package net.corda.networkbuilder.containers.push

import java.util.concurrent.CompletableFuture

interface ContainerPusher {
    fun pushContainerToImageRepository(localImageId: String,
                                       remoteImageName: String,
                                       networkName: String): CompletableFuture<String>
}