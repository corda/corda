package net.corda.bootstrapper.nodes

import net.corda.node.services.config.NodeConfiguration
import java.io.File

open class PushedNode(configFile: File, baseDirectory: File,
                      copiedNodeConfig: File, copiedNodeDir: File,
                      nodeConfig: NodeConfiguration, localImageId: String, val remoteImageName: String) : BuiltNode(
        configFile,
        baseDirectory,
        copiedNodeConfig,
        copiedNodeDir,
        nodeConfig,
        localImageId
) {
    fun toNodeInstanceRequest(nodeInstanceName: String, actualX500: String, expectedFqName: String): NodeInstanceRequest {
        return NodeInstanceRequest(configFile, baseDirectory, copiedNodeConfig, copiedNodeDir, nodeConfig, localImageId, remoteImageName, nodeInstanceName, actualX500, expectedFqName)
    }

    override fun toString(): String {
        return "PushedNode(remoteImageName='$remoteImageName') ${super.toString()}"
    }


}