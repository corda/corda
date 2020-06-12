package net.corda.node.internal.subcommands

import net.corda.node.internal.Node
import net.corda.node.internal.NodeCliCommand
import net.corda.node.internal.NodeStartup
import net.corda.node.internal.NodeStartupCli
import net.corda.node.internal.RunAfterNodeInitialisation
import picocli.CommandLine

class GenerateNodeInfoCli(startup: NodeStartup): NodeCliCommand("generate-node-info", "Perform the node start-up tasks necessary to generate the nodeInfo file, save it to disk, then exit.", startup) {
    @CommandLine.ParentCommand
    lateinit var nodeStartupCli: NodeStartupCli

    override fun runProgram(): Int {
        return startup.initialiseAndRun(nodeStartupCli.cmdLineOptions, object : RunAfterNodeInitialisation {
            override fun run(node: Node) {
                node.generateAndSaveNodeInfo()
            }
        })
    }
}