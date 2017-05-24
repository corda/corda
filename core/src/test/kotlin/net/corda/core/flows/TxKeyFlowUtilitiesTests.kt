package net.corda.core.flows

import net.corda.core.identity.Party
import net.corda.core.utilities.ALICE
import net.corda.core.utilities.BOB
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.testing.node.MockNetwork
import org.junit.Before
import org.junit.Test
import java.security.PublicKey
import kotlin.test.assertNotNull

class TxKeyFlowUtilitiesTests {
    lateinit var net: MockNetwork

    @Before
    fun before() {
        net = MockNetwork(false)
    }

    @Test
    fun `issue key`() {
        // We run this in parallel threads to help catch any race conditions that may exist.
        net = MockNetwork(false, true)

        // Set up values we'll need
        val notaryNode = net.createNotaryNode(null, DUMMY_NOTARY.name)
        val aliceNode = net.createPartyNode(notaryNode.info.address, ALICE.name)
        val bobNode = net.createPartyNode(notaryNode.info.address, BOB.name)
        val bobKey: Party = bobNode.services.myInfo.legalIdentity

        // Run the flows
        bobNode.registerServiceFlow(TxKeyFlow.Requester::class) { TxKeyFlow.Provider(it) }
        val requesterFlow = aliceNode.services.startFlow(TxKeyFlow.Requester(bobKey))

        // Get the results
        val actual: PublicKey = requesterFlow.resultFuture.get().first
        assertNotNull(actual)
    }
}
