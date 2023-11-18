package net.corda.node

import net.corda.core.crypto.SecureHash
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.node.CustomSerializationSchemeDriverTest.CreateWireTxFlow
import net.corda.node.CustomSerializationSchemeDriverTest.WriteTxToLedgerFlow
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.node.internal.CustomCordapp
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNodeParameters
import net.corda.testing.node.internal.enclosedCordapp
import net.corda.testing.node.internal.startFlow
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CustomSerializationSchemeMockNetworkTest {

    private lateinit var mockNetwork : InternalMockNetwork

    val customSchemeCordapp: CustomCordapp = CustomSerializationSchemeDriverTest().enclosedCordapp()

    @Before
    fun setup() {
        mockNetwork = InternalMockNetwork(cordappsForAllNodes = listOf(customSchemeCordapp))
    }

    @After
    fun shutdown() {
        mockNetwork.stopNodes()
    }

    @Test(timeout = 300_000)
    fun `transactions network parameter hash is correct`() {
        val alice = mockNetwork.createNode(InternalMockNodeParameters(legalName = ALICE_NAME))
        val bob = mockNetwork.createNode(InternalMockNodeParameters(legalName = BOB_NAME))
        val flow = alice.services.startFlow (CreateWireTxFlow(bob.info.legalIdentities.single()))
        mockNetwork.runNetwork()
        val wireTx =  flow.resultFuture.get()
        /** The NetworkParmeters is the last component in the list of component groups. If we ever change this this
         *  in [net.corda.core.internal.createComponentGroups] this test will need to be updated.*/
        val serializedHash = SerializedBytes<SecureHash>(wireTx.componentGroups.last().components.single().bytes)
        assertEquals(alice.internals.networkParametersStorage.defaultHash, serializedHash.deserialize())
    }

    @Test(timeout = 300_000)
    fun `transaction can be written to the ledger`() {
        val alice = mockNetwork.createNode(InternalMockNodeParameters(legalName = ALICE_NAME))
        val bob = mockNetwork.createNode(InternalMockNodeParameters(legalName = BOB_NAME))
        val flow = alice.services.startFlow (WriteTxToLedgerFlow(bob.info.legalIdentities.single(),
                mockNetwork.notaryNodes.single().info.legalIdentities.single()))
        mockNetwork.runNetwork()
        val txId = flow.resultFuture.get()
        val getTxFlow = bob.services.startFlow(CustomSerializationSchemeDriverTest.GetTxFromDBFlow(txId))
        mockNetwork.runNetwork()
        assertNotNull(getTxFlow.resultFuture.get())
    }
}
