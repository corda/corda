package net.corda.node.internal.subcommands

import net.corda.node.internal.Node
import net.corda.node.internal.NodeCliCommand
import net.corda.node.internal.NodeStartup
import net.corda.node.internal.RunAfterNodeInitialisation

class RunMigrationScriptsCli(startup: NodeStartup) : NodeCliCommand("run-migration-scripts", "Run the database migration scripts and create or update schemas", startup) {
    override fun runProgram(): Int {
        return startup.initialiseAndRun(cmdLineOptions, object : RunAfterNodeInitialisation {
            override fun run(node: Node) {
                node.runDatabaseMigrationScripts()
            }
        })
    }
}