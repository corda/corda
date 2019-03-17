package net.corda.djvm.tools.cli

import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import java.nio.file.Path

@Command(
        name = "build",
        description = ["Build one or more Java source files, each implementing the sandbox runnable interface " +
                "required for execution in the deterministic sandbox."]
)
@Suppress("KDocMissingDocumentation")
class BuildCommand : CommandBase() {

    @Parameters
    var files: Array<Path> = emptyArray()

    override fun validateArguments() = files.isNotEmpty()

    override fun handleCommand(): Boolean {
        val codePath = createCodePath()
        val files = files.getFileNames { codePath.resolve(it) }
        printVerbose("Compiling ${files.joinToString(", ")}...")
        ProcessBuilder("javac", "-cp", "tmp:$jarPath", *files).apply {
            inheritIO()
            environment().putAll(System.getenv())
            start().apply {
                waitFor()
                return (exitValue() == 0).apply {
                    if (this) {
                        printInfo("Build succeeded")
                    }
                }
            }
        }
        return true
    }

}
