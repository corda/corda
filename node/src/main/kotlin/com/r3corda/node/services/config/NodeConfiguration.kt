package com.r3corda.node.services.config

import com.google.common.net.HostAndPort
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.generateKeyPair
import com.r3corda.core.node.NodeInfo
import com.r3corda.core.node.services.ServiceType
import com.r3corda.node.internal.Node
import com.r3corda.node.serialization.NodeClock
import com.r3corda.node.services.messaging.ArtemisMessagingService
import com.r3corda.node.services.network.NetworkMapService
import com.r3corda.node.services.transactions.SimpleNotaryService
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlin.reflect.KProperty
import kotlin.reflect.jvm.javaType

interface NodeConfiguration {
    val myLegalName: String
    val exportJMXto: String
    val nearestCity: String
    val keyStorePassword: String
    val trustStorePassword: String
}

@Suppress("UNCHECKED_CAST")
operator fun <T> Config.getValue(receiver: Any, metadata: KProperty<*>): T {
    return when (metadata.returnType.javaType) {
        String::class.java -> getString(metadata.name) as T
        Int::class.java -> getInt(metadata.name) as T
        Long::class.java -> getLong(metadata.name) as T
        Double::class.java -> getDouble(metadata.name) as T
        Boolean::class.java -> getBoolean(metadata.name) as T
        LocalDate::class.java -> LocalDate.parse(getString(metadata.name)) as T
        Instant::class.java -> Instant.parse(getString(metadata.name)) as T
        HostAndPort::class.java -> HostAndPort.fromString(getString(metadata.name)) as T
        Path::class.java -> Paths.get(getString(metadata.name)) as T
        else -> throw IllegalArgumentException("Unsupported type ${metadata.returnType}")
    }
}

class NodeConfigurationFromConfig(val config: Config = ConfigFactory.load()) : NodeConfiguration {
    override val myLegalName: String by config
    override val exportJMXto: String by config
    override val nearestCity: String by config
    override val keyStorePassword: String by config
    override val trustStorePassword: String by config
}

class NameServiceConfig(conf: Config) {
    val hostServiceLocally: Boolean by conf
    val address: HostAndPort by conf
    val identity: String by conf
}

class FullNodeConfiguration(conf: Config) : NodeConfiguration {
    val basedir: Path by conf
    override val myLegalName: String by conf
    override val nearestCity: String by conf
    override val exportJMXto: String = "http"
    override val keyStorePassword: String by conf
    override val trustStorePassword: String by conf
    val artemisAddress: HostAndPort by conf
    val webAddress: HostAndPort by conf
    val hostNotaryServiceLocally: Boolean by conf
    val extraAdvertisedServiceIds: String by conf
    val mapService: NameServiceConfig = NameServiceConfig(conf.getConfig("mapService"))
    val clock: Clock = NodeClock()

    fun createNode(): Node {
        val networkMapTarget = ArtemisMessagingService.makeRecipient(mapService.address)
        val advertisedServices = mutableSetOf<ServiceType>()
        if (mapService.hostServiceLocally) advertisedServices.add(NetworkMapService.Type)
        if (hostNotaryServiceLocally) advertisedServices.add(SimpleNotaryService.Type)
        if (!extraAdvertisedServiceIds.isNullOrEmpty()) {
            for (serviceId in extraAdvertisedServiceIds.split(",")) {
                advertisedServices.add(object : ServiceType(serviceId) {})
            }
        }
        // TODO Node startup should not need a full NodeInfo for the remote NetworkMapService provider as bootstrap
        val networkMapBootstrapIdentity = Party(mapService.identity, generateKeyPair().public)
        val networkMapAddress: NodeInfo? = if (mapService.hostServiceLocally) null else NodeInfo(networkMapTarget, networkMapBootstrapIdentity, setOf(NetworkMapService.Type))
        return Node(basedir.toAbsolutePath().normalize(),
                artemisAddress,
                webAddress,
                this,
                networkMapAddress,
                advertisedServices,
                clock
        )
    }
}