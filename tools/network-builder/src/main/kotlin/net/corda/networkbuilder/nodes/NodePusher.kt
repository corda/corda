package net.corda.networkbuilder.nodes

import net.corda.networkbuilder.containers.push.ContainerPusher
import net.corda.networkbuilder.context.Context
import java.util.concurrent.CompletableFuture

class NodePusher(private val containerPusher: ContainerPusher,
                 private val context: Context) {

    fun pushNode(builtNode: BuiltNode): CompletableFuture<PushedNode> {

        val localImageId = builtNode.localImageId
        val nodeImageIdentifier = "node-${builtNode.name}"
        val nodeImageNameFuture = containerPusher.pushContainerToImageRepository(localImageId,
                nodeImageIdentifier, context.networkName)
        return nodeImageNameFuture.thenApplyAsync { imageName -> builtNode.toPushedNode(imageName) }
    }
}