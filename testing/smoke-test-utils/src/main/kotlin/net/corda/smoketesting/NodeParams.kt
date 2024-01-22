package net.corda.smoketesting

import com.typesafe.config.ConfigFactory.empty
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValue
import com.typesafe.config.ConfigValueFactory
import net.corda.client.rpc.CordaRPCClientConfiguration
import net.corda.core.identity.CordaX500Name
import net.corda.nodeapi.internal.config.User
import net.corda.nodeapi.internal.config.toConfig
import java.nio.file.Path

class NodeParams @JvmOverloads constructor(
        val legalName: CordaX500Name,
        val p2pPort: Int,
        val rpcPort: Int,
        val rpcAdminPort: Int,
        val users: List<User>,
        val cordappJars: List<Path> = emptyList(),
        val clientRpcConfig: CordaRPCClientConfiguration = CordaRPCClientConfiguration.DEFAULT,
        val devMode: Boolean = true,
        val version: String? = null
) {
    companion object {
        val renderOptions: ConfigRenderOptions = ConfigRenderOptions.defaults().setOriginComments(false)
    }

    val commonName: String get() = legalName.organisation

    fun createNodeConfig(isNotary: Boolean): String {
        val config = empty()
                .withValue("myLegalName", valueFor(legalName.toString()))
                .withValue("p2pAddress", addressValueFor(p2pPort))
                .withValue("rpcSettings", empty()
                        .withValue("address", addressValueFor(rpcPort))
                        .withValue("adminAddress", addressValueFor(rpcAdminPort))
                        .root())
                .withValue("rpcUsers", valueFor(users.map { it.toConfig().root().unwrapped() }.toList()))
                .withValue("useTestClock", valueFor(true))
                .withValue("devMode", valueFor(devMode))
        return if (isNotary) {
            config.withValue("notary", ConfigValueFactory.fromMap(mapOf("validating" to true)))
        } else {
            config
        }.root().render(renderOptions)
    }

    private fun <T> valueFor(any: T): ConfigValue? = ConfigValueFactory.fromAnyRef(any)

    private fun addressValueFor(port: Int) = valueFor("localhost:$port")
}
