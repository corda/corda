package net.corda.bootstrapper.nodes

import net.corda.node.services.config.NodeConfiguration
import java.io.File

open class CopiedNode(configFile: File, baseDirectory: File,
                      open val copiedNodeConfig: File, open val copiedNodeDir: File) :
        FoundNode(configFile, baseDirectory) {

    constructor(foundNode: FoundNode, copiedNodeConfig: File, copiedNodeDir: File) : this(
            foundNode.configFile, foundNode.baseDirectory, copiedNodeConfig, copiedNodeDir
    )

    operator fun component4(): File {
        return copiedNodeDir;
    }

    operator fun component5(): File {
        return copiedNodeConfig;
    }


    fun builtNode(nodeConfig: NodeConfiguration, imageId: String): BuiltNode {
        return BuiltNode(configFile, baseDirectory, copiedNodeConfig, copiedNodeDir, nodeConfig, imageId)
    }

    override fun toString(): String {
        return "CopiedNode(" +
                "copiedNodeConfig=$copiedNodeConfig," +
                "copiedNodeDir=$copiedNodeDir" +
                ")" +
                " ${super.toString()}"
    }

    fun toBuiltNode(nodeConfig: NodeConfiguration, localImageId: String): BuiltNode {
        return BuiltNode(this.configFile, this.baseDirectory, this.copiedNodeConfig, this.copiedNodeDir, nodeConfig, localImageId)
    }


}