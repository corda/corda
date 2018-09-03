package net.corda.bridge.services

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.bridge.createAndLoadConfigFromResource
import net.corda.bridge.createBridgeKeyStores
import net.corda.bridge.createNetworkParams
import net.corda.bridge.services.artemis.BridgeArtemisConnectionServiceImpl
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.services.config.EnterpriseConfiguration
import net.corda.node.services.config.MutualExclusionConfiguration
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.messaging.ArtemisMessagingServer
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.MAX_MESSAGE_SIZE
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.internal.rigorousMock
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ArtemisConnectionTest {
    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Rule
    @JvmField
    val serializationEnvironment = SerializationEnvironmentRule(true)

    private abstract class AbstractNodeConfiguration : NodeConfiguration

    @Test
    fun `Basic lifecycle test`() {
        val configResource = "/net/corda/bridge/singleprocess/firewall.conf"
        createNetworkParams(tempFolder.root.toPath())
        val bridgeConfig = createAndLoadConfigFromResource(tempFolder.root.toPath(), configResource)
        bridgeConfig.createBridgeKeyStores(DUMMY_BANK_A_NAME)
        val auditService = TestAuditService()
        val artemisService = BridgeArtemisConnectionServiceImpl(bridgeConfig, MAX_MESSAGE_SIZE, auditService)
        val stateFollower = artemisService.activeChange.toBlocking().iterator
        artemisService.start()
        assertEquals(false, stateFollower.next())
        assertEquals(false, artemisService.active)
        assertNull(artemisService.started)
        auditService.start()
        assertEquals(false, artemisService.active)
        assertNull(artemisService.started)
        var artemisServer = createArtemis()
        try {
            assertEquals(true, stateFollower.next())
            assertEquals(true, artemisService.active)
            assertNotNull(artemisService.started)
            auditService.stop()
            assertEquals(false, stateFollower.next())
            assertEquals(false, artemisService.active)
            assertNull(artemisService.started)
            auditService.start()
            assertEquals(true, stateFollower.next())
            assertEquals(true, artemisService.active)
            assertNotNull(artemisService.started)
        } finally {
            artemisServer.stop()
        }
        assertEquals(false, stateFollower.next())
        assertEquals(false, artemisService.active)
        assertNull(artemisService.started)
        artemisServer = createArtemis()
        try {
            assertEquals(true, stateFollower.next())
            assertEquals(true, artemisService.active)
            assertNotNull(artemisService.started)
        } finally {
            artemisServer.stop()
        }
        assertEquals(false, stateFollower.next())
        assertEquals(false, artemisService.active)
        assertNull(artemisService.started)
        artemisService.stop()
    }


    private fun createArtemis(): ArtemisMessagingServer {
        val artemisConfig = rigorousMock<AbstractNodeConfiguration>().also {
            doReturn(tempFolder.root.toPath()).whenever(it).baseDirectory
            doReturn(DUMMY_BANK_A_NAME).whenever(it).myLegalName
            doReturn("trustpass").whenever(it).trustStorePassword
            doReturn("cordacadevpass").whenever(it).keyStorePassword
            doReturn(NetworkHostAndPort("localhost", 11005)).whenever(it).p2pAddress
            doReturn(null).whenever(it).jmxMonitoringHttpPort
            doReturn(EnterpriseConfiguration(MutualExclusionConfiguration(false, "", 20000, 40000), externalBridge = true)).whenever(it).enterpriseConfiguration
        }
        val artemisServer = ArtemisMessagingServer(artemisConfig, NetworkHostAndPort("0.0.0.0", 11005), MAX_MESSAGE_SIZE)
        artemisServer.start()
        return artemisServer
    }

}