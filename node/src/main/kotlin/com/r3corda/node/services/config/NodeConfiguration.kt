package com.r3corda.node.services.config

import com.google.common.net.HostAndPort
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.generateKeyPair
import com.r3corda.core.node.NodeInfo
import com.r3corda.core.node.services.ServiceType
import com.r3corda.node.internal.Node
import com.r3corda.node.serialization.NodeClock
import com.r3corda.node.services.messaging.ArtemisMessagingClient
import com.r3corda.node.services.network.NetworkMapService
import com.r3corda.node.services.transactions.SimpleNotaryService
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import com.typesafe.config.ConfigRenderOptions
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.*
import kotlin.reflect.KProperty
import kotlin.reflect.jvm.javaType

interface NodeConfiguration {
    val myLegalName: String
    val exportJMXto: String
    val nearestCity: String
    val keyStorePassword: String
    val trustStorePassword: String
    val dataSourceProperties: Properties get() = Properties()

    companion object {
        val log = LoggerFactory.getLogger("NodeConfiguration")

        fun loadConfig(baseDirectoryPath: Path, configFileOverride: Path? = null, allowMissingConfig: Boolean = false, configOverrides: Map<String, Any?> = emptyMap()): Config {
            val defaultConfig = ConfigFactory.parseResources("reference.conf", ConfigParseOptions.defaults().setAllowMissing(false))

            val normalisedBaseDir = baseDirectoryPath.normalize()
            val configFile = (configFileOverride?.normalize() ?: normalisedBaseDir.resolve("node.conf")).toFile()
            val appConfig = ConfigFactory.parseFile(configFile, ConfigParseOptions.defaults().setAllowMissing(allowMissingConfig))

            val overridesMap = HashMap<String, Any?>() // If we do require a few other command line overrides eg for a nicer development experience they would go inside this map.
            overridesMap.putAll(configOverrides)
            overridesMap["basedir"] = normalisedBaseDir.toAbsolutePath().toString()
            val overrideConfig = ConfigFactory.parseMap(overridesMap)

            val mergedAndResolvedConfig = overrideConfig.withFallback(appConfig).withFallback(defaultConfig).resolve()
            log.info("config:\n ${mergedAndResolvedConfig.root().render(ConfigRenderOptions.defaults())}")
            return mergedAndResolvedConfig
        }
    }
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
        Properties::class.java -> getProperties(metadata.name) as T
        else -> throw IllegalArgumentException("Unsupported type ${metadata.returnType}")
    }
}

fun Config.getProperties(path: String): Properties {
    val obj = this.getObject(path)
    val props = Properties()
    for ((property, objectValue) in obj.entries) {
        props.setProperty(property, objectValue.unwrapped().toString())
    }
    return props
}

class NodeConfigurationFromConfig(val config: Config = ConfigFactory.load()) : NodeConfiguration {
    override val myLegalName: String by config
    override val exportJMXto: String by config
    override val nearestCity: String by config
    override val keyStorePassword: String by config
    override val trustStorePassword: String by config
    override val dataSourceProperties: Properties by config
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
    override val dataSourceProperties: Properties by conf
    val artemisAddress: HostAndPort by conf
    val webAddress: HostAndPort by conf
    val messagingServerAddress: HostAndPort? = if (conf.hasPath("messagingServerAddress")) HostAndPort.fromString(conf.getString("messagingServerAddress")) else null
    val hostNotaryServiceLocally: Boolean by conf
    val extraAdvertisedServiceIds: String by conf
    val mapService: NameServiceConfig = NameServiceConfig(conf.getConfig("mapService"))
    val clock: Clock = NodeClock()

    fun createNode(): Node {
        val networkMapTarget = ArtemisMessagingClient.makeNetworkMapAddress(mapService.address)
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
                clock,
                messagingServerAddress
        )
    }
}

