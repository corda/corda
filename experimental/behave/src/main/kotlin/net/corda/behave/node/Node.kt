/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.behave.node

import net.corda.behave.database.DatabaseConnection
import net.corda.behave.database.DatabaseType
import net.corda.behave.file.LogSource
import net.corda.behave.file.currentDirectory
import net.corda.behave.file.stagingRoot
import net.corda.behave.monitoring.PatternWatch
import net.corda.behave.node.configuration.*
import net.corda.behave.process.JarCommand
import net.corda.behave.process.JarCommandWithMain
import net.corda.behave.service.Service
import net.corda.behave.service.ServiceSettings
import net.corda.behave.service.proxy.CordaRPCProxyClient
import net.corda.behave.ssh.MonitoringSSHClient
import net.corda.behave.ssh.SSHClient
import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCClientConfiguration
import net.corda.core.internal.copyTo
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.core.internal.exists
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.minutes
import net.corda.core.utilities.seconds
import org.apache.commons.io.FileUtils
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.concurrent.CountDownLatch

/**
 * Corda node.
 */
class Node(
        val config: Configuration,
        private val rootDirectory: Path = currentDirectory,
        private val settings: ServiceSettings = ServiceSettings(),
        val rpcProxy: Boolean = false,
        val networkType: Distribution.Type
) {

    private val log = loggerFor<Node>()

    private val runtimeDirectory = rootDirectory / config.name

    private val logDirectory = runtimeDirectory / "logs"

    private val command = JarCommand(
            runtimeDirectory / "corda.jar",
            arrayOf("--config", "node.conf", "--log-to-console", "--logging-level", "DEBUG"),
            runtimeDirectory,
            settings.timeout,
            enableRemoteDebugging = false
    )

    private val isAliveLatch = PatternWatch(command.output, "Node for \".*\" started up and registered")
    private val isNetworkMapReadyLatch = PatternWatch(command.output, "Done adding node with info")

    private var isConfigured = false

    private val serviceDependencies = mutableListOf<Service>()

    private var isStarted = false

    private var haveDependenciesStarted = false

    private var haveDependenciesStopped = false

    fun configure() {
        if (isConfigured) {
            return
        }
        isConfigured = true
        log.info("Configuring {} ...", this)
        serviceDependencies.addAll(config.database.type.dependencies(config))
        config.distribution.ensureAvailable()
        val nodeDirectory = runtimeDirectory.createDirectories()
        config.writeToFile(nodeDirectory / "node.conf")
        // Copy jar to node folder, the bootstrapper will use this instead of the master jar.
        config.distribution.cordaJar.copyTo(nodeDirectory / "corda.jar", StandardCopyOption.REPLACE_EXISTING)
        installApps()
        installDatabaseDriver(config.database.type)
    }

    private fun installDatabaseDriver(type: DatabaseType) {
        if (type.driverJar != null) {
            val driversDir = runtimeDirectory / "drivers"
            log.info("Creating directory for drivers: $driversDir")
            driversDir.toFile().mkdirs()

            val driverPath = stagingRoot / "drivers" / type.driverJar
            log.info("Copying database drivers from $driverPath to $driversDir")
            Files.copy(driverPath, driversDir / type.driverJar, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun initialiseDatabase(database: DatabaseConfiguration) {
        val driversDir = runtimeDirectory / "drivers"
        log.info("Creating directory for drivers: $driversDir")
        driversDir.toFile().mkdirs()
        log.info("Initialising database for Corda Enterprise node: $database")
        val command = JarCommandWithMain(listOf(config.distribution.dbMigrationJar, rootDirectory / "libs" / database.type.driverJar!!),
                "com.r3.corda.dbmigration.DBMigration",
                arrayOf("--base-directory", "$runtimeDirectory", "--execute-migration"),
                runtimeDirectory, 2.minutes)
        command.run()
    }

    fun start(): Boolean {
        if (!startDependencies()) {
            return false
        }
        log.info("Starting {} ...", this)
        return try {
            // initialise database via DB migration tool
            if (config.distribution.type == Distribution.Type.CORDA_ENTERPRISE &&
                    config.database.type != DatabaseType.H2) {
                initialiseDatabase(config.database)
            }
            // launch node itself
            command.start()
            isStarted = true
            true
        } catch (e: Exception) {
            log.warn("Failed to start {}", this)
            e.printStackTrace()
            false
        }
    }

    fun waitUntilRunning(waitDuration: Duration = settings.timeout): Boolean {
        val ok = isAliveLatch.await(waitDuration)
                && isNetworkMapReadyLatch.await(waitDuration)
        if (!ok) {
            log.warn("{} did not start up as expected within the given time frame", this)
        } else {
            log.info("{} is running and ready for incoming connections", this)
        }
        return ok
    }

    fun shutDown(): Boolean {
        return try {
            if (isStarted) {
                log.info("Shutting down {} ...", this)
                command.kill()
            }
            stopDependencies()
            true
        } catch (e: Exception) {
            log.warn("Failed to shut down {} cleanly", this)
            e.printStackTrace()
            false
        }
    }

    val logOutput: LogSource by lazy {
        val hostname = InetAddress.getLocalHost().hostName
        LogSource(logDirectory, "node-$hostname.*.log")
    }

    val database: DatabaseConnection by lazy {
        DatabaseConnection(config.database, config.databaseType.settings.template)
    }

    fun ssh(
            exitLatch: CountDownLatch? = null,
            clientLogic: (MonitoringSSHClient) -> Unit
    ) {
        Thread(Runnable {
            val network = config.nodeInterface
            val user = config.users.first()
            val client = SSHClient.connect(network.sshPort, user.password, username = user.username)
            MonitoringSSHClient(client).use {
                log.info("Connected to {} over SSH", this)
                clientLogic(it)
                log.info("Disconnecting from {} ...", this)
                it.writeLine("bye")
                exitLatch?.countDown()
            }
        }).start()
    }

    fun <T> rpc(action: (CordaRPCOps) -> T): T {
        var result: T? = null
        val user = config.users.first()
        val address = config.nodeInterface
        val targetHost = NetworkHostAndPort(address.host, address.rpcPort)
        val config = CordaRPCClientConfiguration.DEFAULT.copy(
            connectionMaxRetryInterval = 10.seconds
        )
        log.info("Establishing RPC connection to ${targetHost.host} on port ${targetHost.port} ...")
        CordaRPCClient(targetHost, config).use(user.username, user.password) {
            log.info("RPC connection to ${targetHost.host}:${targetHost.port} established")
            val client = it.proxy
            result = action(client)
        }
        return result ?: error("Failed to run RPC action")
    }

    fun <T> http(action: (CordaRPCOps) -> T): T {
        val address = config.nodeInterface
        val targetHost = NetworkHostAndPort(address.host, address.rpcProxy)
        log.info("Establishing HTTP connection to ${targetHost.host} on port ${targetHost.port} ...")
        try {
            return action(CordaRPCProxyClient(targetHost))
        } catch (e: Exception) {
            log.warn("Failed to invoke http endpoint: ", e)
            e.printStackTrace()
            error("Failed to run http action")
        }
    }

    override fun toString(): String {
        return "Node(name = ${config.name}, version = ${config.distribution.version})"
    }

    fun startDependencies(): Boolean {
        if (haveDependenciesStarted) {
            return true
        }
        haveDependenciesStarted = true

        if (serviceDependencies.isEmpty()) {
            return true
        }

        log.info("Starting dependencies for {} ...", this)
        val latch = CountDownLatch(serviceDependencies.size)
        var failed = false
        serviceDependencies.parallelStream().forEach {
            val wasStarted = it.start()
            latch.countDown()
            if (!wasStarted) {
                failed = true
            }
        }
        latch.await()
        return if (!failed) {
            log.info("Dependencies started for {}", this)
            true
        } else {
            log.warn("Failed to start one or more dependencies for {}", this)
            false
        }
    }

    private fun stopDependencies() {
        if (haveDependenciesStopped) {
            return
        }
        haveDependenciesStopped = true

        if (serviceDependencies.isEmpty()) {
            return
        }

        log.info("Stopping dependencies for {} ...", this)
        val latch = CountDownLatch(serviceDependencies.size)
        serviceDependencies.parallelStream().forEach {
            it.stop()
            latch.countDown()
        }
        latch.await()
        log.info("Dependencies stopped for {}", this)
    }

    private fun installApps() {
        val appDirectory = config.distribution.cordappDirectory
        if (appDirectory.exists()) {
            val targetAppDirectory = runtimeDirectory / "cordapps"
            FileUtils.copyDirectory(appDirectory.toFile(), targetAppDirectory.toFile())
        }
    }

    class Builder {

        var name: String? = null
            private set

        private var distribution = Distribution.MASTER

        private var databaseType = DatabaseType.H2

        private var notaryType = NotaryType.NONE

        private var compatibilityZoneURL: String? = null

        private val issuableCurrencies = mutableListOf<String>()

        private var location: String = "London"

        private var country: String = "GB"

        private val apps = mutableListOf<String>()

        private var includeFinance = false

        private var directory: Path? = null

        private var timeout = Duration.ofSeconds(60)

        private var rpcProxy = false

        var networkType = distribution.type

        fun withName(newName: String): Builder {
            name = newName
            return this
        }

        fun withDistribution(newDistribution: Distribution): Builder {
            distribution = newDistribution
            networkType = distribution.type
            return this
        }

        fun withDatabaseType(newDatabaseType: DatabaseType): Builder {
            databaseType = newDatabaseType
            return this
        }

        fun withNotaryType(newNotaryType: NotaryType): Builder {
            notaryType = newNotaryType
            return this
        }

        fun withNetworkMap(newCompatibilityZoneURL: String?): Builder {
            compatibilityZoneURL = newCompatibilityZoneURL
            return this
        }

        fun withNetworkType(newNetworkType: Distribution.Type): Builder {
            networkType = newNetworkType
            return this
        }

        fun withIssuableCurrencies(vararg currencies: String): Builder {
            issuableCurrencies.addAll(currencies)
            return this
        }

        fun withIssuableCurrencies(currencies: List<String>): Builder {
            issuableCurrencies.addAll(currencies)
            return this
        }

        fun withLocation(location: String, country: String): Builder {
            this.location = location
            this.country = country
            return this
        }

        fun withFinanceApp(): Builder {
            includeFinance = true
            return this
        }

        fun withApp(app: String): Builder {
            apps.add(app)
            return this
        }

        fun withDirectory(newDirectory: Path): Builder {
            directory = newDirectory
            return this
        }

        fun withTimeout(newTimeout: Duration): Builder {
            timeout = newTimeout
            return this
        }

        fun withRPCProxy(withRPCProxy: Boolean): Builder {
            rpcProxy = withRPCProxy
            return this
        }

        fun build(): Node {
            val name = name ?: error("Node name not set")
            val directory = directory ?: error("Runtime directory not set")
            // TODO: rework how we use the Doorman/NMS (now these are a separate product / distribution)
            val compatibilityZoneURL = if (networkType == Distribution.Type.CORDA_ENTERPRISE && System.getProperty("USE_NETWORK_SERVICES") != null) {
                "http://localhost:1300"         // TODO: add additional USE_NETWORK_SERVICES_URL to specify location of existing operational environment to use.
            } else {
                null
            }
            return Node(
                    Configuration(
                            name,
                            distribution,
                            databaseType,
                            location = location,
                            country = country,
                            notary = NotaryConfiguration(notaryType),
                            cordapps = CordappConfiguration(
                                    apps = apps,
                                    includeFinance = includeFinance
                            ),
                            configElements = *arrayOf(
                                    NotaryConfiguration(notaryType),
                                    NetworkMapConfiguration(compatibilityZoneURL),
                                    CurrencyConfiguration(issuableCurrencies)
                            )
                    ),
                    directory,
                    ServiceSettings(timeout),
                    rpcProxy = rpcProxy,
                    networkType = networkType
            )
        }

        private fun <T> error(message: String): T {
            throw IllegalArgumentException(message)
        }
    }

    companion object {

        fun new() = Builder()

    }

}