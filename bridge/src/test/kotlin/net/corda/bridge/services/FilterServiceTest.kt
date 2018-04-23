/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.bridge.services

import com.nhaarman.mockito_kotlin.*
import net.corda.bridge.createPartialMock
import net.corda.bridge.services.api.BridgeArtemisConnectionService
import net.corda.bridge.services.api.BridgeConfiguration
import net.corda.bridge.services.api.BridgeSenderService
import net.corda.bridge.services.filter.SimpleMessageFilterService
import net.corda.nodeapi.internal.ArtemisMessagingClient
import net.corda.nodeapi.internal.ArtemisMessagingComponent
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.P2P_PREFIX
import net.corda.nodeapi.internal.protonwrapper.messages.ReceivedMessage
import net.corda.testing.core.DUMMY_BANK_B_NAME
import net.corda.testing.internal.rigorousMock
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.api.core.client.*
import org.junit.Test
import org.mockito.ArgumentMatchers
import kotlin.test.assertEquals

class FilterServiceTest {
    private abstract class TestBridgeArtemisConnectionService : BridgeArtemisConnectionService, TestServiceBase()
    private abstract class TestBridgeSenderService : BridgeSenderService, TestServiceBase()

    companion object {
        private val inboxTopic = "${P2P_PREFIX}test"
    }

    @Test
    fun `Basic function tests`() {
        val conf = rigorousMock<BridgeConfiguration>().also {
            doReturn(ArtemisMessagingComponent.Companion.P2PMessagingHeaders.whitelistedHeaders.toList()).whenever(it).whitelistedHeaders
        }
        val auditService = TestAuditService()
        val dummyMessage = rigorousMock<ClientMessage>().also {
            doReturn(it).whenever(it).putStringProperty(ArgumentMatchers.anyString(), ArgumentMatchers.anyString())
            doReturn(it).whenever(it).putStringProperty(ArgumentMatchers.any<SimpleString>(), ArgumentMatchers.any<SimpleString>())
            doReturn(it).whenever(it).writeBodyBufferBytes(any())
        }
        val dummyProducer = rigorousMock<ClientProducer>().also {
            doNothing().whenever(it).send(any(), eq(dummyMessage), any())
            doNothing().whenever(it).close()
        }
        val dummySession = rigorousMock<ClientSession>().also {
            doReturn(dummyMessage).whenever(it).createMessage(true)
            doReturn(dummyProducer).whenever(it).createProducer()
            doNothing().whenever(it).close()
        }
        val artemisStarted = ArtemisMessagingClient.Started(
                rigorousMock(),
                rigorousMock<ClientSessionFactory>().also {
                    doReturn(dummySession).whenever(it).createSession(ArtemisMessagingComponent.NODE_USER, ArtemisMessagingComponent.NODE_USER, false, true, true, false, ActiveMQClient.DEFAULT_ACK_BATCH_SIZE)
                },
                rigorousMock(),
                rigorousMock()
        )
        val artemisService = createPartialMock<TestBridgeArtemisConnectionService>().also {
            doReturn(artemisStarted).whenever(it).started
        }
        val senderService = createPartialMock<TestBridgeSenderService>().also {
            doReturn(true).whenever(it).validateReceiveTopic(ArgumentMatchers.anyString(), any())
        }
        val filterService = SimpleMessageFilterService(conf, auditService, artemisService, senderService)
        val stateFollower = filterService.activeChange.toBlocking().iterator
        val auditFollower = auditService.onAuditEvent.toBlocking().iterator
        filterService.start()
        // Not ready so packet dropped
        val fakeMessage = rigorousMock<ReceivedMessage>().also {
            doNothing().whenever(it).complete(true) // ACK was called
        }
        filterService.sendMessageToLocalBroker(fakeMessage)
        assertEquals(TestAuditService.AuditEvent.PACKET_DROP, auditFollower.next()) // Dropped as not ready
        assertEquals(false, stateFollower.next())
        assertEquals(false, filterService.active)
        verify(dummyProducer, times(0)).send(ArgumentMatchers.any(), eq(dummyMessage), ArgumentMatchers.any()) // not sent
        auditService.start()
        assertEquals(false, filterService.active)
        artemisService.start()
        assertEquals(false, filterService.active)
        senderService.start()
        assertEquals(true, stateFollower.next())
        assertEquals(true, filterService.active)
        // ready so packet forwarded
        val goodMessage = rigorousMock<ReceivedMessage>().also {
            doNothing().whenever(it).complete(true) // ACK was called
            doReturn(DUMMY_BANK_B_NAME.toString()).whenever(it).sourceLegalName
            doReturn(inboxTopic).whenever(it).topic
            doReturn(ByteArray(1)).whenever(it).payload
            doReturn(emptyMap<Any?, Any?>()).whenever(it).applicationProperties
        }
        filterService.sendMessageToLocalBroker(goodMessage)
        assertEquals(TestAuditService.AuditEvent.PACKET_ACCEPT, auditFollower.next()) // Accepted the message
        verify(dummyProducer, times(1)).send(ArgumentMatchers.any(), eq(dummyMessage), ArgumentMatchers.any()) // message forwarded
        filterService.stop()
        assertEquals(false, stateFollower.next())
        assertEquals(false, filterService.active)
    }


