/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.vega.plugin

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.core.serialization.SerializationWhitelist
import net.corda.finance.plugin.registerFinanceJSONMappers
import net.corda.vega.api.PortfolioApi
import net.corda.webserver.services.WebServerPluginRegistry
import java.util.function.Function

/**
 * [SimmService] is the object that makes available the flows and services for the Simm agreement / evaluation flow.
 * It is loaded via discovery - see [SerializationWhitelist].
 * It is also the object that enables a human usable web service for demo purpose
 * It is loaded via discovery see [WebServerPluginRegistry].
 */
class SimmPlugin : WebServerPluginRegistry {
    override val webApis = listOf(Function(::PortfolioApi))
    override val staticServeDirs: Map<String, String> = emptyMap()
    override fun customizeJSONSerialization(om: ObjectMapper): Unit = registerFinanceJSONMappers(om)
}
