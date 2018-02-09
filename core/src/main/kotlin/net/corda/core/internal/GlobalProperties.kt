package net.corda.core.internal

import net.corda.core.node.NetworkParameters

// TODO: This will cause problems when we run tests in parallel, make each node have its own properties.
object GlobalProperties {

    //todo - is this a reasonable approach?
    val useWhitelistedByZoneAttachmentConstraint = true

    private var _networkParameters: NetworkParameters? = null

    var networkParameters: NetworkParameters
        get() = checkNotNull(_networkParameters) { "Property 'networkParameters' has not been initialised." }
        set(value) {
            _networkParameters = value
        }
}