package net.corda.node.internal.subcommands

import net.corda.cliutils.CliWrapperBase
import net.corda.node.BootstrapRaftNotaryCmdLineOptions
import net.corda.node.internal.Node
import net.corda.node.internal.NodeStartup
import net.corda.node.internal.RunAfterNodeInitialisation
import picocli.CommandLine

class BootstrapRaftNotaryCli(val startup: NodeStartup) : CliWrapperBase("bootstrap-raft-cluster", "Bootstraps Raft cluster. The node forms a single node cluster (ignoring otherwise configured peer addresses), acting as a seed for other nodes to join the cluster.") {
    override fun runProgram(): Int {
        return startup.initialiseAndRun(cmdLineOptions, object : RunAfterNodeInitialisation {
            val startupTime = System.currentTimeMillis()

            override fun run(node: Node) = startup.startNode(node, startupTime)
        })
    }

    @CommandLine.Mixin
    val cmdLineOptions = BootstrapRaftNotaryCmdLineOptions()
}