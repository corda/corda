package net.corda.bootstrapper

import com.jcabi.manifests.Manifests
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.*
import net.corda.nodeapi.internal.network.NetworkBootstrapper
import picocli.CommandLine
import picocli.CommandLine.*
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val main = Main()
    try {
        CommandLine.run(main, *args)
    } catch (e: ExecutionException) {
        val throwable = e.cause ?: e
        if (main.verbose) {
            throwable.printStackTrace()
        } else {
            System.err.println("*ERROR*: ${throwable.rootMessage ?: "Use --verbose for more details"}")
        }
        exitProcess(1)
    }
}

@Command(
        name = "Network Bootstrapper",
        versionProvider = CordaVersionProvider::class,
        mixinStandardHelpOptions = true,
        showDefaultValues = true,
        description = ["Bootstrap a local test Corda network using a set of node configuration files and CorDapp JARs"]
)
class Main : Runnable {
    @Option(
            names = ["--dir"],
            description = [
                "Root directory containing the node configuration files and CorDapp JARs that will form the test network.",
                "It may also contain existing node directories."
            ]
    )
    private var dir: Path = Paths.get(".")

    @Option(names = ["--no-copy"], description = ["""Don't copy the CorDapp JARs into the nodes' "cordapps" directories."""])
    private var noCopy: Boolean = false

    @Option(names = ["--verbose"], description = ["Enable verbose output."])
    var verbose: Boolean = false

    @Option(names = ["--install-shell-extensions", "-i"], description = ["Install bootstrapper alias and autocompletion in bash"])
    var install: Boolean = false

    @Option(names = ["--new-option"], description = ["This is a new option"])
    var newOption: Boolean = false

    // Return the lines in the file if it exists, else return an empty mutable list
    private fun getFileLines(filePath: Path): MutableList<String> {
        return if (filePath.exists()) {
            filePath.toFile().readLines().toMutableList()
        } else {
            emptyList<String>().toMutableList()
        }
    }

    private fun MutableList<String>.addOrReplaceIfStartsWith(startsWith: String, replaceWith: String) {
        val index = this.indexOfFirst { it.startsWith(startsWith) }
        if (index >= 0) {
            this[index] = replaceWith
        } else {
            this.add(replaceWith)
        }
    }

    private fun MutableList<String>.addIfNotExists(line: String) {
        if (!this.contains(line)) {
            this.add(line)
        }
    }

    private val userHome: Path by lazy { Paths.get(System.getProperty("user.home")) }
    private val jarLocation: Path by lazy { this.javaClass.location.toPath() }
    private val jarHash: SecureHash by lazy { jarLocation.toFile().inputStream().hash() }

    // If on Windows, Path.toString() returns a path with \ instead of /, but for bash Windows users we want to convert those back to /'s
    private fun Path.toStringWithDeWindowsfication(): String = this.toAbsolutePath().toString().replace("\\", "/")
    private fun jarSignature(alias: String, jarHash: SecureHash) = "# $alias: $jarHash"
    private fun getAutoCompleteFileLocation(alias: String) = userHome / ".completion" / alias

    private fun generateAutoCompleteFile(alias: String) {
        println("Generating $alias auto completion file")
        val autoCompleteFile = getAutoCompleteFileLocation(alias)
        autoCompleteFile.root.createDirectories()
        picocli.AutoComplete.main("-f", "-n", alias, this.javaClass.name, "-o", autoCompleteFile.toStringWithDeWindowsfication())

        // Append hash of file to autocomplete file
        autoCompleteFile.toFile().appendText(jarSignature(alias, jarHash))
    }

    private fun installShellExtensions(alias: String) {
        // Get jar location and generate alias command
        val command = "alias $alias='java -jar \"${jarLocation.toStringWithDeWindowsfication()}\"'"

        generateAutoCompleteFile(alias)

        // Get bash settings file
        val bashSettingsFile = userHome / ".bashrc"
        val bashSettingsFileLines = getFileLines(bashSettingsFile).toMutableList()

        println("Updating bash settings files")
        // Replace any existing bootstrapper alias. There can be only one.
        bashSettingsFileLines.addOrReplaceIfStartsWith("alias $alias", command)

        val completionFileCommand = "for bcfile in ~/.completion/* ; do . \$bcfile; done"
        bashSettingsFileLines.addIfNotExists(completionFileCommand)

        bashSettingsFile.writeLines(bashSettingsFileLines)

        println("Installation complete, $alias is available in bash with autocompletion. ")
        println("Type `$alias <options>` from the commandline.")
        println("Restart bash for this to take effect, or run `. ~/.bashrc` to re-initialise bash now")
    }

    private fun checkForAutoCompleteUpdate(alias: String) {
        val autoCompleteFile = getAutoCompleteFileLocation(alias)

        // If no autocomplete file, it hasn't been installed, so don't do anything
        if (!autoCompleteFile.exists()) return

        var lastLine = ""
        autoCompleteFile.toFile().forEachLine { lastLine = it.toString() }

        if (lastLine != jarSignature(alias, jarHash)) {
            println("Old auto completion file detected... regenerating")
            generateAutoCompleteFile(alias)
            println("Restart bash for this to take effect, or run `. ~/.bashrc` to re-initialise bash now")
        }
    }

    private fun installOrUpdateShellExtensions(alias: String) {
        if (install) {
            installShellExtensions(alias)
            return
        } else {
            checkForAutoCompleteUpdate(alias)
        }
    }

    override fun run() {
        installOrUpdateShellExtensions("bootstrapper")
        if (verbose) {
            System.setProperty("logLevel", "trace")
        }
        NetworkBootstrapper().bootstrap(dir.toAbsolutePath().normalize(), copyCordapps = !noCopy)
    }
}

private class CordaVersionProvider : IVersionProvider {
    override fun getVersion(): Array<String> {
        return arrayOf(
                "Version: ${Manifests.read("Corda-Release-Version")}",
                "Revision: ${Manifests.read("Corda-Revision")}"
        )
    }
}
