package net.corda.node.internal.subcommands

import net.corda.node.internal.Node
import net.corda.node.internal.NodeCliCommand
import net.corda.node.internal.NodeStartup
import net.corda.node.internal.RunAfterNodeInitialisation
import picocli.CommandLine

class RunMigrationScriptsCli(startup: NodeStartup) : NodeCliCommand("run-migration-scripts", "Run the database migration scripts and create or update schemas", startup) {
    @CommandLine.Option(names = ["--core-schemas"], description = ["Manage the core/node schemas"])
    var updateCoreSchemas: Boolean = false

    @CommandLine.Option(names = ["--app-schemas"], description = ["Manage the CorDapp schemas"])
    var updateAppSchemas: Boolean = false

    @CommandLine.Option(names = ["--update-app-schema-with-checkpoints"], description = ["Allow updating app schema even if there are suspended flows"])
    var updateAppSchemaWithCheckpoints: Boolean = false



    override fun runProgram(): Int {
        require(updateAppSchemas || updateCoreSchemas) { "Nothing to do: at least one of --core-schemas or --app-schemas must be set" }
        return startup.initialiseAndRun(cmdLineOptions, object : RunAfterNodeInitialisation {
            override fun run(node: Node) {
                node.runDatabaseMigrationScripts(updateCoreSchemas, updateAppSchemas, updateAppSchemaWithCheckpoints)
            }
        })
    }
}