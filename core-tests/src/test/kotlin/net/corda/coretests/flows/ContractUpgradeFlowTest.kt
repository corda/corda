package net.corda.coretests.flows

import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import net.corda.core.contracts.*
import net.corda.core.flows.UnexpectedFlowEndException
import net.corda.core.identity.AbstractParty
import net.corda.core.internal.Emoji
import net.corda.core.transactions.ContractUpgradeLedgerTransaction
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.USD
import net.corda.finance.`issued by`
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashIssueFlow
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyContractV2
import net.corda.testing.contracts.DummyContractV3
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
import net.corda.coretesting.internal.matchers.flow.willReturn
import net.corda.coretesting.internal.matchers.flow.willThrow
import net.corda.testing.node.internal.*
import org.junit.AfterClass
import org.junit.Test
import java.util.*

class ContractUpgradeFlowTest : WithContracts, WithFinality {

    companion object {
        private val classMockNet = InternalMockNetwork(cordappsForAllNodes = listOf(FINANCE_CONTRACTS_CORDAPP, FINANCE_WORKFLOWS_CORDAPP, DUMMY_CONTRACTS_CORDAPP, enclosedCordapp()))

        @JvmStatic
        @AfterClass
        fun tearDown() = classMockNet.stopNodes()
    }

    override val mockNet = classMockNet

    private val aliceNode = makeNode(ALICE_NAME)
    private val bobNode = makeNode(BOB_NAME)

    private val alice = aliceNode.info.singleIdentity()
    private val bob = bobNode.info.singleIdentity()
    private val notary = mockNet.defaultNotaryIdentity

    @Test(timeout=300_000)
	fun `2 parties contract upgrade`() {
        // Create dummy contract.
        val signedByA = aliceNode.signDummyContract(alice.ref(1), 0, bob.ref(1))
        val stx = bobNode.addSignatureTo(signedByA)

        aliceNode.finalise(stx, bob)

        val aliceTx = aliceNode.getValidatedTransaction(stx)
        val bobTx = bobNode.getValidatedTransaction(stx)

        // The request is expected to be rejected because party B hasn't authorised the upgrade yet.
        assertThat(
                aliceNode.initiateContractUpgrade(aliceTx, DummyContractV2::class),
                willThrow<UnexpectedFlowEndException>())

        // Party B authorises the contract state upgrade, and immediately de-authorises the same.
        assertThat(bobNode.authoriseContractUpgrade(bobTx, DummyContractV2::class), willReturn())
        assertThat(bobNode.deauthoriseContractUpgrade(bobTx), willReturn())

        // The request is expected to be rejected because party B has subsequently de-authorised a previously authorised upgrade.
        assertThat(
                aliceNode.initiateContractUpgrade(aliceTx, DummyContractV2::class),
                willThrow<UnexpectedFlowEndException>())

        // Party B authorises the contract state upgrade.
        assertThat(bobNode.authoriseContractUpgrade(bobTx, DummyContractV2::class), willReturn())

        // Party A initiates contract upgrade flow, expected to succeed this time.
        assertThat(
                aliceNode.initiateContractUpgrade(aliceTx, DummyContractV2::class),
                willReturn(
                        aliceNode.hasContractUpgradeTransaction<DummyContract.State, DummyContractV2.State>()
                                and bobNode.hasContractUpgradeTransaction<DummyContract.State, DummyContractV2.State>()))

        val upgradedState = aliceNode.getStateFromVault(DummyContractV2.State::class)

        // We now test that the upgraded state can be upgraded further, to V3.
        // Party B authorises the contract state upgrade.
        assertThat(bobNode.authoriseContractUpgrade(upgradedState, DummyContractV3::class), willReturn())

        // Party A initiates contract upgrade flow which is expected to succeed.
        assertThat(
                aliceNode.initiateContractUpgrade(upgradedState, DummyContractV3::class),
                willReturn(
                        aliceNode.hasContractUpgradeTransaction<DummyContractV2.State, DummyContractV3.State>()
                                and bobNode.hasContractUpgradeTransaction<DummyContractV2.State, DummyContractV3.State>()))
    }

    private fun TestStartedNode.issueCash(amount: Amount<Currency> = Amount(1000, USD)) =
            services.startFlow(CashIssueFlow(amount, OpaqueBytes.of(1), notary))
                    .andRunNetwork()
                    .resultFuture.getOrThrow()

    private fun TestStartedNode.getBaseStateFromVault() = getStateFromVault(ContractState::class)

