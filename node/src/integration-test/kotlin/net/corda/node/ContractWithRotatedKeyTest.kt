package net.corda.node

import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.node.internal.cordappWithPackages
import net.corda.core.internal.hash
import net.corda.core.utilities.OpaqueBytes
import net.corda.finance.DOLLARS
import net.corda.finance.GBP
import net.corda.finance.POUNDS
import net.corda.finance.USD
import net.corda.finance.flows.CashIssueAndPaymentFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.finance.workflows.getCashBalance
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.RotatedSignerKeyConfiguration
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.internal.JarSignatureTestUtils.generateKey
import net.corda.testing.core.internal.SelfCleaningDir
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNodeParameters
import net.corda.testing.node.internal.MockNodeArgs
import net.corda.testing.node.internal.TestStartedNode
import net.corda.testing.node.internal.startFlow
import org.apache.commons.io.FileUtils.deleteDirectory
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import kotlin.io.path.div
import kotlin.test.assertEquals

class ContractWithRotatedKeyTest {
    private val ref = OpaqueBytes.of(0x01)

    private val TestStartedNode.party get() = info.legalIdentities.first()

    private lateinit var mockNet: InternalMockNetwork

    @Before
    fun setup() {
        mockNet = InternalMockNetwork(initialNetworkParameters = testNetworkParameters(minimumPlatformVersion = 8), notarySpecs = listOf(MockNetworkNotarySpec(
        DUMMY_NOTARY_NAME,
        validating = false
        )))
    }

    @After
    fun shutdown() {
        mockNet.stopNodes()
    }

    private fun restartNodeAndDeleteOldCorDapps(network: InternalMockNetwork,
                                                node: TestStartedNode,
                                                parameters: InternalMockNodeParameters = InternalMockNodeParameters(),
                                                nodeFactory: (MockNodeArgs) -> InternalMockNetwork.MockNode = network.defaultFactory
    ): TestStartedNode {
        node.internals.disableDBCloseOnStop()
        node.dispose()
        val cordappsDir = network.baseDirectory(node) / "cordapps"
        deleteDirectory(cordappsDir.toFile())
        return network.createNode(
                parameters.copy(legalName = node.internals.configuration.myLegalName, forcedID = node.internals.id),
                nodeFactory
        )
    }

    @Test(timeout = 300_000)
    fun `cordapp with rotated key continues to transact`() {
        val keyStoreDir1 = SelfCleaningDir()
        val keyStoreDir2 = SelfCleaningDir()

        val packageOwnerKey1 = keyStoreDir1.path.generateKey(alias="1-testcordapp-rsa")
        val packageOwnerKey2 = keyStoreDir2.path.generateKey(alias="1-testcordapp-rsa")

        val unsignedFinanceCorDapp1 = cordappWithPackages("net.corda.finance", "migration", "META-INF.services")
        val unsignedFinanceCorDapp2 = cordappWithPackages("net.corda.finance", "migration", "META-INF.services").copy(versionId = 2)

        val signedFinanceCorDapp1 = unsignedFinanceCorDapp1.signed( keyStoreDir1.path )
        val signedFinanceCorDapp2 = unsignedFinanceCorDapp2.signed( keyStoreDir2.path )

        val configOverrides = { conf: NodeConfiguration ->
            val rotatedKeys = listOf(RotatedSignerKeyConfiguration(listOf(packageOwnerKey1.hash.toString(), packageOwnerKey2.hash.toString())))
            doReturn(rotatedKeys).whenever(conf).rotatedCordappSignerKeys
        }

        val alice = mockNet.createNode(InternalMockNodeParameters(legalName = ALICE_NAME, additionalCordapps = listOf(signedFinanceCorDapp1), configOverrides = configOverrides))
        val bob = mockNet.createNode(InternalMockNodeParameters(legalName = BOB_NAME, additionalCordapps = listOf(signedFinanceCorDapp1), configOverrides = configOverrides))

        val flow1 = alice.services.startFlow(CashIssueAndPaymentFlow(300.DOLLARS, ref, alice.party, false, mockNet.defaultNotaryIdentity))
        val flow2 = alice.services.startFlow(CashIssueAndPaymentFlow(1000.DOLLARS, ref, bob.party, false, mockNet.defaultNotaryIdentity))
        val flow3 = bob.services.startFlow(CashIssueAndPaymentFlow(300.POUNDS, ref, bob.party, false, mockNet.defaultNotaryIdentity))
        val flow4 = bob.services.startFlow(CashIssueAndPaymentFlow(1000.POUNDS, ref, alice.party, false, mockNet.defaultNotaryIdentity))
        mockNet.runNetwork()
        flow1.resultFuture.getOrThrow()
        flow2.resultFuture.getOrThrow()
        flow3.resultFuture.getOrThrow()
        flow4.resultFuture.getOrThrow()

        val alice2 = restartNodeAndDeleteOldCorDapps(mockNet, alice, parameters = InternalMockNodeParameters(additionalCordapps = listOf(signedFinanceCorDapp2), configOverrides = configOverrides))
        val bob2 = restartNodeAndDeleteOldCorDapps(mockNet, bob, parameters = InternalMockNodeParameters(additionalCordapps = listOf(signedFinanceCorDapp2), configOverrides = configOverrides))

        assertEquals(alice.party, alice2.party)
        assertEquals(bob.party, bob2.party)
        assertEquals(alice2.party, alice2.services.identityService.wellKnownPartyFromX500Name(ALICE_NAME))
        assertEquals(bob2.party, alice2.services.identityService.wellKnownPartyFromX500Name(BOB_NAME))
        assertEquals(alice2.party, bob2.services.identityService.wellKnownPartyFromX500Name(ALICE_NAME))
        assertEquals(bob2.party, bob2.services.identityService.wellKnownPartyFromX500Name(BOB_NAME))

        val flow5 = alice2.services.startFlow(CashPaymentFlow(300.DOLLARS, bob2.party, false))
        val flow6 = bob2.services.startFlow(CashPaymentFlow(300.POUNDS, alice2.party, false))
        mockNet.runNetwork()
        val flow7 = bob2.services.startFlow(CashPaymentFlow(1300.DOLLARS, alice2.party, false))
        val flow8 = alice2.services.startFlow(CashPaymentFlow(1300.POUNDS, bob2.party, false))
        mockNet.runNetwork()

        flow5.resultFuture.getOrThrow()
        flow6.resultFuture.getOrThrow()
        flow7.resultFuture.getOrThrow()
        flow8.resultFuture.getOrThrow()

        assertEquals(1300.DOLLARS, alice2.services.getCashBalance(USD))
        assertEquals(0.POUNDS, alice2.services.getCashBalance(GBP))
        assertEquals(0.DOLLARS, bob2.services.getCashBalance(USD))
        assertEquals(1300.POUNDS, bob2.services.getCashBalance(GBP))

        keyStoreDir1.close()
        keyStoreDir2.close()
    }
}