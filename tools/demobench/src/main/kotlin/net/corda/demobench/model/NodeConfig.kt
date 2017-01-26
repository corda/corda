package net.corda.demobench.model

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValue
import com.typesafe.config.ConfigValueFactory

class NodeConfig(legalName: String, nearestCity: String, p2pPort: Int, artemisPort: Int, webPort: Int) {

    private var keyValue: String = toKey(legalName)
    val key : String
        get() { return keyValue }

    private var legalNameValue: String = legalName
    val legalName : String
        get() { return legalNameValue }

    private var nearestCityName: String = nearestCity
    val nearestCity : String
        get() { return nearestCityName }

    private var p2pPortValue: Int = p2pPort
    val p2pPort : Int
        get() { return p2pPortValue }

    private var artemisPortValue: Int = artemisPort
    val artemisPort : Int
        get() { return artemisPortValue }

    private var webPortValue: Int = webPort
    val webPort : Int
        get() { return webPortValue }

    private fun toKey(value: String): String {
        return value.replace("\\s++", "").toLowerCase()
    }

    val toFileConfig : Config
        get() = ConfigFactory.empty()
                    .withValue("myLegalName", valueFor(legalName))
                    .withValue("nearestCity", valueFor(nearestCity))
                    .withValue("extraAdvertisedServiceIds", valueFor(""))
                    .withFallback(ConfigFactory.empty()
                        .withValue("address", addressValueFor(p2pPort))
                        .withValue("legalName", valueFor("Notary"))
                        .atPath("networkMapService")
                    )
                    .withValue("artemisAddress", addressValueFor(artemisPort))
                    .withValue("webAddress", addressValueFor(webPort))
                    .withValue("rpcUsers", valueFor(listOf<String>()))
                    .withValue("useTestClock", valueFor(true))

}

private fun <T> valueFor(any: T): ConfigValue? {
    return ConfigValueFactory.fromAnyRef(any)
}

private fun addressValueFor(port: Int): ConfigValue? {
    return valueFor("localhost:%d".format(port))
}
