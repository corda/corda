package com.r3corda.node.services.config

import com.google.common.net.HostAndPort
import com.r3corda.core.messaging.SingleMessageRecipient
import com.r3corda.core.node.services.ServiceType
import com.r3corda.node.internal.Node
import com.r3corda.node.serialization.NodeClock
import com.r3corda.node.services.messaging.NodeMessagingClient
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

interface NodeSSLConfiguration {
    val keyStorePassword: String
    val trustStorePassword: String

    // TODO: Move cert paths into this interface as well.
}

interface NodeConfiguration : NodeSSLConfiguration {
    val myLegalName: String
    val nearestCity: String
    val emailAddress: String
    val exportJMXto: String
    val dataSourceProperties: Properties get() = Properties()
    val devMode: Boolean
    val certificateSigningService: HostAndPort

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

/**
 * Helper class for optional configurations
 */
class OptionalConfig<out T>(val conf: Config, val lambda: () -> T) {
    operator fun getValue(receiver: Any, metadata: KProperty<*>): T {
        return if (conf.hasPath(metadata.name)) conf.getValue(receiver, metadata) else lambda()
    }
}

fun <T> Config.getOrElse(lambda: () -> T): OptionalConfig<T> {
    return OptionalConfig(this, lambda)
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
    override val nearestCity: String by config
    override val emailAddress: String by config
    override val exportJMXto: String by config
    override val keyStorePassword: String by config
    override val trustStorePassword: String by config
    override val dataSourceProperties: Properties by config
    override val devMode: Boolean by config.getOrElse { false }
    override val certificateSigningService: HostAndPort by config
}

class FullNodeConfiguration(conf: Config) : NodeConfiguration {
    val basedir: Path by conf
    override val myLegalName: String by conf
    override val nearestCity: String by conf
    override val emailAddress: String by conf
    override val exportJMXto: String = "http"
    override val keyStorePassword: String by conf
    override val trustStorePassword: String by conf
    override val dataSourceProperties: Properties by conf
    override val devMode: Boolean by conf.getOrElse { false }
    override val certificateSigningService: HostAndPort by conf
    val useHTTPS: Boolean by conf
    val artemisAddress: HostAndPort by conf
    val webAddress: HostAndPort by conf
    val messagingServerAddress: HostAndPort? = if (conf.hasPath("messagingServerAddress")) HostAndPort.fromString(conf.getString("messagingServerAddress")) else null
    val networkMapAddress: HostAndPort? = if (conf.hasPath("networkMapAddress")) HostAndPort.fromString(conf.getString("networkMapAddress")) else null
    val hostNotaryServiceLocally: Boolean by conf
    val extraAdvertisedServiceIds: String by conf
    val clock: Clock = NodeClock()

    fun createNode(): Node {
        val advertisedServices = mutableSetOf<ServiceType>()
        if (hostNotaryServiceLocally) advertisedServices.add(SimpleNotaryService.Type)
        if (!extraAdvertisedServiceIds.isNullOrEmpty()) {
            for (serviceId in extraAdvertisedServiceIds.split(",")) {
                advertisedServices.add(object : ServiceType(serviceId) {})
            }
        }
        if (networkMapAddress == null) advertisedServices.add(NetworkMapService.Type)
        val networkMapMessageAddress: SingleMessageRecipient? = if (networkMapAddress == null) null else NodeMessagingClient.makeNetworkMapAddress(networkMapAddress)
        return Node(basedir.toAbsolutePath().normalize(),
                artemisAddress,
                webAddress,
                this,
                networkMapMessageAddress,
                advertisedServices,
                clock,
                messagingServerAddress
        )
    }
}

