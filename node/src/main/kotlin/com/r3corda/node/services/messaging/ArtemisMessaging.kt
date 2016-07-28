package com.r3corda.node.services.messaging

import com.google.common.net.HostAndPort
import com.r3corda.core.ThreadBox
import com.r3corda.core.messaging.Message
import com.r3corda.core.messaging.MessageHandlerRegistration
import com.r3corda.core.messaging.SingleMessageRecipient
import org.apache.activemq.artemis.api.core.client.ClientConsumer
import org.apache.activemq.artemis.api.core.client.ClientProducer
import org.apache.activemq.artemis.api.core.client.ClientSession
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory
import org.apache.activemq.artemis.core.postoffice.Address
import org.apache.activemq.artemis.core.server.ActiveMQServer
import java.util.*
import java.util.concurrent.Executor


internal data class ArtemisAddress(val hostAndPort: HostAndPort) : SingleMessageRecipient

/** A registration to handle messages of different types */
internal data class ArtemisHandler(
        val executor: Executor?,
        val topic: String,
        val callback: (Message, MessageHandlerRegistration) -> Unit) : MessageHandlerRegistration

class ArtemisMessaging {

    private lateinit var activeMQServer: ActiveMQServer
    private lateinit var clientFactory: ClientSessionFactory
    private var session: ClientSession? = null
    private var inboundConsumer: ClientConsumer? = null

    private class InnerState {
        var running = false
        val sendClients = HashMap<Address, ClientProducer>()
    }

    private val mutex = ThreadBox(InnerState())


}
