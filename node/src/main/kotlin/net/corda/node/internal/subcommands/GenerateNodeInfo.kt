package net.corda.node.internal.subcommands

import net.corda.node.internal.Node
import net.corda.node.internal.NodeCliCommand
import net.corda.node.internal.NodeStartup
import net.corda.node.internal.RunAfterNodeInitialisation

class GenerateNodeInfo(startup: NodeStartup): NodeCliCommand("generate-node-info", "Perform the node start-up task necessary to generate its node info, save it to disk, then quit", startup) {
    override fun runProgram(): Int {
        return startup.initialiseAndRun(cmdLineOptions, object : RunAfterNodeInitialisation {
            override fun run(node: Node) {
                node.generateAndSaveNodeInfo()
            }
        })
    }
}