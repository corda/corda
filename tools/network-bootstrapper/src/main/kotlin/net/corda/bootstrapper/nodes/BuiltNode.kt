package net.corda.bootstrapper.nodes

import net.corda.node.services.config.NodeConfiguration
import java.io.File

open class BuiltNode(configFile: File, baseDirectory: File,
                     copiedNodeConfig: File, copiedNodeDir: File,
                     val nodeConfig: NodeConfiguration, val localImageId: String) : CopiedNode(configFile, baseDirectory, copiedNodeConfig, copiedNodeDir) {


    override fun toString(): String {
        return "BuiltNode(" +
                "nodeConfig=$nodeConfig," +
                "localImageId='$localImageId'" +
                ")" +
                " ${super.toString()}"
    }

    fun toPushedNode(remoteImageName: String): PushedNode {
        return PushedNode(configFile, baseDirectory, copiedNodeConfig, copiedNodeDir, nodeConfig, localImageId, remoteImageName)
    }
}