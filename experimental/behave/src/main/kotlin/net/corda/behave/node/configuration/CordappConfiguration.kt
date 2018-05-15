/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.behave.node.configuration

class CordappConfiguration(var apps: List<String> = emptyList(), val includeFinance: Boolean = false) : ConfigurationTemplate() {

    private val applications = apps + if (includeFinance) {
        listOf("net.corda:corda-finance:CORDA_VERSION")
    } else {
        emptyList()
    }

    override val config: (Configuration) -> String
        get() = { config ->
            if (applications.isEmpty()) {
                ""
            } else {
                """
                |cordapps = [
                |${applications.joinToString(", ") { formatApp(config, it) }}
                |]
                """
            }
        }

    private fun formatApp(config: Configuration, app: String): String {
        return "\"${app.replace("CORDA_VERSION", config.distribution.version)}\""
    }
}
