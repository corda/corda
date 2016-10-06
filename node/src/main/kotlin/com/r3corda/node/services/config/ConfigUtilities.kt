package com.r3corda.node.services.config

import com.google.common.net.HostAndPort
import com.r3corda.core.crypto.X509Utilities
import com.r3corda.core.utilities.loggerFor
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import com.typesafe.config.ConfigRenderOptions
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.time.LocalDate
import java.util.*
import kotlin.reflect.KProperty
import kotlin.reflect.jvm.javaType

object ConfigHelper {
    val log = loggerFor<ConfigHelper>()

    fun loadConfig(baseDirectoryPath: Path,
                   configFileOverride: Path? = null,
                   allowMissingConfig: Boolean = false,
                   configOverrides: Map<String, Any?> = emptyMap()): Config {

        val defaultConfig = ConfigFactory.parseResources("reference.conf", ConfigParseOptions.defaults().setAllowMissing(false))

        val normalisedBaseDir = baseDirectoryPath.normalize()
        val configFile = (configFileOverride?.normalize() ?: normalisedBaseDir.resolve("node.conf")).toFile()
        val appConfig = ConfigFactory.parseFile(configFile, ConfigParseOptions.defaults().setAllowMissing(allowMissingConfig))

        val overridesMap = HashMap<String, Any?>() // If we do require a few other command line overrides eg for a nicer development experience they would go inside this map.
        overridesMap.putAll(configOverrides)
        overridesMap["basedir"] = normalisedBaseDir.toAbsolutePath().toString()
        val overrideConfig = ConfigFactory.parseMap(overridesMap)

        val mergedAndResolvedConfig = overrideConfig.withFallback(appConfig).withFallback(defaultConfig).resolve()
        log.info("Config:\n ${mergedAndResolvedConfig.root().render(ConfigRenderOptions.defaults())}")
        return mergedAndResolvedConfig
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
        URL::class.java -> URL(getString(metadata.name)) as T
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

/**
 * Strictly for dev only automatically construct a server certificate/private key signed from
 * the CA certs in Node resources. Then provision KeyStores into certificates folder under node path.
 */
fun NodeSSLConfiguration.configureWithDevSSLCertificate() {
    Files.createDirectories(certificatesPath)
    if (!Files.exists(trustStorePath)) {
        Files.copy(javaClass.classLoader.getResourceAsStream("com/r3corda/node/internal/certificates/cordatruststore.jks"),
                trustStorePath)
    }
    if (!Files.exists(keyStorePath)) {
        val caKeyStore = X509Utilities.loadKeyStore(
                javaClass.classLoader.getResourceAsStream("com/r3corda/node/internal/certificates/cordadevcakeys.jks"),
                "cordacadevpass")
        X509Utilities.createKeystoreForSSL(keyStorePath, keyStorePassword, keyStorePassword, caKeyStore, "cordacadevkeypass")
    }
}
