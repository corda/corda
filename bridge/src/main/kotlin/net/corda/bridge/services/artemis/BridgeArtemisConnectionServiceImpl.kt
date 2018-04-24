/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.bridge.services.artemis

import net.corda.bridge.services.api.*
import net.corda.bridge.services.config.BridgeSSLConfigurationImpl
import net.corda.bridge.services.util.ServiceStateCombiner
import net.corda.bridge.services.util.ServiceStateHelper
import net.corda.core.internal.ThreadBox
import net.corda.core.serialization.internal.nodeSerializationEnv
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.ArtemisTcpTransport
import net.corda.nodeapi.ConnectionDirection
import net.corda.nodeapi.internal.ArtemisMessagingClient
import net.corda.nodeapi.internal.ArtemisMessagingComponent
import org.apache.activemq.artemis.api.core.client.ActiveMQClient
import org.apache.activemq.artemis.api.core.client.FailoverEventType
import org.apache.activemq.artemis.api.core.client.ServerLocator
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants
import rx.Subscription
import java.util.concurrent.CountDownLatch

class BridgeArtemisConnectionServiceImpl(val conf: BridgeConfiguration,
                                         val maxMessageSize: Int,
                                         val auditService: BridgeAuditService,
                                         private val stateHelper: ServiceStateHelper = ServiceStateHelper(log)) : BridgeArtemisConnectionService, ServiceStateSupport by stateHelper {
    companion object {
        val log = contextLogger()
    }

    private class InnerState {
        var running = false
        var locator: ServerLocator? = null
        var started: ArtemisMessagingClient.Started? = null
        var connectThread: Thread? = null
    }

    private val state = ThreadBox(InnerState())
    private val sslConfiguration: BridgeSSLConfiguration
    private val statusFollower: ServiceStateCombiner
    private var statusSubscriber: Subscription? = null

    init {
        statusFollower = ServiceStateCombiner(listOf(auditService))
        sslConfiguration = conf.outboundConfig?.customSSLConfiguration ?: BridgeSSLConfigurationImpl(conf)
    }

    override fun start() {
        statusSubscriber = statusFollower.activeChange.subscribe {
            if (it) {
                startArtemisConnection()
            } else {
                stopArtemisConnection()
            }
        }
    }

    private fun startArtemisConnection() {
        state.locked {
            check(!running) { "start can't be called twice" }
            running = true
            val outboundConf = conf.outboundConfig!!
            log.info("Connecting to message broker: ${outboundConf.artemisBrokerAddress}")
            val brokerAddresses = listOf(outboundConf.artemisBrokerAddress) + outboundConf.alternateArtemisBrokerAddresses
            // TODO Add broker CN to config for host verification in case the embedded broker isn't used
            val tcpTransports = brokerAddresses.map { ArtemisTcpTransport.tcpTransport(ConnectionDirection.Outbound(), it, sslConfiguration) }
            locator = ActiveMQClient.createServerLocatorWithoutHA(*tcpTransports.toTypedArray()).apply {
                // Never time out on our loopback Artemis connections. If we switch back to using the InVM transport this
                // would be the default and the two lines below can be deleted.
                connectionTTL = -1
                clientFailureCheckPeriod = -1
                minLargeMessageSize = maxMessageSize
                isUseGlobalPools = nodeSerializationEnv != null
            }
            connectThread = Thread({ artemisReconnectionLoop() }, "Artemis Connector Thread").apply {
                isDaemon = true
            }
            connectThread!!.start()
        }
    }

    override fun stop() {
        stopArtemisConnection()
        statusSubscriber?.unsubscribe()
        statusSubscriber = null
    }

    private fun stopArtemisConnection() {
        stateHelper.active = false
        val connectThread = state.locked {
            if (running) {
                log.info("Shutdown artemis")
                running = false
                started?.apply {
                    producer.close()
                    session.close()
                    sessionFactory.close()
                }
                started = null
                locator?.close()
                locator = null
                val thread = connectThread
                connectThread = null
                thread
            } else null
        }
        connectThread?.interrupt()
        connectThread?.join(conf.politeShutdownPeriod.toLong())
    }

    override val started: ArtemisMessagingClient.Started?
        get() = state.locked { started }

    private fun artemisReconnectionLoop() {
        var tcpIndex = 0
        while (state.locked { running }) {
            val locator = state.locked { locator }
            if (locator == null) {
                break
            }
            try {
                val transport = locator.staticTransportConfigurations[tcpIndex]
                tcpIndex = (tcpIndex + 1).rem(locator.staticTransportConfigurations.size)
                log.info("Try create session factory ${transport.params[TransportConstants.HOST_PROP_NAME]}:${transport.params[TransportConstants.PORT_PROP_NAME]}")
                val newSessionFactory = locator.createSessionFactory(transport)
                log.info("Got session factory")
                val latch = CountDownLatch(1)
                newSessionFactory.connection.addCloseListener {
                    log.info("Connection close event")
                    latch.countDown()
                }
                newSessionFactory.addFailoverListener { evt: FailoverEventType ->
                    log.info("Session failover Event $evt")
                    if (evt == FailoverEventType.FAILOVER_FAILED) {
                        latch.countDown()
                    }
                }
                val newSession = newSessionFactory.createSession(ArtemisMessagingComponent.NODE_USER,
                        ArtemisMessagingComponent.NODE_USER,
                        false,
                        true,
                        true,
                        locator.isPreAcknowledge,
                        ActiveMQClient.DEFAULT_ACK_BATCH_SIZE)
                newSession.start()
                log.info("Session created")
                val newProducer = newSession.createProducer()
                state.locked {
                    started = ArtemisMessagingClient.Started(locator, newSessionFactory, newSession, newProducer)
                }
                stateHelper.active = true
                latch.await()
                state.locked {
                    started?.apply {
                        producer.close()
                        session.close()
                        sessionFactory.close()
                    }
                    started = null
                }
                stateHelper.active = false
                log.info("Session closed")
            } catch (ex: Exception) {
                log.trace("Caught exception", ex)
            }

            try {
                // Sleep for a short while before attempting reconnect
                Thread.sleep(conf.artemisReconnectionInterval.toLong())
            } catch (ex: InterruptedException) {
                // ignore
            }
        }
        log.info("Ended Artemis Connector Thread")
    }
}