package net.corda.node.internal.subcommands

import net.corda.cliutils.CliWrapperBase
import net.corda.core.internal.*
import net.corda.node.InitialRegistrationCmdLineOptions
import net.corda.node.NodeRegistrationOption
import net.corda.node.internal.*
import net.corda.node.internal.NodeStartupLogging.Companion.logger
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.utilities.registration.HTTPNetworkRegistrationService
import net.corda.node.utilities.registration.NodeRegistrationHelper
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import picocli.CommandLine.Mixin
import picocli.CommandLine.Option
import java.io.File
import java.nio.file.Path
import java.sql.SQLException
import java.util.function.Consumer

class InitialRegistrationCli(val startup: NodeStartup): CliWrapperBase("initial-registration", "Start initial node registration with Corda network to obtain certificate from the permissioning server.") {
    @Option(names = ["-t", "--network-root-truststore"], description = ["Network root trust store obtained from network operator."])
    var networkRootTrustStorePathParameter: Path? = null

    @Option(names = ["-p", "--network-root-truststore-password"], description = ["Network root trust store password obtained from network operator."], required = true)
    var networkRootTrustStorePassword: String = ""

    override fun runProgram() : Int {
        val networkRootTrustStorePath: Path = networkRootTrustStorePathParameter ?: cmdLineOptions.baseDirectory / "certificates" / "network-root-truststore.jks"
        return startup.initialiseAndRun(cmdLineOptions, InitialRegistration(cmdLineOptions.baseDirectory, networkRootTrustStorePath, networkRootTrustStorePassword, startup))
    }

    override fun initLogging(): Boolean = this.initLogging(cmdLineOptions.baseDirectory)

    @Mixin
    val cmdLineOptions = InitialRegistrationCmdLineOptions()
}

class InitialRegistration(val baseDirectory: Path, private val networkRootTrustStorePath: Path, networkRootTrustStorePassword: String, private val startup: NodeStartup) : RunAfterNodeInitialisation, NodeStartupLogging {
    companion object {
        private const val INITIAL_REGISTRATION_MARKER = ".initialregistration"

        fun checkRegistrationMode(baseDirectory: Path): Boolean {
            // If the node was started with `--initial-registration`, create marker file.
            // We do this here to ensure the marker is created even if parsing the args with NodeArgsParser fails.
            val marker = baseDirectory / INITIAL_REGISTRATION_MARKER
            if (!marker.exists()) {
                return false
            }
            try {
                marker.createFile()
            } catch (e: Exception) {
                logger.warn("Could not create marker file for `initial-registration`.", e)
            }
            return true
        }
    }

    private val nodeRegistration = NodeRegistrationOption(networkRootTrustStorePath, networkRootTrustStorePassword)

    private val EXISTING_STATE_GENERIC_WARNING = "Initial registration can only be run on a new, clean node."

    @VisibleForTesting
    fun registerWithNetwork(conf: NodeConfiguration) {
        verifyNoStateFromPreviousRuns(conf)

        val versionInfo = startup.getVersionInfo()

        println("\n" +
                "******************************************************************\n" +
                "*                                                                *\n" +
                "*      Registering as a new participant with a Corda network     *\n" +
                "*                                                                *\n" +
                "******************************************************************\n")

        NodeRegistrationHelper(conf,
                HTTPNetworkRegistrationService(
                        requireNotNull(conf.networkServices),
                        versionInfo),
                nodeRegistration).generateKeysAndRegister()

        // Minimal changes to make registration tool create node identity.
        // TODO: Move node identity generation logic from node to registration helper.
        startup.createNode(conf, versionInfo).generateAndSaveNodeInfo()

        println("Successfully registered Corda node with compatibility zone, node identity keys and certificates are stored in '${conf.certificatesDirectory}', it is advised to backup the private keys and certificates.")
        println("Corda node will now terminate.")
    }

    private fun verifyNoStateFromPreviousRuns(conf: NodeConfiguration) {
        val artemisDirectory = baseDirectory / "artemis"
        if (artemisDirectory.exists()) {
            check(artemisDirectory.isDirectory()) { "$artemisDirectory is not a directory." }
            check(artemisDirectory.list().isEmpty()) { "The node folder contains a non-empty artemis directory. $EXISTING_STATE_GENERIC_WARNING" }
        }

        val brokersDirectory = baseDirectory / "brokers"
        if (brokersDirectory.exists()) {
            check(brokersDirectory.isDirectory()) { "$brokersDirectory is not a directory." }
            check(brokersDirectory.list().isEmpty()) { "The node folder contains a non-empty brokers directory. $EXISTING_STATE_GENERIC_WARNING" }
        }

        val datasource = DataSourceFactory.createDataSource(conf.dataSourceProperties, false)
        try {
            val connection = datasource.connection
            connection.use {
                val tables = mutableSetOf<String>()
                val connectionMetadata = it.metaData

                // Accounting for different case-sensitivity behaviours (i.e. H2 creates tables in upper-case in some cases)
                val tablesLowerCaseResultSet = connectionMetadata.getTables(null, null, "$NODE_DATABASE_PREFIX%", null)
                while (tablesLowerCaseResultSet.next()) {
                    tables.add(tablesLowerCaseResultSet.getString(3))
                }
                val tablesUpperCaseResultSet = connectionMetadata.getTables(null, null, "${NODE_DATABASE_PREFIX.toUpperCase()}%", null)
                while (tablesUpperCaseResultSet.next()) {
                    tables.add(tablesUpperCaseResultSet.getString(3).toLowerCase())
                }
                logger.info("tables: $tables")

                if (tables.contains("node_infos")) {
                    val statement = it.createStatement()
                    if (statement.execute("SELECT COUNT(*) FROM NODE_INFOS")) {
                        if (statement.resultSet.next()) {
                            val nrNodeInfos = statement.resultSet.getInt(1)
                            logger.info("$nrNodeInfos node infos found")
                            check(nrNodeInfos == 0) { "The node info table contains node infos. $EXISTING_STATE_GENERIC_WARNING" }
                        }
                    }
                }
            }
        } catch (exception: SQLException) {
            throw Exception("An error occurred whilst connecting to \"${conf.dataSourceProperties.getProperty("dataSource.url")}\". ", exception)
        }
    }

    private fun initialRegistration(config: NodeConfiguration) {
        // Null checks for [compatibilityZoneURL], [rootTruststorePath] and [rootTruststorePassword] has been done in [CmdLineOptions.loadConfig]
        attempt { registerWithNetwork(config) }.doOnFailure(Consumer(this::handleRegistrationError))
        deleteNodeRegistrationMarker(baseDirectory)
    }

    private fun deleteNodeRegistrationMarker(baseDir: Path) {
        try {
            val marker = File((baseDir / INITIAL_REGISTRATION_MARKER).toUri())
            if (marker.exists()) {
                marker.delete()
            }
        } catch (e: Exception) {
            e.logAsUnexpected( "Could not delete the marker file that was created for `initial-registration`.", print = logger::warn)
        }
    }

    override fun run(node: Node) {
        require(networkRootTrustStorePath.exists()) { "Network root trust store path: '$networkRootTrustStorePath' doesn't exist" }
        if (checkRegistrationMode(baseDirectory)) {
            println("Node was started before with `--initial-registration`, but the registration was not completed.\nResuming registration.")
        }
        initialRegistration(node.configuration)
    }
}
