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

    private val userMap: Map<String, Any>
    val user: Map<String, Any>
        get() = userMap

    val ssl: SSLConfiguration = object : SSLConfiguration {
        override val certificatesDirectory: Path = nodeDir.resolve("certificates")
        override val trustStorePassword: String = "trustpass"
        override val keyStorePassword: String = "cordacadevpass"
    }

    val toFileConfig: Config
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

    val isCashIssuer: Boolean
        get() = extraServices.any {
                    it.startsWith("corda.issuer.")
                }

    init {
        userMap = mapOf<String, Any>(
                "password" to "letmein",
                "user" to "guest",
                "permissions" to listOf(
                    "StartFlow.net.corda.flows.CashFlow",
                    "StartFlow.net.corda.flows.IssuerFlow\$IssuanceRequester"
                )
        )
    }

}

private fun <T> valueFor(any: T): ConfigValue? = ConfigValueFactory.fromAnyRef(any)

private fun addressValueFor(port: Int) = valueFor("localhost:$port")

private fun <T> optional(path: String, obj: T?, body: (Config, T) -> Config): Config {
    val config = ConfigFactory.empty()
    return if (obj == null) config else body(config, obj).atPath(path)
}
