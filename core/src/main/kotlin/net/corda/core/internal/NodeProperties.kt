package net.corda.core.internal

import net.corda.core.node.NetworkParameters

object NodeProperties {
    private var _networkParameters: NetworkParameters? = null

    var networkParameters: NetworkParameters
        get() = _networkParameters ?: throw IllegalArgumentException("Property 'networkParameters' has not been initialised.")
        set(value) {
            _networkParameters = value
        }
}