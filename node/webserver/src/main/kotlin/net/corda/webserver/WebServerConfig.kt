package net.corda.webserver

import com.google.common.net.HostAndPort
import com.typesafe.config.Config
import net.corda.core.div
import net.corda.nodeapi.User
import net.corda.nodeapi.config.SSLConfiguration
import net.corda.nodeapi.config.getListOrElse
import net.corda.nodeapi.config.getValue
import java.nio.file.Path

/**
 * [baseDirectory] is not retrieved from the config file but rather from a command line argument.
 */
class WebServerConfig(val baseDirectory: Path, val config: Config) : SSLConfiguration {
    override val certificatesDirectory: Path get() = baseDirectory / "certificates"
    override val keyStorePassword: String by config
    override val trustStorePassword: String by config
    val myLegalName: String by config
    val exportJMXto: String get() = "http"
    val rpcUsers: List<User> = config
            .getListOrElse<Config>("rpcUsers") { emptyList() }
            .map {
                val username = it.getString("user")
                require(username.matches("\\w+".toRegex())) { "Username $username contains invalid characters" }
                val password = it.getString("password")
                val permissions = it.getListOrElse<String>("permissions") { emptyList() }.toSet()
                User(username, password, permissions)
            }
    val useHTTPS: Boolean by config
    val p2pAddress: HostAndPort by config
    val rpcAddress: HostAndPort? by config
    val webAddress: HostAndPort by config
}