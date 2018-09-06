package net.corda.bootstrapper

import net.corda.cliutils.CordaCliWrapper
import net.corda.cliutils.start
import net.corda.nodeapi.internal.network.NetworkBootstrapper
import picocli.CommandLine.Option
import java.nio.file.Path
import java.nio.file.Paths

fun main(args: Array<String>) {
    NetworkBootstrapperRunner().start(args)
}

class NetworkBootstrapperRunner : CordaCliWrapper("bootstrapper", "Bootstrap a local test Corda network using a set of node configuration files and CorDapp JARs") {
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

    override fun runProgram(): Int {
        NetworkBootstrapper().bootstrap(dir.toAbsolutePath().normalize(), copyCordapps = !noCopy)
        return 0 //exit code
    }
}