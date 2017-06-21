package net.corda.node.services

import net.corda.core.serialization.SingletonSerializeAsToken

interface PluginServiceFactory<out T : PluginService> {
    fun create(services: PluginServiceHub, serializationContext: MutableList<Any>): T
}

abstract class PluginService: SingletonSerializeAsToken() {
    open fun start() {}
    open fun stop() {}
}