package net.corda.node.utilities

import java.util.*

/**
 * Expose properties defined in top-level 'constants.properties' file.
 */
object NodeBuildProperties {

    // Note: initialization order is important
    private val data by lazy {
        Properties().apply {
            NodeBuildProperties::class.java.getResourceAsStream("/build.properties")
                    ?.let { load(it) }
        }
    }

    /**
     * Jolokia dependency version
     */
    val JOLOKIA_AGENT_VERSION = get("jolokiaAgentVersion")

    /**
     * Get property value by name
     */
    fun get(key: String): String? = data.getProperty(key)
}