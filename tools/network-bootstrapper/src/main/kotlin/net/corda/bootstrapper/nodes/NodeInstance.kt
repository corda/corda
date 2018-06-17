package net.corda.bootstrapper.nodes

import net.corda.node.services.config.NodeConfiguration
import java.io.File

class NodeInstance(configFile: File,
                   baseDirectory: File,
                   copiedNodeConfig: File,
                   copiedNodeDir: File,
                   nodeConfig: NodeConfiguration,
                   localImageId: String,
                   remoteImageName: String,
                   nodeInstanceName: String,
                   actualX500: String,
                   expectedFqName: String,
                   val reachableAddress: String,
                   val portMapping: Map<Int, Int>) :
        NodeInstanceRequest(
                configFile,
                baseDirectory,
                copiedNodeConfig,
                copiedNodeDir,
                nodeConfig,
                localImageId,
                remoteImageName,
                nodeInstanceName,
                actualX500,
                expectedFqName
        ) {
}

