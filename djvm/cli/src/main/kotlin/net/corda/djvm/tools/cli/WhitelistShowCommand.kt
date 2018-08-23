package net.corda.djvm.tools.cli

import picocli.CommandLine.*
import java.nio.file.Path

@Command(
        name = "show",
        description = ["Print the whitelist used for the deterministic sandbox."]
)
@Suppress("KDocMissingDocumentation")
class WhitelistShowCommand : CommandBase() {

    @Option(
            names = ["-w", "--whitelist"],
            description = ["Override the default whitelist. Use provided whitelist instead."]
    )
    var whitelist: Path? = null

    @Parameters(description = ["Words or phrases to use to filter down the result."])
    var filters: Array<String> = emptyArray()

    override fun validateArguments() = true

    override fun handleCommand(): Boolean {
        val whitelist = whitelistFromPath(whitelist)
        val filters = filters.map(String::toLowerCase)
        whitelist.items
                .filter { item -> filters.all { it in item.toLowerCase() } }
                .forEach { println(it) }
        return true
    }

}
