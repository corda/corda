package net.corda.testing.node

import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.NotaryFlow
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.notary.NotaryService
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.api.ServiceHubInternal
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.internal.DUMMY_CONTRACTS_CORDAPP
import net.corda.testing.node.internal.enclosedCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.security.PublicKey
import java.util.*

class CustomNotaryTest {
    private lateinit var mockNet: MockNetwork
    private lateinit var notaryNode: StartedMockNode
    private lateinit var aliceNode: StartedMockNode
    private lateinit var notary: Party
    private lateinit var alice: Party

    @Before
    fun setup() {
        // START 1
        mockNet = MockNetwork(MockNetworkParameters(
                cordappsForAllNodes = listOf(DUMMY_CONTRACTS_CORDAPP, enclosedCordapp()),
                notarySpecs = listOf(MockNetworkNotarySpec(
                        name = CordaX500Name("Custom Notary", "Amsterdam", "NL"),
                        className = "net.corda.testing.node.CustomNotaryTest\$CustomNotaryService",
                        validating = false // Can also be validating if preferred.
                ))
        ))
        // END 1
        aliceNode = mockNet.createPartyNode(ALICE_NAME)
        notaryNode = mockNet.defaultNotaryNode
        notary = mockNet.defaultNotaryIdentity
        alice = aliceNode.info.singleIdentity()
    }

    @After
    fun tearDown() {
        mockNet.stopNodes()
    }

    @Test(expected = CustomNotaryException::class)
    fun `custom notary service is active`() {
        val tx = DummyContract.generateInitial(Random().nextInt(), notary, alice.ref(0))
        val stx = aliceNode.services.signInitialTransaction(tx)
        val future = aliceNode.startFlow(NotaryFlow.Client(stx))
        mockNet.runNetwork()
        future.getOrThrow()
    }

    class CustomNotaryService(override val services: ServiceHubInternal, override val notaryIdentityKey: PublicKey) : NotaryService() {

        override fun createServiceFlow(otherPartySession: FlowSession): FlowLogic<Void?> =
                object : FlowLogic<Void?>() {
                    override fun call(): Void? {
                        throw CustomNotaryException("Proof that a custom notary service is running!")
                    }
                }

        override fun start() {}
        override fun stop() {}
    }

    class CustomNotaryException(message: String) : FlowException(message)
}

