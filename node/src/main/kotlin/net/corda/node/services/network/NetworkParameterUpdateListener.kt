package net.corda.node.services.network

import net.corda.core.node.NetworkParameters

interface NetworkParameterUpdateListener {
    fun onNewNetworkParameters(networkParameters: NetworkParameters)
}