    @Test
    fun `Rejection tests`() {
        val conf = rigorousMock<BridgeConfiguration>().also {
            doReturn(ArtemisMessagingComponent.Companion.P2PMessagingHeaders.whitelistedHeaders.toList()).whenever(it).whitelistedHeaders
        }
        val auditService = TestAuditService()
        val dummyMessage = rigorousMock<ClientMessage>().also {
            doReturn(it).whenever(it).putStringProperty(ArgumentMatchers.anyString(), ArgumentMatchers.anyString())
            doReturn(it).whenever(it).putStringProperty(ArgumentMatchers.any<SimpleString>(), ArgumentMatchers.any<SimpleString>())
            doReturn(it).whenever(it).writeBodyBufferBytes(any())
        }
        val dummyProducer = rigorousMock<ClientProducer>().also {
            doNothing().whenever(it).send(any(), eq(dummyMessage), any())
            doNothing().whenever(it).close()
        }
        val dummySession = rigorousMock<ClientSession>().also {
            doReturn(dummyMessage).whenever(it).createMessage(true)
            doReturn(dummyProducer).whenever(it).createProducer()
            doNothing().whenever(it).close()
        }
        val artemisStarted = ArtemisMessagingClient.Started(
                rigorousMock(),
                rigorousMock<ClientSessionFactory>().also {
                    doReturn(dummySession).whenever(it).createSession(ArtemisMessagingComponent.NODE_USER, ArtemisMessagingComponent.NODE_USER, false, true, true, false, ActiveMQClient.DEFAULT_ACK_BATCH_SIZE)
                },
                rigorousMock(),
                rigorousMock()
        )
        val artemisService = createPartialMock<TestBridgeArtemisConnectionService>().also {
            doReturn(artemisStarted).whenever(it).started
        }
        val senderService = createPartialMock<TestBridgeSenderService>().also {
            doAnswer {
                val topic = it.arguments[0] as String
                (topic == inboxTopic)
            }.whenever(it).validateReceiveTopic(ArgumentMatchers.anyString(), any())
        }
        val filterService = SimpleMessageFilterService(conf, auditService, artemisService, senderService)
        val auditFollower = auditService.onAuditEvent.toBlocking().iterator
        auditService.start()
        artemisService.start()
        senderService.start()
        filterService.start()
        assertEquals(true, filterService.active)

        // empty legal name
        val badMessage1 = rigorousMock<ReceivedMessage>().also {
            doNothing().whenever(it).complete(true) // ACK was called
            doReturn("").whenever(it).sourceLegalName
            doReturn(inboxTopic).whenever(it).topic
            doReturn(ByteArray(1)).whenever(it).payload
            doReturn(emptyMap<Any?, Any?>()).whenever(it).applicationProperties
        }
        filterService.sendMessageToLocalBroker(badMessage1)
        assertEquals(TestAuditService.AuditEvent.PACKET_DROP, auditFollower.next())
        verify(dummyProducer, times(0)).send(ArgumentMatchers.any(), eq(dummyMessage), ArgumentMatchers.any()) // not sent
        // bad legal name
        val badMessage2 = rigorousMock<ReceivedMessage>().also {
            doNothing().whenever(it).complete(true) // ACK was called
            doReturn("CN=Test").whenever(it).sourceLegalName
            doReturn(inboxTopic).whenever(it).topic
            doReturn(ByteArray(1)).whenever(it).payload
            doReturn(emptyMap<Any?, Any?>()).whenever(it).applicationProperties
        }
        filterService.sendMessageToLocalBroker(badMessage2)
        assertEquals(TestAuditService.AuditEvent.PACKET_DROP, auditFollower.next())
        verify(dummyProducer, times(0)).send(ArgumentMatchers.any(), eq(dummyMessage), ArgumentMatchers.any()) // not sent
        // empty payload
        val badMessage3 = rigorousMock<ReceivedMessage>().also {
            doNothing().whenever(it).complete(true) // ACK was called
            doReturn(DUMMY_BANK_B_NAME.toString()).whenever(it).sourceLegalName
            doReturn(inboxTopic).whenever(it).topic
            doReturn(ByteArray(0)).whenever(it).payload
            doReturn(emptyMap<Any?, Any?>()).whenever(it).applicationProperties
        }
        filterService.sendMessageToLocalBroker(badMessage3)
        assertEquals(TestAuditService.AuditEvent.PACKET_DROP, auditFollower.next())
        verify(dummyProducer, times(0)).send(ArgumentMatchers.any(), eq(dummyMessage), ArgumentMatchers.any()) // not sent
        // bad topic
        val badMessage4 = rigorousMock<ReceivedMessage>().also {
            doNothing().whenever(it).complete(true) // ACK was called
            doReturn(DUMMY_BANK_B_NAME.toString()).whenever(it).sourceLegalName
            doReturn("bridge.control").whenever(it).topic
            doReturn(ByteArray(1)).whenever(it).payload
            doReturn(emptyMap<Any?, Any?>()).whenever(it).applicationProperties
        }
        filterService.sendMessageToLocalBroker(badMessage4)
        assertEquals(TestAuditService.AuditEvent.PACKET_DROP, auditFollower.next())
        verify(dummyProducer, times(0)).send(ArgumentMatchers.any(), eq(dummyMessage), ArgumentMatchers.any()) // not sent
        // Non-whitelist header header
        val badMessage5 = rigorousMock<ReceivedMessage>().also {
            doNothing().whenever(it).complete(true) // ACK was called
            doReturn(DUMMY_BANK_B_NAME.toString()).whenever(it).sourceLegalName
            doReturn(inboxTopic).whenever(it).topic
            doReturn(ByteArray(1)).whenever(it).payload
            doReturn(mapOf<Any?, Any?>("Suspicious" to "Header")).whenever(it).applicationProperties
        }
        filterService.sendMessageToLocalBroker(badMessage5)
        assertEquals(TestAuditService.AuditEvent.PACKET_DROP, auditFollower.next())
        verify(dummyProducer, times(0)).send(ArgumentMatchers.any(), eq(dummyMessage), ArgumentMatchers.any()) // not sent

        // Valid message sent and completed
        val goodMessage = rigorousMock<ReceivedMessage>().also {
            doNothing().whenever(it).complete(true) // ACK was called
            doReturn(DUMMY_BANK_B_NAME.toString()).whenever(it).sourceLegalName
            doReturn(inboxTopic).whenever(it).topic
            doReturn(ByteArray(1)).whenever(it).payload
            doReturn(emptyMap<Any?, Any?>()).whenever(it).applicationProperties
        }
        filterService.sendMessageToLocalBroker(goodMessage)
        assertEquals(TestAuditService.AuditEvent.PACKET_ACCEPT, auditFollower.next()) // packet was accepted
        verify(dummyProducer, times(1)).send(ArgumentMatchers.any(), eq(dummyMessage), ArgumentMatchers.any()) // not sent
        filterService.stop()
    }

}