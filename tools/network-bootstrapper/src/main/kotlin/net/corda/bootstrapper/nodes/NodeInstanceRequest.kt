package net.corda.bootstrapper.nodes

import net.corda.node.services.config.NodeConfiguration
import java.io.File

open class NodeInstanceRequest(configFile: File, baseDirectory: File,
                               copiedNodeConfig: File, copiedNodeDir: File,
                               nodeConfig: NodeConfiguration, localImageId: String, remoteImageName: String,
                               internal val nodeInstanceName: String,
                               internal val actualX500: String,
                               internal val expectedFqName: String) : PushedNode(
        configFile, baseDirectory, copiedNodeConfig, copiedNodeDir, nodeConfig, localImageId, remoteImageName
) {

    override fun toString(): String {
        return "NodeInstanceRequest(nodeInstanceName='$nodeInstanceName', actualX500='$actualX500', expectedFqName='$expectedFqName') ${super.toString()}"
    }

    fun toNodeInstance(reachableAddress: String, portMapping: Map<Int, Int>): NodeInstance {
        return NodeInstance(configFile, baseDirectory, copiedNodeConfig, copiedNodeDir, nodeConfig, localImageId, remoteImageName, nodeInstanceName, actualX500, expectedFqName, reachableAddress, portMapping)
    }


}