package net.corda.plugins

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import groovy.lang.Closure
import org.gradle.api.Project

class RpcSettings(private val project: Project) {
    private var config: Config = ConfigFactory.empty()

    /**
     * RPC address for the node.
     */
    fun address(value: String) {
        config += "address" to value
    }

    /**
     * RPC admin address for the node (necessary if [useSsl] is false or unset).
     */
    fun adminAddress(value: String) {
        config += "adminAddress" to value
    }

    /**
     * Specifies whether the node RPC layer will require SSL from clients.
     */
    fun useSsl(value: Boolean) {
        config += "useSsl" to value
    }

    /**
     * Specifies whether the RPC broker is separate from the node.
     */
    fun standAloneBroker(value: Boolean) {
        config += "standAloneBroker" to value
    }

    /**
     * Specifies SSL certificates options for the RPC layer.
     */
    fun ssl(configureClosure: Closure<in SslOptions>) {
        val sslOptions = project.configure(SslOptions(), configureClosure) as SslOptions
        config = sslOptions.addTo("ssl", config)
    }

    internal fun addTo(key: String, config: Config): Config {
        if (this.config.isEmpty) {
            return config
        }
        return config + (key to this.config.root())
    }
}

internal operator fun Config.plus(entry: Pair<String, Any>): Config {

    return withValue(entry.first, ConfigValueFactory.fromAnyRef(entry.second))
}