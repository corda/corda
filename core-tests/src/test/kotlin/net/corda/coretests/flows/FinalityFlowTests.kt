package net.corda.coretests.flows

import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import net.corda.core.contracts.Amount
import net.corda.core.flows.FinalityFlow
import net.corda.core.identity.Party
import net.corda.core.internal.PLATFORM_VERSION
import net.corda.core.internal.PlatformVersionSwitches
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.coretests.flows.WithFinality.FinalityInvoker
import net.corda.finance.POUNDS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.issuedBy
import net.corda.testing.core.*
import net.corda.coretesting.internal.matchers.flow.willReturn
import net.corda.coretesting.internal.matchers.flow.willThrow
import net.corda.finance.GBP
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.testing.node.internal.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test

class FinalityFlowTests : WithFinality {
    companion object {
        private val CHARLIE = TestIdentity(CHARLIE_NAME, 90).party
    }

    override val mockNet = InternalMockNetwork(cordappsForAllNodes = setOf(FINANCE_CONTRACTS_CORDAPP, FINANCE_WORKFLOWS_CORDAPP, enclosedCordapp(),
                                                       CustomCordapp(targetPlatformVersion = 3, classes = setOf(FinalityFlow::class.java))))

    private val aliceNode = makeNode(ALICE_NAME)

    private val notary = mockNet.defaultNotaryIdentity

    @After
    fun tearDown() = mockNet.stopNodes()

    @Test(timeout=300_000)
	fun `finalise a simple transaction`() {
        val bob = createBob()
        val stx = aliceNode.issuesCashTo(bob)

        assertThat(
                aliceNode.finalise(stx, bob.info.singleIdentity()),
                willReturn(
                        requiredSignatures(1)
                                and visibleTo(bob)))
    }

    @Test(timeout=300_000)
	fun `reject a transaction with unknown parties`() {
        // Charlie isn't part of this network, so node A won't recognise them
        val stx = aliceNode.issuesCashTo(CHARLIE)

        assertThat(
                aliceNode.finalise(stx),
                willThrow<IllegalArgumentException>())
    }

    @Test(timeout=300_000)
	fun `allow use of the old API if the CorDapp target version is 3`() {
        val oldBob = createBob(cordapps = listOf(tokenOldCordapp()))
        val stx = aliceNode.issuesCashTo(oldBob)
        @Suppress("DEPRECATION")
        aliceNode.startFlowAndRunNetwork(FinalityFlow(stx)).resultFuture.getOrThrow()
        assertThat(oldBob.services.validatedTransactions.getTransaction(stx.id)).isNotNull()
    }

    @Test(timeout=300_000)
	fun `broadcasting to both new and old participants`() {
        val newCharlie = mockNet.createNode(InternalMockNodeParameters(legalName = CHARLIE_NAME))
        val oldBob = createBob(cordapps = listOf(tokenOldCordapp()))
        val stx = aliceNode.issuesCashTo(oldBob)
        val resultFuture = aliceNode.startFlowAndRunNetwork(FinalityInvoker(
                stx,
                newRecipients = setOf(newCharlie.info.singleIdentity()),
                oldRecipients = setOf(oldBob.info.singleIdentity())
        )).resultFuture
        resultFuture.getOrThrow()
        assertThat(newCharlie.services.validatedTransactions.getTransaction(stx.id)).isNotNull()
        assertThat(oldBob.services.validatedTransactions.getTransaction(stx.id)).isNotNull()
    }

    @Test(timeout=300_000)
    fun `two phase finality flow transaction`() {
        val bobNode = createBob(platformVersion = PlatformVersionSwitches.TWO_PHASE_FINALITY)

        aliceNode.startFlow(CashIssueFlow(Amount(1000L, GBP), OpaqueBytes.of(1), notary)).resultFuture.getOrThrow()
        aliceNode.startFlowAndRunNetwork(CashPaymentFlow(Amount(100, GBP), bobNode.info.singleIdentity())).resultFuture.getOrThrow()
    }

    @Test(timeout=300_000)
    fun `two phase finality flow initiator to pre-2PF peer`() {
        val bobNode = createBob(platformVersion = PlatformVersionSwitches.TWO_PHASE_FINALITY - 1)
        aliceNode.startFlow(CashIssueFlow(Amount(1000L, GBP), OpaqueBytes.of(1), notary)).resultFuture.getOrThrow()
        aliceNode.startFlowAndRunNetwork(CashPaymentFlow(Amount(100, GBP), bobNode.info.singleIdentity())).resultFuture.getOrThrow()

    }

    @Test(timeout=300_000)
    fun `pre-2PF initiator to two phase finality flow peer`() {
        val bobNode = createBob(platformVersion = PlatformVersionSwitches.TWO_PHASE_FINALITY - 1)
        bobNode.startFlow(CashIssueFlow(Amount(1000L, GBP), OpaqueBytes.of(1), notary)).resultFuture.getOrThrow()
        bobNode.startFlowAndRunNetwork(CashPaymentFlow(Amount(100, GBP), aliceNode.info.singleIdentity())).resultFuture.getOrThrow()

    }

    private fun createBob(cordapps: List<TestCordappInternal> = emptyList(), platformVersion: Int = PLATFORM_VERSION): TestStartedNode {
        return mockNet.createNode(InternalMockNodeParameters(legalName = BOB_NAME, additionalCordapps = cordapps,
            version = MOCK_VERSION_INFO.copy(platformVersion = platformVersion)))
    }

    private fun TestStartedNode.issuesCashTo(recipient: TestStartedNode): SignedTransaction {
        return issuesCashTo(recipient.info.singleIdentity())
    }

    private fun TestStartedNode.issuesCashTo(other: Party): SignedTransaction {
        val amount = 1000.POUNDS.issuedBy(info.singleIdentity().ref(0))
        val builder = TransactionBuilder(notary)
        Cash().generateIssue(builder, amount, other, notary)
        return services.signInitialTransaction(builder)
    }

    /** "Old" CorDapp which will force its node to keep its FinalityHandler enabled */
    private fun tokenOldCordapp() = cordappWithPackages().copy(targetPlatformVersion = 3)
}
