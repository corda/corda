package net.corda.webserver

import com.typesafe.config.Config
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.config.NodeSSLConfiguration
import net.corda.nodeapi.internal.config.User
import net.corda.nodeapi.internal.config.getValue
import net.corda.nodeapi.internal.config.parseAs
import java.nio.file.Path

/**
 * [baseDirectory] is not retrieved from the config file but rather from a command line argument.
 */
class WebServerConfig(override val baseDirectory: Path, val config: Config, val runAs: User) : NodeSSLConfiguration {
    override val keyStorePassword: String by config
    override val trustStorePassword: String by config
    val exportJMXto: String get() = "http"
    val useHTTPS: Boolean by config
    val myLegalName: String by config
    val rpcAddress: NetworkHostAndPort by lazy {
        if (config.hasPath("rpcSettings.address")) {
            return@lazy NetworkHostAndPort.parse(config.getConfig("rpcSettings").getString("address"))
        }
        if (config.hasPath("rpcAddress")) {
            return@lazy NetworkHostAndPort.parse(config.getString("rpcAddress"))
        }
        throw Exception("Missing rpc address property. Either 'rpcSettings' or 'rpcAddress' must be specified.")
    }
    val webAddress: NetworkHostAndPort by config

    // Use credentials of first user defined in configuration.
    // TODO: implement proper login procedure and remove this constructor.
    constructor(baseDirectory: Path, config: Config): this(
            baseDirectory = baseDirectory,
            config = config,
            runAs = defaultRunAs(config))

    private companion object {

        fun Config.getListOrNull(path: String): List<Config>? {
            return if (hasPath(path)) {
                getConfigList(path)
            } else null
        }

        fun defaultRunAs(config: Config) : User {
            val users = config.getListOrNull("security.authService.dataSource.users") ?:
                    config.getListOrNull("rpcUsers") ?:
                    throw IllegalArgumentException("Cannot find any user credential in config file")

            return users.first().parseAs()
        }
    }
}

