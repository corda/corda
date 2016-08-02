package com.r3corda.standalone

import com.google.common.net.HostAndPort
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.generateKeyPair
import com.r3corda.core.node.NodeInfo
import com.r3corda.core.node.services.ServiceType
import com.r3corda.node.internal.Node
import com.r3corda.node.serialization.NodeClock
import com.r3corda.node.services.config.NodeConfiguration
import com.r3corda.node.services.messaging.ArtemisMessagingService
import com.r3corda.node.services.network.NetworkMapService
import com.r3corda.node.services.transactions.SimpleNotaryService
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import joptsimple.OptionParser
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.management.ManagementFactory
import java.net.InetAddress
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.*
import kotlin.reflect.KProperty
import kotlin.reflect.jvm.javaType

val log = LoggerFactory.getLogger("Main")

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

interface AdvertisedServiceConfig {
    val hostServiceLocally: Boolean
    val address: HostAndPort
    val identity: String
}

class AdvertisedServiceConfigImpl(conf: Config) : AdvertisedServiceConfig {
    override val hostServiceLocally: Boolean by conf
    override val address: HostAndPort by conf
    override val identity: String by conf
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
    val mapService: AdvertisedServiceConfigImpl = AdvertisedServiceConfigImpl(conf.getConfig("mapService"))
    val clock: Clock = NodeClock()

    fun createNode(): Node {
        val networkMapTarget = ArtemisMessagingService.makeRecipient(mapService.address)
        val advertisedServices = mutableSetOf<ServiceType>()
        if (mapService.hostServiceLocally) advertisedServices.add(NetworkMapService.Type)
        if (hostNotaryServiceLocally) advertisedServices.add(SimpleNotaryService.Type)
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

object ParamsSpec {
    val parser = OptionParser()

    val baseDirectoryArg =
            parser.accepts("base-directory", "The directory to put all files under")
                    .withOptionalArg()
    val configFileArg =
            parser.accepts("config-file", "The path to the config file")
                    .withOptionalArg()
}

fun main(args: Array<String>) {
    log.info("Starting Corda Node")
    val cmdlineOptions = try {
        ParamsSpec.parser.parse(*args)
    } catch (ex: Exception) {
        log.error("Unable to parse args", ex)
        System.exit(1)
        return
    }

    val baseDirectoryPath = if (cmdlineOptions.has(ParamsSpec.baseDirectoryArg)) Paths.get(cmdlineOptions.valueOf(ParamsSpec.baseDirectoryArg)) else Paths.get(".").normalize()

    val defaultConfig = ConfigFactory.parseResources("reference.conf")

    val configFile = if (cmdlineOptions.has(ParamsSpec.configFileArg)) {
        File(cmdlineOptions.valueOf(ParamsSpec.configFileArg))
    } else {
        baseDirectoryPath.resolve("node.conf").normalize().toFile()
    }
    val appConfig = ConfigFactory.parseFile(configFile)

    val cmdlineOverrideMap = HashMap<String, Any?>()
    cmdlineOverrideMap.put("basedir", baseDirectoryPath.toString())
    val overrideConfig = ConfigFactory.parseMap(cmdlineOverrideMap)

    val mergedAndResolvedConfig = overrideConfig.withFallback(appConfig).withFallback(defaultConfig).resolve()

    log.info("config:\n ${mergedAndResolvedConfig.root().render(ConfigRenderOptions.defaults())}")
    val conf = FullNodeConfiguration(mergedAndResolvedConfig)
    val dir = conf.basedir.toAbsolutePath().normalize()
    logInfo(args, dir)

    val dirFile = dir.toFile()
    if (!dirFile.exists()) {
        dirFile.mkdirs()
    }

    try {
        val node = conf.createNode()
        node.start()
        try {
            while (true) Thread.sleep(Long.MAX_VALUE)
        } catch(e: InterruptedException) {
            node.stop()
        }
    } catch (e: Exception) {
        log.error("Exception during node startup", e)
        System.exit(1)
    }
    System.exit(0)
}

private fun logInfo(args: Array<String>, dir: Path?) {
    log.info("Main class: ${FullNodeConfiguration::class.java.protectionDomain.codeSource.location.toURI().getPath()}")
    val info = ManagementFactory.getRuntimeMXBean()
    log.info("CommandLine Args: ${info.getInputArguments().joinToString(" ")}")
    log.info("Application Args: ${args.joinToString(" ")}")
    log.info("bootclasspath: ${info.bootClassPath}")
    log.info("classpath: ${info.classPath}")
    log.info("VM ${info.vmName} ${info.vmVendor} ${info.vmVersion}")
    log.info("Machine: ${InetAddress.getLocalHost().hostName}")
    log.info("Working Directory: ${dir}")
}

