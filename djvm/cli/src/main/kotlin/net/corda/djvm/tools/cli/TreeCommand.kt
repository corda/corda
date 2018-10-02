package net.corda.djvm.tools.cli

import picocli.CommandLine.Command
import java.nio.file.Files

@Command(
        name = "tree",
        description = ["Show the hierarchy of the classes that have been created with the 'new' command."]
)
@Suppress("KDocMissingDocumentation")
class TreeCommand : CommandBase() {

    override fun validateArguments() = true

    override fun handleCommand(): Boolean {
        val path = workingDirectory.resolve("tmp")
        if (!Files.exists(path)) {
            printError("No classes have been created so far. Run `djvm new` to get started.")
            return false
        }
        ProcessBuilder("find", ".", "-type", "f").apply {
            inheritIO()
            environment().putAll(System.getenv())
            directory(path.toFile())
            start().apply {
                waitFor()
                exitValue()
            }
        }
        return true
    }

}
