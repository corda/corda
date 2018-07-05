package net.corda.djvm.tools.cli

import picocli.CommandLine
import picocli.CommandLine.Command

@Command(
        name = "djvm",
        versionProvider = VersionProvider::class,
        description = ["JVM for running programs in a deterministic sandbox."],
        mixinStandardHelpOptions = true,
        subcommands = [
            BuildCommand::class,
            CheckCommand::class,
            InspectionCommand::class,
            NewCommand::class,
            RunCommand::class,
            ShowCommand::class,
            TreeCommand::class,
            WhitelistCommand::class
        ]
)
@Suppress("KDocMissingDocumentation")
class Commands : CommandBase() {

    fun run(args: Array<String>) = when (CommandLine.call(this, System.err, *args)) {
        true -> 0
        else -> 1
    }

}
