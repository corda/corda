package net.corda.demobench.model

import com.typesafe.config.*
import net.corda.core.identity.CordaX500Name
import net.corda.nodeapi.User
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class NodeConfig constructor(
        baseDir: Path,
        legalName: CordaX500Name,
        p2pPort: Int,
        val rpcPort: Int,
        val webPort: Int,
        val h2Port: Int,
        val extraServices: MutableList<String> = mutableListOf(),
        val users: List<User> = listOf(defaultUser),
        var networkMap: NetworkMapConfig? = null
) : NetworkMapConfig(legalName, p2pPort), HasPlugins {

    companion object {
        val renderOptions: ConfigRenderOptions = ConfigRenderOptions.defaults().setOriginComments(false)
        val defaultUser = user("guest")
    }

    val nearestCity: String = legalName.locality
    val nodeDir: Path = baseDir.resolve(key)
    override val pluginDir: Path = nodeDir.resolve("plugins")
    val explorerDir: Path = baseDir.resolve("$key-explorer")

    var state: NodeState = NodeState.STARTING

    val isCashIssuer: Boolean = extraServices.any {
        it.startsWith("corda.issuer.")
    }

    fun isNetworkMap(): Boolean = networkMap == null

    /*
     * The configuration object depends upon the networkMap,
     * which is mutable.
     */
    fun toFileConfig(): Config {
        return ConfigFactory.empty()
                .withValue("myLegalName", valueFor(legalName.toString()))
                .withValue("p2pAddress", addressValueFor(p2pPort))
                .withValue("extraAdvertisedServiceIds", valueFor(extraServices))
                .withFallback(optional("networkMapService", networkMap, { c, n ->
                    c.withValue("address", addressValueFor(n.p2pPort))
                            .withValue("legalName", valueFor(n.legalName.toString()))
                }))
                .withValue("webAddress", addressValueFor(webPort))
                .withValue("rpcAddress", addressValueFor(rpcPort))
                .withValue("rpcUsers", valueFor(users.map(User::toMap).toList()))
                .withValue("h2port", valueFor(h2Port))
                .withValue("useTestClock", valueFor(true))
                .withValue("detectPublicIp", valueFor(false))
    }

    fun toText(): String = toFileConfig().root().render(renderOptions)

    fun moveTo(baseDir: Path) = NodeConfig(
            baseDir, legalName, p2pPort, rpcPort, webPort, h2Port, extraServices, users, networkMap
    )

    fun install(plugins: Collection<Path>) {
        if (plugins.isNotEmpty() && pluginDir.toFile().forceDirectory()) {
            plugins.forEach {
                Files.copy(it, pluginDir.resolve(it.fileName.toString()), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

}

private fun <T> valueFor(any: T): ConfigValue? = ConfigValueFactory.fromAnyRef(any)

private fun addressValueFor(port: Int) = valueFor("localhost:$port")

private inline fun <T> optional(path: String, obj: T?, body: (Config, T) -> Config): Config {
    val config = ConfigFactory.empty()
    return if (obj == null) config else body(config, obj).atPath(path)
}

fun File.forceDirectory(): Boolean = this.isDirectory || this.mkdirs()
