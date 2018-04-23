/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

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