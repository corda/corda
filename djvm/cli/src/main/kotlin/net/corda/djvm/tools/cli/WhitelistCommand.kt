package net.corda.djvm.tools.cli

import picocli.CommandLine.Command

@Command(
        name = "whitelist",
        description = ["Utilities and commands related to the whitelist for the deterministic sandbox."],
        subcommands = [
            WhitelistGenerateCommand::class,
            WhitelistShowCommand::class
        ]
)
@Suppress("KDocMissingDocumentation")
class WhitelistCommand : CommandBase()
