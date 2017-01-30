package net.corda.demobench.model

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValue
import com.typesafe.config.ConfigValueFactory
import java.lang.String.join

class NodeConfig(
        legalName: String,
        artemisPort: Int,
        nearestCity: String,
        webPort: Int,
        h2Port: Int,
        extraServices: List<String>
) : NetworkMapConfig(legalName, artemisPort) {

    private var nearestCityName: String = nearestCity
    val nearestCity : String
        get() { return nearestCityName }

    private var webPortValue: Int = webPort
    val webPort : Int
        get() { return webPortValue }

    private var h2PortValue: Int = h2Port
    val h2Port : Int
       get() { return h2PortValue }

    private var extraServicesValue: List<String> = extraServices
    val extraServices : List<String>
        get() { return extraServicesValue }

    private var networkMapValue: NetworkMapConfig? = null
    var networkMap : NetworkMapConfig?
        get() { return networkMapValue }
        set(value) { networkMapValue = value }

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
                    .withValue("rpcUsers", valueFor(listOf<String>()))
                    .withValue("h2port", valueFor(h2Port))
                    .withValue("useTestClock", valueFor(true))

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
