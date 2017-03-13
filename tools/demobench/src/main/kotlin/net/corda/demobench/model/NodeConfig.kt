package net.corda.demobench.model

import com.typesafe.config.*
import java.lang.String.join
import java.io.File
import java.nio.file.Path
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import net.corda.node.services.config.SSLConfiguration

class NodeConfig(
        baseDir: Path,
        legalName: String,
        artemisPort: Int,
        val nearestCity: String,
        val webPort: Int,
        val h2Port: Int,
        val extraServices: List<String>,
        val users: List<User> = listOf(user("guest")),
        var networkMap: NetworkMapConfig? = null
) : NetworkMapConfig(legalName, artemisPort), HasPlugins {

    companion object {
        val renderOptions: ConfigRenderOptions = ConfigRenderOptions.defaults().setOriginComments(false)
    }

    val nodeDir: Path = baseDir.resolve(key)
    override val pluginDir: Path = nodeDir.resolve("plugins")
    val explorerDir: Path = baseDir.resolve("$key-explorer")

    val ssl: SSLConfiguration = object : SSLConfiguration {
        override val certificatesDirectory: Path = nodeDir.resolve("certificates")
        override val trustStorePassword: String = "trustpass"
        override val keyStorePassword: String = "cordacadevpass"
    }

    var state: NodeState = NodeState.STARTING

    val isCashIssuer: Boolean = extraServices.any {
        it.startsWith("corda.issuer.")
    }

    fun isNetworkMap(): Boolean = networkMap == null

    /*
     * The configuration object depends upon the networkMap,
     * which is mutable.
     */
    fun toFileConfig(): Config = ConfigFactory.empty()
            .withValue("myLegalName", valueFor(legalName))
            .withValue("artemisAddress", addressValueFor(artemisPort))
            .withValue("nearestCity", valueFor(nearestCity))
            .withValue("extraAdvertisedServiceIds", valueFor(join(",", extraServices)))
            .withFallback(optional("networkMapService", networkMap, {
                c, n -> c.withValue("address", addressValueFor(n.artemisPort))
                    .withValue("legalName", valueFor(n.legalName))
            } ))
            .withValue("webAddress", addressValueFor(webPort))
            .withValue("rpcUsers", valueFor(users.map(User::toMap).toList()))
            .withValue("h2port", valueFor(h2Port))
            .withValue("useTestClock", valueFor(true))

    fun toText(): String = toFileConfig().root().render(renderOptions)

    fun moveTo(baseDir: Path) = NodeConfig(
        baseDir, legalName, artemisPort, nearestCity, webPort, h2Port, extraServices, users, networkMap
    )

    fun install(plugins: Collection<Path>) {
        if (plugins.isNotEmpty() && pluginDir.toFile().forceDirectory()) {
            plugins.forEach {
                Files.copy(it, pluginDir.resolve(it.fileName.toString()), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    fun extendUserPermissions(permissions: Collection<String>) = users.forEach { it.extendPermissions(permissions) }
}

private fun <T> valueFor(any: T): ConfigValue? = ConfigValueFactory.fromAnyRef(any)

private fun addressValueFor(port: Int) = valueFor("localhost:$port")

private inline fun <T> optional(path: String, obj: T?, body: (Config, T) -> Config): Config {
    val config = ConfigFactory.empty()
    return if (obj == null) config else body(config, obj).atPath(path)
}

fun File.forceDirectory(): Boolean = this.isDirectory || this.mkdirs()
