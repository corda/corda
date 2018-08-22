package net.corda.node.services.config.parsing

import com.typesafe.config.ConfigRenderOptions

// TODO sollecitom move
interface ConfigDefinition {

    fun serialize(options: ConfigRenderOptions = ConfigRenderOptions.concise().setFormatted(true).setJson(true)): String
}