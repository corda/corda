package net.corda.node.internal.subcommands

import net.corda.node.internal.Node
import net.corda.node.internal.NodeCliCommand
import net.corda.node.internal.NodeStartup
import net.corda.node.internal.NodeStartupCli
import net.corda.node.internal.RunAfterNodeInitialisation
import picocli.CommandLine

class ClearNetworkCacheCli(startup: NodeStartup): NodeCliCommand("clear-network-cache", "Clear local copy of network map, on node startup it will be restored from server or file system.", startup) {
    @CommandLine.ParentCommand
    lateinit var nodeStartupCli: NodeStartupCli

    override fun runProgram(): Int {
        return startup.initialiseAndRun(nodeStartupCli.cmdLineOptions, object: RunAfterNodeInitialisation {
            override fun run(node: Node) = node.clearNetworkMapCache()
        })
    }
}