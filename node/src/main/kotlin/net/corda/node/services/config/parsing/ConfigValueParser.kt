package net.corda.node.services.config.parsing

import com.typesafe.config.Config

interface ConfigValueParser<VALUE : Any> {

    fun parse(configuration: Config, strict: Boolean): Validated<VALUE, Configuration.Validation.Error>
}