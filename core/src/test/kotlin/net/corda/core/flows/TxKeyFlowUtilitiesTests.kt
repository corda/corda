package net.corda.core.flows

import net.corda.core.crypto.Party
import net.corda.core.utilities.DUMMY_BANK_A
import net.corda.core.utilities.DUMMY_BANK_B
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.testing.MOCK_IDENTITY_SERVICE
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
        net.identities += MOCK_IDENTITY_SERVICE.identities
    }

    @Test
    fun `issue key`() {
        // We run this in parallel threads to help catch any race conditions that may exist.
        net = MockNetwork(false, true)

        // Set up values we'll need
        val notaryNode = net.createNotaryNode(null, DUMMY_NOTARY.name)
        val bankANode = net.createPartyNode(notaryNode.info.address, DUMMY_BANK_A.name)
        val bankBNode = net.createPartyNode(notaryNode.info.address, DUMMY_BANK_B.name)
        val bankBKey: Party = bankBNode.services.myInfo.legalIdentity

        // Run the flows
        TxKeyFlow.registerFlowInitiator(bankBNode.services)
        val requesterFlow = bankANode.services.startFlow(TxKeyFlow.Requester(bankBKey))

        // Get the results
        val actual: PublicKey = requesterFlow.resultFuture.get().first
        assertNotNull(actual)
    }
}
