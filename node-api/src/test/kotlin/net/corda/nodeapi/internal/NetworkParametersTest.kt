package net.corda.nodeapi.internal

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.flows.CashIssueFlow
import net.corda.node.services.config.NotaryConfig
import net.corda.testing.*
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.MockNodeParameters
import net.corda.testing.node.MockServices
import org.junit.After
import org.junit.Test
import java.nio.file.Path
import kotlin.test.assertFails
import kotlin.test.assertFalse

class NetworkParametersTest {
    private val mockNet= MockNetwork(
            MockNetworkParameters(networkSendManuallyPumped = true),
            notarySpecs = listOf(MockNetwork.NotarySpec(DUMMY_NOTARY.name)))

    @After
    fun tearDown() {
        mockNet.stopNodes()
    }

    @Test
    fun `only one network parameters file allowed in base directory`() {
        val alice = mockNet.createUnstartedNode(MockNodeParameters(legalName = ALICE.name, forcedID = 100))
        val aliceDirectory = mockNet.baseDirectory(100)
        val firstParams = testNetworkParameters(notaries = listOf(NotaryInfo(DUMMY_NOTARY, true)), epoch = 10)
        val secondParams = testNetworkParameters(notaries = listOf(NotaryInfo(DUMMY_BANK_A, true)), epoch = 10)
        dropParametersToDir(aliceDirectory, "network-parameters", firstParams)
        dropParametersToDir(aliceDirectory, "network-parameters-1", secondParams)
        assertFails { alice.start() }
    }

    // Minimum Platform Version tests
    @Test
    fun `node shutdowns when on lower platform version than network`() {
        val alice = mockNet.createUnstartedNode(MockNodeParameters(legalName = ALICE.name, forcedID = 100, version = MockServices.MOCK_VERSION_INFO.copy(platformVersion = 1)))
        val aliceDirectory = mockNet.baseDirectory(100)
        val netParams = testNetworkParameters(
                notaries = listOf(NotaryInfo(mockNet.defaultNotaryIdentity, true)),
                minimumPlatformVersion = 2)
        dropParametersToDir(aliceDirectory, "network-parameters", netParams)
        assertFails { alice.start() }
    }

    @Test
    fun `node works fine when on higher platform version`() {
        val alice = mockNet.createUnstartedNode(MockNodeParameters(legalName = ALICE.name, forcedID = 100, version = MockServices.MOCK_VERSION_INFO.copy(platformVersion = 2)))
        val aliceDirectory = mockNet.baseDirectory(100)
        val netParams = testNetworkParameters(
                notaries = listOf(NotaryInfo(mockNet.defaultNotaryIdentity, true)),
                minimumPlatformVersion = 1)
        dropParametersToDir(aliceDirectory, "network-parameters", netParams)
        alice.start()
    }

    // Notaries tests
    @Test
    fun `choosing notary not specified in network parameters will fail`() {
        val fakeNotary = mockNet.createNode(MockNodeParameters(legalName = BOB_NAME, configOverrides = {
            val notary = NotaryConfig(false)
            doReturn(notary).whenever(it).notary}))
        val fakeNotaryId = fakeNotary.info.chooseIdentity()
        assertFalse(fakeNotary.internals.configuration.notary == null)
        val alice = mockNet.createPartyNode(ALICE_NAME)
        assertFalse(fakeNotaryId in alice.services.networkMapCache.notaryIdentities)
        assertFails {
            alice.services.startFlow(CashIssueFlow(500.DOLLARS, OpaqueBytes.of(0x01), fakeNotaryId)).resultFuture.getOrThrow()
        }
    }

    // Helpers
    private fun dropParametersToDir(dir: Path, filename: String, params: NetworkParameters) {
        NetworkParametersCopier(params).install(dir, filename)
    }
}