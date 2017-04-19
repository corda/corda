package net.corda.core.flows

import net.corda.core.contracts.DummyContract
import net.corda.core.crypto.Party
import net.corda.core.getOrThrow
import net.corda.core.node.PluginServiceHub
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.DUMMY_KEY_1
import net.corda.flows.AbstractCollectSignaturesFlowResponder
import net.corda.flows.CollectSignaturesFlow
import net.corda.testing.node.MockNetwork
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.security.SignatureException
import kotlin.test.assertFailsWith

class CollectSignaturesFlowTests {
    lateinit var mockNet: MockNetwork
    lateinit var a: MockNetwork.MockNode
    lateinit var b: MockNetwork.MockNode
    lateinit var c: MockNetwork.MockNode
    lateinit var notary: Party

    @Before
    fun setup() {
        mockNet = MockNetwork()
        val nodes = mockNet.createSomeNodes(3)
        a = nodes.partyNodes[0]
        b = nodes.partyNodes[1]
        c = nodes.partyNodes[2]
        notary = nodes.notaryNode.info.notaryIdentity
        mockNet.runNetwork()
        CollectSigsTestCorDapp.registerFlows(a.services)
        CollectSigsTestCorDapp.registerFlows(b.services)
        CollectSigsTestCorDapp.registerFlows(c.services)
    }

    @After
    fun tearDown() {
        mockNet.stopNodes()
    }

    // Our sub-classed AbstractCollectSignaturesFlow for testing.
    class CollectSignaturesFlowResponder(otherParty: Party) : AbstractCollectSignaturesFlowResponder(otherParty) {
        override fun checkTransaction(stx: SignedTransaction) = Unit
    }

    object CollectSigsTestCorDapp {
        // Would normally be called by custom service init in a CorDapp.
        fun registerFlows(pluginHub: PluginServiceHub) {
            pluginHub.registerFlowInitiator(CollectSignaturesFlow::class.java, ::CollectSignaturesFlowResponder)
        }
    }

    @Test
    fun `successfully collects two signatures`() {
        val threePartyDummyContract = DummyContract.generateInitial(1337, notary,
                a.info.legalIdentity.ref(1),
                b.info.legalIdentity.ref(2),
                c.info.legalIdentity.ref(3))
        val ptx = threePartyDummyContract.signWith(a.services.legalIdentityKey).toSignedTransaction(false)
        val flow = a.services.startFlow(CollectSignaturesFlow(ptx))
        mockNet.runNetwork()
        val result = flow.resultFuture.getOrThrow()
        println(result.tx)
        println(result.sigs)
    }

    @Test
    fun `no need to collect any signatures`() {
        val onePartyDummyContract = DummyContract.generateInitial(1337, notary, a.info.legalIdentity.ref(1))
        val ptx = onePartyDummyContract.signWith(a.services.legalIdentityKey).toSignedTransaction(false)
        val flow = a.services.startFlow(CollectSignaturesFlow(ptx))
        mockNet.runNetwork()
        val result = flow.resultFuture.getOrThrow()
        println(result.tx)
        println(result.sigs)
    }

    @Test
    fun `fails with no initiator signature`() {
        val twoPartyDummyContract = DummyContract.generateInitial(1337, notary,
                a.info.legalIdentity.ref(1),
                b.info.legalIdentity.ref(2))
        val ptx = twoPartyDummyContract.signWith(DUMMY_KEY_1).toSignedTransaction(false)
        val flow = a.services.startFlow(CollectSignaturesFlow(ptx))
        mockNet.runNetwork()
        assertFailsWith<SignatureException> { flow.resultFuture.getOrThrow() }
    }

    @Test
    fun `fails with more than one initial signature`() {
        val twoPartyDummyContract = DummyContract.generateInitial(1337, notary,
                a.info.legalIdentity.ref(1),
                b.info.legalIdentity.ref(2))
        val ptx = twoPartyDummyContract.signWith(a.services.legalIdentityKey).signWith(b.services.legalIdentityKey).toSignedTransaction(false)
        val flow = a.services.startFlow(CollectSignaturesFlow(ptx))
        mockNet.runNetwork()
        assertFailsWith<IllegalStateException>("There should only be the Initiators signature present.") {
            flow.resultFuture.getOrThrow()
        }
    }
}

