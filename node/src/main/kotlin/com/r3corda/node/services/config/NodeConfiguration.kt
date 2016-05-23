package com.r3corda.node.services.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import kotlin.reflect.KProperty

interface NodeConfiguration {
    val myLegalName: String
    val exportJMXto: String
    val nearestCity: String
}

// Allow the use of "String by config" syntax. TODO: Make it more flexible.
operator fun Config.getValue(receiver: NodeConfigurationFromConfig, metadata: KProperty<*>) = getString(metadata.name)

class NodeConfigurationFromConfig(val config: Config = ConfigFactory.load()) : NodeConfiguration {
    override val myLegalName: String by config
    override val exportJMXto: String by config
    override val nearestCity: String by config
}