package com.r3corda.node.internal

import com.r3corda.node.services.api.ServiceHubInternal
import com.r3corda.node.services.messaging.CordaRPCOps

/**
 * Server side implementations of RPCs available to MQ based client tools. Execution takes place on the server
 * thread (i.e. serially). Arguments are serialised and deserialised automatically.
 */
class ServerRPCOps(services: ServiceHubInternal) : CordaRPCOps {
    override val protocolVersion: Int = 0

    // TODO: Add useful RPCs for client apps (examining the vault, etc)
}
