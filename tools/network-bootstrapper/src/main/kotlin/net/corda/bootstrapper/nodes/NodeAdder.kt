package net.corda.bootstrapper.nodes

import net.corda.bootstrapper.containers.instance.InstanceInfo
import net.corda.bootstrapper.context.Context
import java.util.concurrent.CompletableFuture

class NodeAdder(val context: Context,
                val nodeInstantiator: NodeInstantiator) {

    fun addNode(context: Context, nodeGroupName: String): CompletableFuture<InstanceInfo> {
        return synchronized(context) {
            val nodeGroup = context.nodes[nodeGroupName]!!
            val nodeInfo = nodeGroup.iterator().next()
            val currentNodeSize = nodeGroup.size
            val newInstanceX500 = nodeInfo.groupX500!!.copy(commonName = nodeInfo.groupX500.commonName + (currentNodeSize)).toString()
            val newInstanceName = nodeGroupName + (currentNodeSize)
            val nextNodeInfo = nodeInfo.copy(
                    instanceX500 = newInstanceX500,
                    instanceName = newInstanceName,
                    fqdn = nodeInstantiator.expectedFqdn(newInstanceName)
            )
            nodeGroup.add(nextNodeInfo)
            nodeInstantiator.instantiateNodeInstance(nextNodeInfo)
        }
    }
}