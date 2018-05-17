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
import net.corda.bridge.*
import net.corda.bridge.services.api.BridgeAMQPListenerService
import net.corda.bridge.services.api.IncomingMessageFilterService
import net.corda.bridge.services.ha.SingleInstanceMasterService
import net.corda.bridge.services.receiver.FloatControlListenerService
import net.corda.bridge.services.receiver.TunnelingBridgeReceiverService
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.P2P_PREFIX
import net.corda.nodeapi.internal.protonwrapper.messages.ReceivedMessage
import net.corda.nodeapi.internal.protonwrapper.netty.ConnectionChange
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.DUMMY_BANK_B_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.internal.rigorousMock
import org.junit.Assert.assertArrayEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import rx.Observable
import rx.subjects.PublishSubject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

class TunnelControlTest {
    companion object {
        val inboxTopic = "${P2P_PREFIX}test"
    }

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Rule
    @JvmField
    val serializationEnvironment = SerializationEnvironmentRule(true)

    private abstract class TestBridgeAMQPListenerService : BridgeAMQPListenerService, TestServiceBase() {
        private var _running: Boolean = false
        override val running: Boolean
            get() = _running

        override fun provisionKeysAndActivate(keyStoreBytes: ByteArray, keyStorePassword: CharArray, keyStorePrivateKeyPassword: CharArray, trustStoreBytes: ByteArray, trustStorePassword: CharArray) {
            _running = true
        }

        override fun wipeKeysAndDeactivate() {
            _running = false
        }
    }

    private abstract class TestIncomingMessageFilterService : IncomingMessageFilterService, TestServiceBase()

    @Test
    fun `Basic tunnel life cycle test`() {
        val bridgeConfigResource = "/net/corda/bridge/withfloat/bridge/bridge.conf"
        val bridgePath = tempFolder.root.toPath() / "bridge"
        bridgePath.createDirectories()
        val maxMessageSize = createNetworkParams(bridgePath)
        val bridgeConfig = createAndLoadConfigFromResource(bridgePath, bridgeConfigResource)
        bridgeConfig.createBridgeKeyStores(DUMMY_BANK_A_NAME)
        val bridgeAuditService = TestAuditService()
        val haService = SingleInstanceMasterService(bridgeConfig, bridgeAuditService)
        val filterService = createPartialMock<TestIncomingMessageFilterService>()
        val bridgeProxiedReceiverService = TunnelingBridgeReceiverService(bridgeConfig, maxMessageSize, bridgeAuditService, haService, filterService)
        val bridgeStateFollower = bridgeProxiedReceiverService.activeChange.toBlocking().iterator
        bridgeProxiedReceiverService.start()
        assertEquals(false, bridgeStateFollower.next())
        assertEquals(false, bridgeProxiedReceiverService.active)
        bridgeAuditService.start()
        assertEquals(false, bridgeProxiedReceiverService.active)
        filterService.start()
        assertEquals(false, bridgeProxiedReceiverService.active)
        haService.start()
        assertEquals(false, bridgeProxiedReceiverService.active)

        val floatConfigResource = "/net/corda/bridge/withfloat/float/bridge.conf"
        val floatPath = tempFolder.root.toPath() / "float"
        floatPath.createDirectories()
        createNetworkParams(floatPath)
        val floatConfig = createAndLoadConfigFromResource(floatPath, floatConfigResource)
        floatConfig.createBridgeKeyStores(DUMMY_BANK_A_NAME)

        val floatAuditService = TestAuditService()
        val amqpListenerService = createPartialMock<TestBridgeAMQPListenerService>().also {
            doReturn(Observable.never<ConnectionChange>()).whenever(it).onConnection
            doReturn(Observable.never<ReceivedMessage>()).whenever(it).onReceive
        }
        val floatControlListener = FloatControlListenerService(floatConfig, maxMessageSize, floatAuditService, amqpListenerService)
        val floatStateFollower = floatControlListener.activeChange.toBlocking().iterator
        assertEquals(false, floatStateFollower.next())
        assertEquals(false, floatControlListener.active)
        floatControlListener.start()
        assertEquals(false, floatControlListener.active)
        floatAuditService.start()
        assertEquals(false, floatControlListener.active)
        verify(amqpListenerService, times(0)).wipeKeysAndDeactivate()
        verify(amqpListenerService, times(0)).provisionKeysAndActivate(any(), any(), any(), any(), any())
        assertEquals(false, serverListening("localhost", 12005))
        amqpListenerService.start()
        assertEquals(true, floatStateFollower.next())
        assertEquals(true, floatControlListener.active)
        assertEquals(true, serverListening("localhost", 12005))

        assertEquals(true, bridgeStateFollower.next())
        assertEquals(true, bridgeProxiedReceiverService.active)
        verify(amqpListenerService, times(0)).wipeKeysAndDeactivate()
        verify(amqpListenerService, times(1)).provisionKeysAndActivate(any(), any(), any(), any(), any())

        haService.stop()
        assertEquals(false, bridgeStateFollower.next())
        assertEquals(false, bridgeProxiedReceiverService.active)
        assertEquals(true, floatControlListener.active)
        verify(amqpListenerService, times(1)).wipeKeysAndDeactivate()
        verify(amqpListenerService, times(1)).provisionKeysAndActivate(any(), any(), any(), any(), any())
        assertEquals(true, serverListening("localhost", 12005))

        haService.start()
        assertEquals(true, bridgeStateFollower.next())
        assertEquals(true, bridgeProxiedReceiverService.active)
        assertEquals(true, floatControlListener.active)
        verify(amqpListenerService, times(1)).wipeKeysAndDeactivate()
        verify(amqpListenerService, times(2)).provisionKeysAndActivate(any(), any(), any(), any(), any())

        floatControlListener.stop()
        assertEquals(false, floatControlListener.active)
        bridgeProxiedReceiverService.stop()
        assertEquals(false, bridgeStateFollower.next())
        assertEquals(false, bridgeProxiedReceiverService.active)
    }

