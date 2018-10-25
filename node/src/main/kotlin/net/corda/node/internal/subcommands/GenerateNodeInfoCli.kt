package net.corda.node.internal.subcommands

import net.corda.node.internal.Node
import net.corda.node.internal.NodeCliCommand
import net.corda.node.internal.NodeStartup
import net.corda.node.internal.RunAfterNodeInitialisation

class GenerateNodeInfoCli(startup: NodeStartup): NodeCliCommand("generate-node-info", "Performs the node start-up tasks necessary to generate the nodeInfo file, saves it to disk, then exits.", startup) {
    override fun runProgram(): Int {
        return startup.initialiseAndRun(cmdLineOptions, object : RunAfterNodeInitialisation {
            override fun run(node: Node) {
                node.generateAndSaveNodeInfo()
            }
        })
    }
}