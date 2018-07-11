package net.corda.bootstrapper.nodes

import net.corda.bootstrapper.Constants
import net.corda.bootstrapper.containers.instance.InstanceInfo
import net.corda.bootstrapper.containers.instance.Instantiator
import net.corda.bootstrapper.context.Context
import net.corda.core.identity.CordaX500Name
import java.util.concurrent.CompletableFuture

class NodeInstantiator(val instantiator: Instantiator,
                       val context: Context) {


    fun createInstanceRequests(pushedNode: PushedNode, nodeCount: Map<FoundNode, Int>): List<NodeInstanceRequest> {

        val namedMap = nodeCount.map { it.key.name.toLowerCase() to it.value }.toMap()

        return (0 until (namedMap[pushedNode.name.toLowerCase()] ?: 1)).map { i ->
            createInstanceRequest(pushedNode, i)
        }
    }

    private fun createInstanceRequest(node: PushedNode, i: Int): NodeInstanceRequest {
        val nodeInstanceName = node.name + i
        val expectedName = instantiator.getExpectedFQDN(nodeInstanceName)
        return node.toNodeInstanceRequest(nodeInstanceName, buildX500(node.nodeConfig.myLegalName, i), expectedName)
    }

    fun createInstanceRequest(node: PushedNode): NodeInstanceRequest {
        return createInstanceRequest(node, 0)
    }


    private fun buildX500(baseX500: CordaX500Name, i: Int): String {
        if (i == 0) {
            return baseX500.toString()
        }
        return baseX500.copy(commonName = (baseX500.commonName ?: "") + i).toString()
    }

    fun instantiateNodeInstance(request: Context.PersistableNodeInstance): CompletableFuture<InstanceInfo> {
        return instantiateNodeInstance(request.remoteImageName, request.instanceName, request.fqdn, request.instanceX500).thenApplyAsync {
            InstanceInfo(request.groupName, request.instanceName, request.fqdn, it.first, it.second)
        }
    }

    fun instantiateNodeInstance(request: NodeInstanceRequest): CompletableFuture<NodeInstance> {
        return instantiateNodeInstance(request.remoteImageName, request.nodeInstanceName, request.expectedFqName, request.actualX500)
                .thenApplyAsync { (reachableName, portMapping) ->
                    request.toNodeInstance(reachableName, portMapping)
                }
    }

    fun instantiateNotaryInstance(request: NodeInstanceRequest): CompletableFuture<NodeInstance> {
        return instantiateNotaryInstance(request.remoteImageName, request.nodeInstanceName, request.expectedFqName)
                .thenApplyAsync { (reachableName, portMapping) ->
                    request.toNodeInstance(reachableName, portMapping)
                }
    }

    private fun instantiateNotaryInstance(remoteImageName: String,
                                          nodeInstanceName: String,
                                          expectedFqName: String): CompletableFuture<Pair<String, Map<Int, Int>>> {
        return instantiator.instantiateContainer(
                remoteImageName,
                listOf(Constants.NODE_P2P_PORT, Constants.NODE_RPC_PORT, Constants.NODE_SSHD_PORT),
                nodeInstanceName,
                mapOf("OUR_NAME" to expectedFqName, "OUR_PORT" to Constants.NODE_P2P_PORT.toString())
        )
    }

    private fun instantiateNodeInstance(remoteImageName: String,
                                        nodeInstanceName: String,
                                        expectedFqName: String,
                                        actualX500: String): CompletableFuture<Pair<String, Map<Int, Int>>> {

        return instantiator.instantiateContainer(
                remoteImageName,
                listOf(Constants.NODE_P2P_PORT, Constants.NODE_RPC_PORT, Constants.NODE_SSHD_PORT),
                nodeInstanceName,
                mapOf("OUR_NAME" to expectedFqName,
                        "OUR_PORT" to Constants.NODE_P2P_PORT.toString(),
                        "X500" to actualX500)
        )
    }

    fun expectedFqdn(newInstanceName: String): String {
        return instantiator.getExpectedFQDN(newInstanceName)
    }


}