    @Test
    fun `Inbound message test`() {
        val bridgeConfigResource = "/net/corda/bridge/withfloat/bridge/bridge.conf"
        val bridgePath = tempFolder.root.toPath() / "bridge"
        bridgePath.createDirectories()
        val maxMessageSize = createNetworkParams(bridgePath)
        val bridgeConfig = createAndLoadConfigFromResource(bridgePath, bridgeConfigResource)
        bridgeConfig.createBridgeKeyStores(DUMMY_BANK_A_NAME)
        val bridgeAuditService = TestAuditService()
        val haService = SingleInstanceMasterService(bridgeConfig, bridgeAuditService)
        val forwardedMessages = PublishSubject.create<ReceivedMessage>()
        val filterService = createPartialMock<TestIncomingMessageFilterService>().also {
            doAnswer {
                val msg = it.arguments[0] as ReceivedMessage
                forwardedMessages.onNext(msg)
                Unit
            }.whenever(it).sendMessageToLocalBroker(any())
        }
        val bridgeProxiedReceiverService = TunnelingBridgeReceiverService(bridgeConfig, maxMessageSize, bridgeAuditService, haService, filterService)
        val bridgeStateFollower = bridgeProxiedReceiverService.activeChange.toBlocking().iterator
        bridgeProxiedReceiverService.start()
        bridgeAuditService.start()
        filterService.start()
        haService.start()
        assertEquals(false, bridgeStateFollower.next())

        val floatConfigResource = "/net/corda/bridge/withfloat/float/bridge.conf"
        val floatPath = tempFolder.root.toPath() / "float"
        floatPath.createDirectories()
        createNetworkParams(floatPath)
        val floatConfig = createAndLoadConfigFromResource(floatPath, floatConfigResource)
        floatConfig.createBridgeKeyStores(DUMMY_BANK_A_NAME)

        val floatAuditService = TestAuditService()
        val receiveObserver = PublishSubject.create<ReceivedMessage>()
        val amqpListenerService = createPartialMock<TestBridgeAMQPListenerService>().also {
            doReturn(Observable.never<ConnectionChange>()).whenever(it).onConnection
            doReturn(receiveObserver).whenever(it).onReceive
        }
        val floatControlListener = FloatControlListenerService(floatConfig, maxMessageSize, floatAuditService, amqpListenerService)
        floatControlListener.start()
        floatAuditService.start()
        amqpListenerService.start()
        assertEquals(true, bridgeStateFollower.next())

        // Message flows back fine from float to bridge and is then forwarded to the filter service
        val receiver = forwardedMessages.toBlocking().iterator
        val testPayload = ByteArray(1) { 0x11 }
        val receivedMessage = rigorousMock<ReceivedMessage>().also {
            doNothing().whenever(it).complete(true) // ACK was called
            doReturn(DUMMY_BANK_B_NAME.toString()).whenever(it).sourceLegalName
            doReturn(NetworkHostAndPort("localhost", 12345)).whenever(it).sourceLink
            doReturn(inboxTopic).whenever(it).topic
            doReturn(testPayload).whenever(it).payload
            doReturn(emptyMap<Any?, Any?>()).whenever(it).applicationProperties
            doReturn(DUMMY_BANK_A_NAME.toString()).whenever(it).destinationLegalName
            doReturn(NetworkHostAndPort("localhost", 6789)).whenever(it).destinationLink
        }
        receiveObserver.onNext(receivedMessage)
        val messageReceived = receiver.next()
        messageReceived.complete(true)
        assertArrayEquals(testPayload, messageReceived.payload)
        assertEquals(inboxTopic, messageReceived.topic)
        assertEquals(DUMMY_BANK_B_NAME.toString(), messageReceived.sourceLegalName)

        // Message NAK is propagated backwards
        val testPayload2 = ByteArray(1) { 0x22 }
        val ackLatch = CountDownLatch(1)
        val receivedMessage2 = rigorousMock<ReceivedMessage>().also {
            doAnswer {
                ackLatch.countDown()
                Unit
            }.whenever(it).complete(false) // NAK was called
            doReturn(DUMMY_BANK_B_NAME.toString()).whenever(it).sourceLegalName
            doReturn(NetworkHostAndPort("localhost", 12345)).whenever(it).sourceLink
            doReturn(inboxTopic).whenever(it).topic
            doReturn(testPayload2).whenever(it).payload
            doReturn(emptyMap<Any?, Any?>()).whenever(it).applicationProperties
            doReturn(DUMMY_BANK_A_NAME.toString()).whenever(it).destinationLegalName
            doReturn(NetworkHostAndPort("localhost", 6789)).whenever(it).destinationLink
        }
        receiveObserver.onNext(receivedMessage2)
        val messageReceived2 = receiver.next()
        messageReceived2.complete(false) // cause NAK to be called
        assertArrayEquals(testPayload2, messageReceived2.payload)
        assertEquals(inboxTopic, messageReceived2.topic)
        assertEquals(DUMMY_BANK_B_NAME.toString(), messageReceived2.sourceLegalName)
        ackLatch.await(1, TimeUnit.SECONDS)
        verify(receivedMessage2, times(1)).complete(false)

        // Message NAK if connection dies, without message acceptance
        val ackLatch2 = CountDownLatch(1)
        val receivedMessage3 = rigorousMock<ReceivedMessage>().also {
            doAnswer {
                ackLatch2.countDown()
                Unit
            }.whenever(it).complete(false) // NAK was called
            doReturn(DUMMY_BANK_B_NAME.toString()).whenever(it).sourceLegalName
            doReturn(NetworkHostAndPort("localhost", 12345)).whenever(it).sourceLink
            doReturn(inboxTopic).whenever(it).topic
            doReturn(testPayload2).whenever(it).payload
            doReturn(emptyMap<Any?, Any?>()).whenever(it).applicationProperties
            doReturn(DUMMY_BANK_A_NAME.toString()).whenever(it).destinationLegalName
            doReturn(NetworkHostAndPort("localhost", 6789)).whenever(it).destinationLink
        }
        receiveObserver.onNext(receivedMessage3)
        receiver.next() // wait message on bridge
        bridgeProxiedReceiverService.stop() // drop control link
        ackLatch.await(1, TimeUnit.SECONDS)
        verify(receivedMessage3, times(1)).complete(false)

        floatControlListener.stop()
    }

}