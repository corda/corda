package net.corda.node.services.network

import net.corda.core.node.NetworkParameters

/**
 * When network parameters change on a flag day, onNewNetworkParameters will be invoked with the new parameters.
 * Used inside {@link net.corda.node.services.network.NetworkParametersUpdater}
 */
interface NetworkParameterUpdateListener {
    fun onNewNetworkParameters(networkParameters: NetworkParameters)
}