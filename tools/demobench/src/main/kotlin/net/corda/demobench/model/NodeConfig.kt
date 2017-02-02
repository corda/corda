package net.corda.demobench.model

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValue
import com.typesafe.config.ConfigValueFactory
import net.corda.node.services.config.SSLConfiguration
import java.lang.String.join
import java.nio.file.Path

class NodeConfig(
        baseDir: Path,
        legalName: String,
        artemisPort: Int,
        val nearestCity: String,
        val webPort: Int,
        val h2Port: Int,
        val extraServices: List<String>
) : NetworkMapConfig(legalName, artemisPort) {

    val nodeDir: Path = baseDir.resolve(key)

    private var networkMapValue: NetworkMapConfig? = null
    var networkMap : NetworkMapConfig?
        get() = networkMapValue
        set(value) { networkMapValue = value }

    private val userMap: Map<String, String>
    val user: Map<String, String>
        get() = userMap

    val ssl: SSLConfiguration = object : SSLConfiguration {
        override val certificatesDirectory: Path = nodeDir.resolve("certificates")
        override val trustStorePassword: String = "trustpass"
        override val keyStorePassword: String = "cordacadevpass"
    }

    val toFileConfig : Config
        get() = ConfigFactory.empty()
                    .withValue("myLegalName", valueFor(legalName))
                    .withValue("artemisAddress", addressValueFor(artemisPort))
                    .withValue("nearestCity", valueFor(nearestCity))
                    .withValue("extraAdvertisedServiceIds", valueFor(join(",", extraServices)))
                    .withFallback(optional("networkMapService", networkMap, {
                        c, n -> c.withValue("address", addressValueFor(n.artemisPort))
                            .withValue("legalName", valueFor(n.legalName))
                    } ))
                    .withValue("webAddress", addressValueFor(webPort))
                    .withValue("rpcUsers", valueFor(listOf<Any>(user)))
                    .withValue("h2port", valueFor(h2Port))
                    .withValue("useTestClock", valueFor(true))

    init {
        userMap = mapOf(
            Pair("password", "letmein"),
            Pair("user", "guest")
        )
    }

}

private fun <T> valueFor(any: T): ConfigValue? {
    return ConfigValueFactory.fromAnyRef(any)
}

private fun addressValueFor(port: Int): ConfigValue? {
    return valueFor("localhost:%d".format(port))
}

private fun <T> optional(path: String, obj: T?, body: (c: Config, o: T) -> Config): Config {
    val config = ConfigFactory.empty()
    return if (obj == null) config else body(config, obj).atPath(path)
}
