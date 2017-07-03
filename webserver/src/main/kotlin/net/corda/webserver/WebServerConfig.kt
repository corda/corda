package net.corda.webserver

import com.typesafe.config.Config
import net.corda.core.utilities.Authority
import net.corda.nodeapi.config.NodeSSLConfiguration
import net.corda.nodeapi.config.getValue
import java.nio.file.Path

/**
 * [baseDirectory] is not retrieved from the config file but rather from a command line argument.
 */
class WebServerConfig(override val baseDirectory: Path, val config: Config) : NodeSSLConfiguration {
    override val keyStorePassword: String by config
    override val trustStorePassword: String by config
    val exportJMXto: String get() = "http"
    val useHTTPS: Boolean by config
    val myLegalName: String by config
    val p2pAddress: Authority by config // TODO: Use RPC port instead of P2P port (RPC requires authentication, P2P does not)
    val webAddress: Authority by config
}
