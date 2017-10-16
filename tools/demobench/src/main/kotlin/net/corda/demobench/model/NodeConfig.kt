package net.corda.demobench.model

import com.typesafe.config.ConfigRenderOptions
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.copyToDirectory
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.User
import net.corda.nodeapi.config.toConfig
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * This is a subset of FullNodeConfiguration, containing only those configs which we need. The node uses reference.conf
 * to fill in the defaults so we're not required to specify them here.
 */
data class NodeConfig(
        val myLegalName: CordaX500Name,
        val p2pAddress: NetworkHostAndPort,
        val rpcAddress: NetworkHostAndPort,
        /** This is not used by the node but by the webserver which looks at node.conf. */
        val webAddress: NetworkHostAndPort,
        val notary: NotaryService?,
        val networkMapService: NetworkMapConfig?,
        val h2port: Int,
        val rpcUsers: List<User> = listOf(defaultUser),
        /** This is an extra config used by the Cash app. */
        val issuableCurrencies: List<String> = emptyList()
) {
    companion object {
        val renderOptions: ConfigRenderOptions = ConfigRenderOptions.defaults().setOriginComments(false)
        val defaultUser = user("guest")
        val cordappDirName = "cordapps"
    }

    @Suppress("unused")
    private val detectPublicIp = false
    @Suppress("unused")
    private val useTestClock = true

    val isNetworkMap: Boolean get() = networkMapService == null

    fun toText(): String = toConfig().root().render(renderOptions)
}

/**
 * This is a mirror of NetworkMapInfo.
 */
data class NetworkMapConfig(val legalName: CordaX500Name, val address: NetworkHostAndPort)

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
    override val cordappsDir: Path = nodeDir / NodeConfig.cordappDirName
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
