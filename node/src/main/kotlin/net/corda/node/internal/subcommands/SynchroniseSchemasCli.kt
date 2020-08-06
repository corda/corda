package net.corda.node.internal.subcommands

import net.corda.node.internal.Node
import net.corda.node.internal.NodeCliCommand
import net.corda.node.internal.NodeStartup
import net.corda.node.internal.RunAfterNodeInitialisation

class SynchroniseSchemasCli(startup: NodeStartup) : NodeCliCommand("sync-app-schemas", "Create changelog entries for liquibase files found in CorDapps", startup) {
    override fun runProgram(): Int {
        return startup.initialiseAndRun(cmdLineOptions, object : RunAfterNodeInitialisation {
            override fun run(node: Node) {
                node.runSchemaSync()
            }
        })
    }
}