package net.corda.node.services.config.parsing

import com.typesafe.config.Config
import com.typesafe.config.ConfigValue

interface ConfigDescriber {

    fun describe(configuration: Config): ConfigValue
}