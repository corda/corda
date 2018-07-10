package net.corda.demobench.model

import net.corda.core.utilities.NetworkHostAndPort

data class NodeRpcSettings(
        val address: NetworkHostAndPort,
        val adminAddress: NetworkHostAndPort
)