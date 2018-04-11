/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.bridge

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.bridge.internal.BridgeInstance
import net.corda.bridge.services.api.BridgeMode
import net.corda.core.internal.div
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.services.config.CertChainPolicyConfig
import net.corda.node.services.config.EnterpriseConfiguration
import net.corda.node.services.config.MutualExclusionConfiguration
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.messaging.ArtemisMessagingServer
import net.corda.nodeapi.internal.ArtemisMessagingClient
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.MAX_MESSAGE_SIZE
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.internal.rigorousMock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BridgeIntegrationTest {
    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Rule
    @JvmField
    val serializationEnvironment = SerializationEnvironmentRule(true)

    private abstract class AbstractNodeConfiguration : NodeConfiguration

    @Test
    fun `Load simple all in one bridge and stand it up`() {
        val configResource = "/net/corda/bridge/singleprocess/bridge.conf"
        createNetworkParams(tempFolder.root.toPath())
        val config = createAndLoadConfigFromResource(tempFolder.root.toPath(), configResource)
        assertEquals(BridgeMode.SenderReceiver, config.bridgeMode)
        assertEquals(NetworkHostAndPort("localhost", 11005), config.outboundConfig!!.artemisBrokerAddress)
        assertEquals(NetworkHostAndPort("0.0.0.0", 10005), config.inboundConfig!!.listeningAddress)
        assertNull(config.floatInnerConfig)
        assertNull(config.floatOuterConfig)
        config.createBridgeKeyStores(DUMMY_BANK_A_NAME)
        val (artemisServer, artemisClient) = createArtemis()
        try {
            val bridge = BridgeInstance(config, BridgeVersionInfo(1, "1.1", "Dummy", "Test"))
            val stateFollower = bridge.activeChange.toBlocking().iterator
            assertEquals(false, stateFollower.next())
            assertEquals(false, bridge.active)
            bridge.start()
            assertEquals(true, stateFollower.next())
            assertEquals(true, bridge.active)
            assertEquals(true, serverListening("localhost", 10005))
            bridge.stop()
            assertEquals(false, stateFollower.next())
            assertEquals(false, bridge.active)
            assertEquals(false, serverListening("localhost", 10005))
        } finally {
            artemisClient.stop()
            artemisServer.stop()
        }
    }


    @Test
    fun `Load bridge (float inner) and float outer and stand them up`() {
        val bridgeFolder = tempFolder.root.toPath()
        val bridgeConfigResource = "/net/corda/bridge/withfloat/bridge/bridge.conf"
        val bridgeConfig = createAndLoadConfigFromResource(bridgeFolder, bridgeConfigResource)
        bridgeConfig.createBridgeKeyStores(DUMMY_BANK_A_NAME)
        createNetworkParams(bridgeFolder)
        assertEquals(BridgeMode.FloatInner, bridgeConfig.bridgeMode)
        assertEquals(NetworkHostAndPort("localhost", 11005), bridgeConfig.outboundConfig!!.artemisBrokerAddress)
        val floatFolder = tempFolder.root.toPath() / "float"
        val floatConfigResource = "/net/corda/bridge/withfloat/float/bridge.conf"
        val floatConfig = createAndLoadConfigFromResource(floatFolder, floatConfigResource)
        floatConfig.createBridgeKeyStores(DUMMY_BANK_A_NAME)
        createNetworkParams(floatFolder)
        assertEquals(BridgeMode.FloatOuter, floatConfig.bridgeMode)
        assertEquals(NetworkHostAndPort("0.0.0.0", 10005), floatConfig.inboundConfig!!.listeningAddress)
        val (artemisServer, artemisClient) = createArtemis()
        try {
            val bridge = BridgeInstance(bridgeConfig, BridgeVersionInfo(1, "1.1", "Dummy", "Test"))
            val bridgeStateFollower = bridge.activeChange.toBlocking().iterator
            val float = BridgeInstance(floatConfig, BridgeVersionInfo(1, "1.1", "Dummy", "Test"))
            val floatStateFollower = float.activeChange.toBlocking().iterator
            assertEquals(false, floatStateFollower.next())
            float.start()
            assertEquals(true, floatStateFollower.next())
            assertEquals(true, float.active) // float is running
            assertEquals(false, serverListening("localhost", 10005)) // but not activated
            assertEquals(false, bridgeStateFollower.next())
            bridge.start()
            assertEquals(true, bridgeStateFollower.next())
            assertEquals(true, bridge.active)
            assertEquals(true, float.active)
            assertEquals(true, serverListening("localhost", 10005)) // now activated
            bridge.stop()
            assertEquals(false, bridgeStateFollower.next())
            assertEquals(false, bridge.active)
            assertEquals(true, float.active)
            assertEquals(false, serverListening("localhost", 10005)) // now de-activated
            float.stop()
            assertEquals(false, floatStateFollower.next())
            assertEquals(false, bridge.active)
            assertEquals(false, float.active)
        } finally {
            artemisClient.stop()
            artemisServer.stop()
        }

    }

    private fun createArtemis(): Pair<ArtemisMessagingServer, ArtemisMessagingClient> {
        val artemisConfig = rigorousMock<AbstractNodeConfiguration>().also {
            doReturn(tempFolder.root.toPath()).whenever(it).baseDirectory
            doReturn(ALICE_NAME).whenever(it).myLegalName
            doReturn("trustpass").whenever(it).trustStorePassword
            doReturn("cordacadevpass").whenever(it).keyStorePassword
            doReturn(NetworkHostAndPort("localhost", 11005)).whenever(it).p2pAddress
            doReturn(null).whenever(it).jmxMonitoringHttpPort
            doReturn(emptyList<CertChainPolicyConfig>()).whenever(it).certificateChainCheckPolicies
            doReturn(EnterpriseConfiguration(MutualExclusionConfiguration(false, "", 20000, 40000), externalBridge = true)).whenever(it).enterpriseConfiguration
        }
        val artemisServer = ArtemisMessagingServer(artemisConfig, NetworkHostAndPort("0.0.0.0", 11005), MAX_MESSAGE_SIZE)
        val artemisClient = ArtemisMessagingClient(artemisConfig, NetworkHostAndPort("localhost", 11005), MAX_MESSAGE_SIZE)
        artemisServer.start()
        artemisClient.start()
        return Pair(artemisServer, artemisClient)
    }
}