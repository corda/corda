package net.corda.demobench.model

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory.empty
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValue
import com.typesafe.config.ConfigValueFactory
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.VisibleForTesting
import net.corda.core.internal.copyToDirectory
import net.corda.core.internal.createDirectories
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.config.User
import net.corda.nodeapi.internal.config.toConfig
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties

/**
 * This is a subset of FullNodeConfiguration, containing only those configs which we need. The node uses corda-reference.conf
 * to fill in the defaults so we're not required to specify them here.
 */
data class NodeConfig(
        val myLegalName: CordaX500Name,
        val p2pAddress: NetworkHostAndPort,
        val rpcSettings: NodeRpcSettings,
        /** This is not used by the node but by the webserver which looks at node.conf. */
        val webAddress: NetworkHostAndPort,
        val notary: NotaryService?,
        val h2port: Int,
        val rpcUsers: List<User> = listOf(defaultUser),
        /** This is an extra config used by the Cash app. */
        val issuableCurrencies: List<String> = emptyList(),
        /** Pass-through for generating node.conf with external DB */
        val dataSourceProperties: Properties? = null,
        val database: Properties? = null,
        val systemProperties: Map<String, Any?>,
        private val devMode: Boolean = true,
        private val detectPublicIp: Boolean = false,
        private val useTestClock: Boolean = true
) {
    companion object {
        val renderOptions: ConfigRenderOptions = ConfigRenderOptions.defaults().setOriginComments(false)
        val defaultUser = user("guest")
        const val CORDAPP_DIR_NAME = "cordapps"
    }

    @VisibleForTesting
    internal fun nodeConf(): Config {
        val rpcSettings: ConfigObject = empty()
            .withValue("address", valueFor(rpcSettings.address.toString()))
            .withValue("adminAddress", valueFor(rpcSettings.adminAddress.toString()))
            .root()
        return NodeConfigurationData(
                myLegalName = myLegalName,
                p2pAddress = p2pAddress,
                rpcAddress = this.rpcSettings.address,
                notary = notary,
                h2port = h2port,
                rpcUsers = rpcUsers,
                useTestClock = useTestClock,
                detectPublicIp = detectPublicIp,
                devMode = devMode)
            .toConfig()
            .withValue("systemProperties", valueFor(systemProperties))
            .withoutPath("rpcAddress")
            .withoutPath("rpcAdminAddress")
            .withValue("rpcSettings", rpcSettings)
    }

    @VisibleForTesting
    internal fun webServerConf() = WebServerConfigurationData(myLegalName, rpcSettings.address, webAddress, rpcUsers).asConfig()

    fun toNodeConfText(): String = nodeConf().render()

    fun toWebServerConfText(): String = webServerConf().render()

    @VisibleForTesting
    internal fun financeConf() = FinanceConfData(issuableCurrencies).toConfig()

    fun toFinanceConfText(): String = financeConf().render()

    fun serialiseAsString(): String = toConfig().render()

    private fun Config.render(): String = root().render(renderOptions)
}

private data class NodeConfigurationData(
    val myLegalName: CordaX500Name,
    val p2pAddress: NetworkHostAndPort,
    val rpcAddress: NetworkHostAndPort,
    val notary: NotaryService?,
    val h2port: Int,
    val rpcUsers: List<User> = listOf(NodeConfig.defaultUser),
    val useTestClock: Boolean,
    val detectPublicIp: Boolean,
    val devMode: Boolean
)

private data class WebServerConfigurationData(
    val myLegalName: CordaX500Name,
    val rpcAddress: NetworkHostAndPort,
    val webAddress: NetworkHostAndPort,
    val rpcUsers: List<User>
) {
   fun asConfig() = toConfig()
}

private data class FinanceConfData(val issuableCurrencies: List<String>)

/**
 * This is a subset of NotaryConfig. It implements [ExtraService] to avoid unnecessary copying.
 */
data class NotaryService(val validating: Boolean) : ExtraService {
    override fun toString(): String = "${if (validating) "V" else "Non-v"}alidating Notary"
}

// TODO Think of a better name
data class NodeConfigWrapper(val baseDir: Path, val nodeConfig: NodeConfig) : HasCordapps {
    val key: String = nodeConfig.myLegalName.organisation.toKey()
    val nodeDir: Path = baseDir / key
    val explorerDir: Path = baseDir / "$key-explorer"
    override val cordappsDir: Path = nodeDir / NodeConfig.CORDAPP_DIR_NAME
    var state: NodeState = NodeState.STARTING

    fun install(cordapps: Collection<Path>) {
        if (cordapps.isEmpty()) return
        cordappsDir.createDirectories()
        for (cordapp in cordapps) {
            cordapp.copyToDirectory(cordappsDir, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

fun user(name: String) = User(name, "letmein", setOf("ALL"))

fun String.toKey() = filter { !it.isWhitespace() }.toLowerCase()

fun <T> valueFor(any: T): ConfigValue = ConfigValueFactory.fromAnyRef(any)

@Suppress("unused")
private fun Config.withOptionalValue(path: String, obj: ConfigObject): Config {
    return if (obj.isEmpty()) this else withValue(path, obj)
}
