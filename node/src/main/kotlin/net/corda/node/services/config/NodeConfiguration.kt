package net.corda.node.services.config

import com.google.common.net.HostAndPort
import com.typesafe.config.Config
import net.corda.core.div
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.node.services.ServiceInfo
import net.corda.node.internal.Node
import net.corda.node.serialization.NodeClock
import net.corda.node.services.User
import net.corda.node.services.messaging.NodeMessagingClient
import net.corda.node.services.network.NetworkMapService
import net.corda.node.utilities.TestClock
import java.nio.file.Path
import java.util.*

interface NodeSSLConfiguration {
    val keyStorePassword: String
    val trustStorePassword: String
    val certificatesPath: Path
    val keyStorePath: Path get() = certificatesPath / "sslkeystore.jks"
    val trustStorePath: Path get() = certificatesPath / "truststore.jks"
}

interface NodeConfiguration : NodeSSLConfiguration {
    val basedir: Path
    override val certificatesPath: Path get() = basedir / "certificates"
    val myLegalName: String
    val nearestCity: String
    val emailAddress: String
    val exportJMXto: String
    val dataSourceProperties: Properties get() = Properties()
    val devMode: Boolean
}

class FullNodeConfiguration(val config: Config) : NodeConfiguration {
    override val basedir: Path by config
    override val myLegalName: String by config
    override val nearestCity: String by config
    override val emailAddress: String by config
    override val exportJMXto: String = "http"
    override val keyStorePassword: String by config
    override val trustStorePassword: String by config
    override val dataSourceProperties: Properties by config
    override val devMode: Boolean by config.getOrElse { false }
    val networkMapAddress: HostAndPort? by config.getOrElse { null }
    val useHTTPS: Boolean by config
    val artemisAddress: HostAndPort by config
    val webAddress: HostAndPort by config
    val messagingServerAddress: HostAndPort? by config.getOrElse { null }
    val extraAdvertisedServiceIds: String by config
    val useTestClock: Boolean by config.getOrElse { false }
    val notaryNodeAddress: HostAndPort? by config.getOrElse { null }
    val notaryClusterAddresses: List<HostAndPort> = config.getListOrElse<String>("notaryClusterAddresses") { emptyList<String>() }.map { HostAndPort.fromString(it) }
    val rpcUsers: List<User> =
            config.getListOrElse<Config>("rpcUsers") { emptyList() }
            .map {
                val username = it.getString("user")
                require(username.matches("\\w+".toRegex())) { "Username $username contains invalid characters" }
                val password = it.getString("password")
                val permissions = it.getListOrElse<String>("permissions") { emptyList() }.toSet()
                User(username, password, permissions)
            }

    fun createNode(): Node {
        // This is a sanity feature do not remove.
        require(!useTestClock || devMode) { "Cannot use test clock outside of dev mode" }

        val advertisedServices = mutableSetOf<ServiceInfo>()
        if (!extraAdvertisedServiceIds.isNullOrEmpty()) {
            for (serviceId in extraAdvertisedServiceIds.split(",")) {
                advertisedServices.add(ServiceInfo.parse(serviceId))
            }
        }
        if (networkMapAddress == null) advertisedServices.add(ServiceInfo(NetworkMapService.type))
        val networkMapMessageAddress: SingleMessageRecipient? = if (networkMapAddress == null) null else NodeMessagingClient.makeNetworkMapAddress(networkMapAddress!!)
        return Node(this, networkMapMessageAddress, advertisedServices, if(useTestClock == true) TestClock() else NodeClock())
    }
}

