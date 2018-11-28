package net.corda.bootstrapper

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import net.corda.cliutils.*
import net.corda.common.configuration.parsing.internal.Configuration
import net.corda.core.internal.PLATFORM_VERSION
import net.corda.core.internal.exists
import net.corda.nodeapi.internal.network.*
import net.corda.nodeapi.internal.network.NetworkBootstrapper.Companion.DEFAULT_MAX_MESSAGE_SIZE
import net.corda.nodeapi.internal.network.NetworkBootstrapper.Companion.DEFAULT_MAX_TRANSACTION_SIZE
import picocli.CommandLine.Option
import java.io.FileNotFoundException
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration

fun main(args: Array<String>) {
    NetworkBootstrapperRunner().start(args)
}

class NetworkBootstrapperRunner(private val bootstrapper: NetworkBootstrapperWithOverridableParameters = NetworkBootstrapper()) : CordaCliWrapper("bootstrapper", "Bootstrap a local test Corda network using a set of node configuration files and CorDapp JARs") {
    @Option(names = ["--dir"],
            description = [ "Root directory containing the node configuration files and CorDapp JARs that will form the test network.",
                "It may also contain existing node directories."])
    var dir: Path = Paths.get(".")

    @Option(names = ["--no-copy"], hidden = true, description = ["""DEPRECATED. Don't copy the CorDapp JARs into the nodes' "cordapps" directories."""])
    var noCopy: Boolean? = null

    @Option(names = ["--copy-cordapps"], description = ["Whether or not to copy the CorDapp JARs into the nodes' 'cordapps' directory. \${COMPLETION-CANDIDATES}"])
    var copyCordapps: CopyCordapps = CopyCordapps.FirstRunOnly

    @Option(names = ["--minimum-platform-version"], description = ["The minimum platform version to use in the network-parameters. Current default is $PLATFORM_VERSION."])
    var minimumPlatformVersion: Int? = null

    @Option(names = ["--max-message-size"], description = ["The maximum message size to use in the network-parameters, in bytes. Current default is $DEFAULT_MAX_MESSAGE_SIZE."])
    var maxMessageSize: Int? = null

    @Option(names = ["--max-transaction-size"], description = ["The maximum transaction size to use in the network-parameters, in bytes. Current default is $DEFAULT_MAX_TRANSACTION_SIZE."])
    var maxTransactionSize: Int? = null

    @Option(names = ["--event-horizon"], description = ["The event horizon to use in the network-parameters. Default is 30 days."])
    var eventHorizon: Duration? = null

    @Option(names = ["--network-parameter-overrides", "-n"], description = ["Overrides the default network parameters with those in the given file."])
    var networkParametersFile: Path? = null


    private fun verifyInputs() {
        require(minimumPlatformVersion == null || minimumPlatformVersion ?: 0 > 0) { "The --minimum-platform-version parameter must be at least 1" }
        require(eventHorizon == null || eventHorizon?.isNegative == false) { "The --event-horizon parameter must be a positive value" }
        require(maxTransactionSize == null || maxTransactionSize ?: 0 > 0) { "The --max-transaction-size parameter must be at least 1" }
        require(maxMessageSize == null || maxMessageSize ?: 0 > 0) { "The --max-message-size parameter must be at least 1" }
    }

    private fun commandLineOverrides(): Map<String, Any> {
        val overrides = mutableMapOf<String, Any>()
        overrides += minimumPlatformVersion?.let { mapOf("minimumPlatformVersion" to minimumPlatformVersion!!) } ?: mutableMapOf()
        overrides += maxMessageSize?.let { mapOf("maxMessageSize" to maxMessageSize!!) } ?: emptyMap()
        overrides += maxTransactionSize?.let { mapOf("maxTransactionSize" to maxTransactionSize!!) } ?: emptyMap()
        overrides += eventHorizon?.let { mapOf("eventHorizon" to eventHorizon!!) } ?: emptyMap()
        return overrides
    }

    private fun getNetworkParametersOverrides(): Valid<NetworkParametersOverrides> {
        val parseOptions = ConfigParseOptions.defaults()
        val config = if (networkParametersFile == null) {
            ConfigFactory.empty()
        } else {
            if (networkParametersFile?.exists() != true) throw FileNotFoundException("Unable to find specified network parameters config file at $networkParametersFile")
            ConfigFactory.parseFile(networkParametersFile!!.toFile(), parseOptions)
        }
        val finalConfig = ConfigFactory.parseMap(commandLineOverrides()).withFallback(config).resolve()
        return finalConfig.parseAsNetworkParametersConfiguration()
    }

    private fun <T> Collection<T>.pluralise() = if (this.count() > 1) "s" else ""

    private fun reportErrors(errors: Set<Configuration.Validation.Error>) {
        printError("Error${errors.pluralise()} found parsing the network parameter overrides file at $networkParametersFile:")
        errors.forEach { printError("Error parsing ${it.pathAsString}: ${it.message}") }
    }

    override fun runProgram(): Int {
        if (noCopy != null) {
            printWarning("The --no-copy parameter has been deprecated and been replaced with the --copy-cordapps parameter.")
            copyCordapps = if (noCopy == true) CopyCordapps.No else CopyCordapps.Yes
        }
        verifyInputs()
        val networkParameterOverrides = getNetworkParametersOverrides().doOnErrors(::reportErrors).optional ?: return ExitCodes.FAILURE
        bootstrapper.bootstrap(dir.toAbsolutePath().normalize(),
                copyCordapps = copyCordapps,
                networkParameterOverrides = networkParameterOverrides
        )
        return ExitCodes.SUCCESS
    }
}
