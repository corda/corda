package net.corda.node.services.config.rpc

import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.config.SSLConfiguration

interface NodeRpcOptions {
    val address: NetworkHostAndPort?
    val adminAddress: NetworkHostAndPort?
    val standAloneBroker: Boolean
    val useSsl: Boolean
    val sslConfig: SSLConfiguration
}