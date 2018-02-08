package net.corda.core.internal

import net.corda.core.node.NetworkParameters

object GlobalProperties {
    private var _networkParameters: NetworkParameters? = null

    var networkParameters: NetworkParameters
        get() = checkNotNull(_networkParameters) { "Property 'networkParameters' has not been initialised." }
        set(value) {
            _networkParameters = value
        }
}