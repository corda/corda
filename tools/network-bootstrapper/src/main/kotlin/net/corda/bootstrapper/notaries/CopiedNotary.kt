package net.corda.bootstrapper.notaries

import net.corda.bootstrapper.nodes.CopiedNode
import java.io.File

class CopiedNotary(configFile: File, baseDirectory: File,
                   copiedNodeConfig: File, copiedNodeDir: File, val nodeInfoFile: File) :
        CopiedNode(configFile, baseDirectory, copiedNodeConfig, copiedNodeDir) {
}


fun CopiedNode.toNotary(nodeInfoFile: File): CopiedNotary {
    return CopiedNotary(this.configFile, this.baseDirectory, this.copiedNodeConfig, this.copiedNodeDir, nodeInfoFile)
}