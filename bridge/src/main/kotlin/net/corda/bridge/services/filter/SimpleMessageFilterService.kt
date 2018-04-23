/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.bridge.services.filter

import net.corda.bridge.services.api.*
import net.corda.bridge.services.util.ServiceStateCombiner
import net.corda.bridge.services.util.ServiceStateHelper
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.ArtemisMessagingComponent
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.P2PMessagingHeaders
import net.corda.nodeapi.internal.protonwrapper.messages.ReceivedMessage
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.api.core.client.ActiveMQClient
import org.apache.activemq.artemis.api.core.client.ClientProducer
import org.apache.activemq.artemis.api.core.client.ClientSession
import rx.Subscription

class SimpleMessageFilterService(val conf: BridgeConfiguration,
                                 val auditService: BridgeAuditService,
                                 val artemisConnectionService: BridgeArtemisConnectionService,
                                 val bridgeSenderService: BridgeSenderService,
                                 private val stateHelper: ServiceStateHelper = ServiceStateHelper(log)) : IncomingMessageFilterService, ServiceStateSupport by stateHelper {
    companion object {
        val log = contextLogger()
    }

    private val statusFollower: ServiceStateCombiner
    private var statusSubscriber: Subscription? = null
    private val whiteListedAMQPHeaders: Set<String> = conf.whitelistedHeaders.toSet()
    private var inboundSession: ClientSession? = null
    private var inboundProducer: ClientProducer? = null

    init {
        statusFollower = ServiceStateCombiner(listOf(auditService, artemisConnectionService, bridgeSenderService))
    }

    override fun start() {
        statusSubscriber = statusFollower.activeChange.subscribe {
            if (it) {
                inboundSession = artemisConnectionService.started!!.sessionFactory.createSession(ArtemisMessagingComponent.NODE_USER, ArtemisMessagingComponent.NODE_USER, false, true, true, false, ActiveMQClient.DEFAULT_ACK_BATCH_SIZE)
                inboundProducer = inboundSession!!.createProducer()
            } else {
                inboundProducer?.close()
                inboundProducer = null
                inboundSession?.close()
                inboundSession = null
            }
            stateHelper.active = it
        }
    }

    override fun stop() {
        inboundProducer?.close()
        inboundProducer = null
        inboundSession?.close()
        inboundSession = null
        stateHelper.active = false
        statusSubscriber?.unsubscribe()
        statusSubscriber = null
    }

    private fun validateMessage(inboundMessage: ReceivedMessage) {
        if (!active) {
            throw IllegalStateException("Unable to forward message as Service Dependencies down")
        }
        val sourceLegalName = try {
            CordaX500Name.parse(inboundMessage.sourceLegalName)
        } catch (ex: IllegalArgumentException) {
            throw SecurityException("Invalid Legal Name ${inboundMessage.sourceLegalName}")
        }
        require(inboundMessage.payload.size > 0) { "No valid payload" }
        val validInboxTopic = bridgeSenderService.validateReceiveTopic(inboundMessage.topic, sourceLegalName)
        require(validInboxTopic) { "Topic not a legitimate Inbox for a node on this Artemis Broker ${inboundMessage.topic}" }
        require(inboundMessage.applicationProperties.keys.all { it!!.toString() in whiteListedAMQPHeaders }) { "Disallowed header present in ${inboundMessage.applicationProperties.keys.map { it.toString() }}" }
    }

    override fun sendMessageToLocalBroker(inboundMessage: ReceivedMessage) {
        try {
            validateMessage(inboundMessage)
        } catch (ex: Exception) {
            auditService.packetDropEvent(inboundMessage, "Packet Failed validation checks: " + ex.message)
            inboundMessage.complete(true) // consume the bad message, so that it isn't redelivered forever.
            return
        }
        try {
            val session = inboundSession
            val producer = inboundProducer
            if (session == null || producer == null) {
                throw IllegalStateException("No artemis connection to forward message over")
            }
            val artemisMessage = session.createMessage(true)
            for (key in whiteListedAMQPHeaders) {
                if (inboundMessage.applicationProperties.containsKey(key)) {
                    artemisMessage.putObjectProperty(key, inboundMessage.applicationProperties[key])
                }
            }
            artemisMessage.putStringProperty(P2PMessagingHeaders.bridgedCertificateSubject, SimpleString(inboundMessage.sourceLegalName))
            artemisMessage.writeBodyBufferBytes(inboundMessage.payload)
            producer.send(SimpleString(inboundMessage.topic), artemisMessage, { _ -> inboundMessage.complete(true) })
            auditService.packetAcceptedEvent(inboundMessage)
        } catch (ex: Exception) {
            log.error("Error trying to forward message", ex)
            inboundMessage.complete(false) // delivery failure. NAK back to source and await re-delivery attempts
        }
    }
}