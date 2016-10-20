package com.r3corda.node.services.config

import com.google.common.net.HostAndPort
import com.r3corda.core.div
import com.r3corda.core.messaging.SingleMessageRecipient
import com.r3corda.core.node.services.ServiceInfo
import com.r3corda.node.internal.Node
import com.r3corda.node.serialization.NodeClock
import com.r3corda.node.services.messaging.NodeMessagingClient
import com.r3corda.node.services.network.NetworkMapService
import com.typesafe.config.Config
import java.nio.file.Path
import java.time.Clock
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

class FullNodeConfiguration(config: Config) : NodeConfiguration {
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
    val clockClass: String? by config.getOrElse { null }

    fun createNode(): Node {
        val advertisedServices = mutableSetOf<ServiceInfo>()
        if (!extraAdvertisedServiceIds.isNullOrEmpty()) {
            for (serviceId in extraAdvertisedServiceIds.split(",")) {
                advertisedServices.add(ServiceInfo.parse(serviceId))
            }
        }
        if (networkMapAddress == null) advertisedServices.add(ServiceInfo(NetworkMapService.type))
        val networkMapMessageAddress: SingleMessageRecipient? = if (networkMapAddress == null) null else NodeMessagingClient.makeNetworkMapAddress(networkMapAddress!!)
        return if(clockClass != null) {
             Node(this, networkMapMessageAddress, advertisedServices, Class.forName(clockClass).newInstance() as Clock)
        } else {
            Node(this, networkMapMessageAddress, advertisedServices)
        }
    }
}