    private fun TestStartedNode.getCashStateFromVault() = getStateFromVault(CashV2.State::class)

    private fun hasIssuedAmount(expected: Amount<Issued<Currency>>) =
            hasContractState(has(CashV2.State::amount, equalTo(expected)))

    private fun belongsTo(vararg recipients: AbstractParty) =
            hasContractState(has(CashV2.State::owners, equalTo(recipients.toList())))

    private fun <T : ContractState> hasContractState(expectation: Matcher<T>) =
            has<StateAndRef<T>, T>(
                    "contract state",
                    { it.state.data },
                    expectation)

    @Test(timeout=300_000)
	fun `upgrade Cash to v2`() {
        // Create some cash.
        val cashFlowResult = aliceNode.issueCash()
        val anonymisedRecipient = cashFlowResult.recipient!!
        val stateAndRef = cashFlowResult.stx.tx.outRef<Cash.State>(0)

        // The un-upgraded state is Cash.State
        assertThat(aliceNode.getBaseStateFromVault(), hasContractState(isA<Cash.State>(anything)))

        // Starts contract upgrade flow.
        assertThat(aliceNode.initiateContractUpgrade(stateAndRef, CashV2::class), willReturn())

        // Get contract state from the vault.
        val upgradedState = aliceNode.getCashStateFromVault()
        assertThat(upgradedState,
                hasIssuedAmount(Amount(1000000, USD) `issued by` (alice.ref(1)))
                        and belongsTo(anonymisedRecipient))

        // Make sure the upgraded state can be spent
        val movedState = upgradedState.state.data.copy(amount = upgradedState.state.data.amount.times(2))
        val spendUpgradedTx = aliceNode.signInitialTransaction {
            addInputState(upgradedState)
            addOutputState(
                    upgradedState.state.copy(data = movedState)
            )
            addCommand(CashV2.Move(), alice.owningKey)
        }

        assertThat(aliceNode.finalise(spendUpgradedTx), willReturn())
        assertThat(aliceNode.getCashStateFromVault(), hasContractState(equalTo(movedState)))
    }

    class CashV2 : UpgradedContractWithLegacyConstraint<Cash.State, CashV2.State> {
        override val legacyContract = Cash.PROGRAM_ID
        override val legacyContractConstraint: AttachmentConstraint
            get() = AlwaysAcceptAttachmentConstraint

        class Move : TypeOnlyCommandData()

        @BelongsToContract(CashV2::class)
        data class State(override val amount: Amount<Issued<Currency>>, val owners: List<AbstractParty>) : FungibleAsset<Currency> {
            override val owner: AbstractParty = owners.first()
            override val exitKeys = (owners + amount.token.issuer.party).map { it.owningKey }.toSet()
            override val participants = owners

            override fun withNewOwnerAndAmount(newAmount: Amount<Issued<Currency>>, newOwner: AbstractParty) = copy(amount = amount.copy(newAmount.quantity), owners = listOf(newOwner))
            override fun toString() = "${Emoji.bagOfCash}New Cash($amount at ${amount.token.issuer} owned by $owner)"
            override fun withNewOwner(newOwner: AbstractParty) = CommandAndState(Cash.Commands.Move(), copy(owners = listOf(newOwner)))
        }

        override fun upgrade(state: Cash.State) = State(state.amount.times(1000), listOf(state.owner))

        override fun verify(tx: LedgerTransaction) {}
    }

    //region Matchers
    private inline fun <reified FROM : Any, reified TO : Any> TestStartedNode.hasContractUpgradeTransaction() =
            has<StateAndRef<ContractState>, ContractUpgradeLedgerTransaction>(
                    "a contract upgrade transaction",
                    { getContractUpgradeTransaction(it) },
                    isUpgrade<FROM, TO>())

    private fun TestStartedNode.getContractUpgradeTransaction(state: StateAndRef<ContractState>) =
            services.validatedTransactions.getTransaction(state.ref.txhash)!!
                    .resolveContractUpgradeTransaction(services)

    private inline fun <reified FROM : Any, reified TO : Any> isUpgrade() =
            isUpgradeFrom<FROM>() and isUpgradeTo<TO>()

    private inline fun <reified T : Any> isUpgradeFrom() =
            has<ContractUpgradeLedgerTransaction, Any>("input data", { it.inputs.single().state.data }, isA<T>(anything))

    private inline fun <reified T : Any> isUpgradeTo() =
            has<ContractUpgradeLedgerTransaction, Any>("output data", { it.outputs.single().data }, isA<T>(anything))
    //endregion
}
