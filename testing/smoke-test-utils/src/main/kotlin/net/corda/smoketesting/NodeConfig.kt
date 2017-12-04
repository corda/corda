package net.corda.smoketesting

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory.empty
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValue
import com.typesafe.config.ConfigValueFactory
import net.corda.core.identity.CordaX500Name
import net.corda.nodeapi.internal.config.User

class NodeConfig(
        val legalName: CordaX500Name,
        val p2pPort: Int,
        val rpcPort: Int,
        val webPort: Int,
        val isNotary: Boolean,
        val users: List<User>,
        var networkMap: NodeConfig? = null
) {
    companion object {
        val renderOptions: ConfigRenderOptions = ConfigRenderOptions.defaults().setOriginComments(false)
    }

    val commonName: String get() = legalName.organisation

    /*
     * The configuration object depends upon the networkMap,
     * which is mutable.
     */
    //TODO Make use of Any.toConfig
    private fun toFileConfig(): Config {
        val config = empty()
                .withValue("myLegalName", valueFor(legalName.toString()))
                .withValue("p2pAddress", addressValueFor(p2pPort))
                .withFallback(optional("networkMapService", networkMap, { c, n ->
                    c.withValue("address", addressValueFor(n.p2pPort))
                            .withValue("legalName", valueFor(n.legalName.toString()))
                }))
                .withValue("webAddress", addressValueFor(webPort))
                .withValue("rpcAddress", addressValueFor(rpcPort))
                .withValue("rpcUsers", valueFor(users.map(User::toMap).toList()))
                .withValue("useTestClock", valueFor(true))
        return if (isNotary) {
            config.withValue("notary", ConfigValueFactory.fromMap(mapOf("validating" to true)))
        } else {
            config
        }
    }

    fun toText(): String = toFileConfig().root().render(renderOptions)

    private fun <T> valueFor(any: T): ConfigValue? = ConfigValueFactory.fromAnyRef(any)
    private fun addressValueFor(port: Int) = valueFor("localhost:$port")
    private inline fun <T> optional(path: String, obj: T?, body: (Config, T) -> Config): Config {
        return if (obj == null) empty() else body(empty(), obj).atPath(path)
    }
}
