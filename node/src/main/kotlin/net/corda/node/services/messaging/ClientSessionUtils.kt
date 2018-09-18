package net.corda.node.services.messaging

import org.apache.activemq.artemis.api.core.client.ClientSession
import org.apache.activemq.artemis.core.client.impl.ClientSessionInternal

fun ClientSession.stillOpen(): Boolean {
    return (!isClosed && (this as? ClientSessionInternal)?.isClosing != false)
}