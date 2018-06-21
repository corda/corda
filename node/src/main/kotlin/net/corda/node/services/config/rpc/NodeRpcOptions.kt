package net.corda.node.services.config.rpc

import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.BrokerRpcSslOptions

interface NodeRpcOptions {
    val address: NetworkHostAndPort
    val adminAddress: NetworkHostAndPort
    val standAloneBroker: Boolean
    val useSsl: Boolean
    val sslConfig: BrokerRpcSslOptions?
}