package net.corda.behave.node.configuration

open class ConfigurationTemplate {

    protected open val config: (Configuration) -> String = { "" }

    fun generate(config: Configuration) = config(config).trimMargin()
}
