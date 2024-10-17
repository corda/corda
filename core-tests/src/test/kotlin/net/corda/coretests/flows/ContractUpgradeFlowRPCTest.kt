package net.corda.coretests.flows

import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.anything
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.isA
import net.corda.core.CordaRuntimeException
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.ContractUpgradeFlow
import net.corda.core.internal.getRequiredTransaction
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.transactions.ContractUpgradeLedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.node.services.Permissions.Companion.startFlow
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyContractV2
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
import net.corda.coretesting.internal.matchers.rpc.willReturn
import net.corda.coretesting.internal.matchers.rpc.willThrow
import net.corda.testing.node.User
import net.corda.testing.node.internal.*
import org.junit.AfterClass
import org.junit.Test

class ContractUpgradeFlowRPCTest : WithContracts, WithFinality {
    companion object {
        private val classMockNet = InternalMockNetwork(cordappsForAllNodes = listOf(DUMMY_CONTRACTS_CORDAPP, enclosedCordapp()))

        @JvmStatic
        @AfterClass
        fun tearDown() = classMockNet.stopNodes()
    }

    override val mockNet = classMockNet

    private val aliceNode = makeNode(ALICE_NAME)
    private val bobNode = makeNode(BOB_NAME)

    private val alice = aliceNode.info.singleIdentity()
    private val bob = bobNode.info.singleIdentity()

    @Test(timeout=300_000)
	fun `2 parties contract upgrade using RPC`() = rpcDriver {
        val testUser = createTestUser()
        val rpcA = startProxy(aliceNode, testUser)
        val rpcB = startProxy(bobNode, testUser)

        // Create, sign and finalise dummy contract.
        val signedByA = aliceNode.signDummyContract(alice.ref(1), 0, bob.ref(1))
        val stx = bobNode.addSignatureTo(signedByA)
        assertThat(rpcA.finalise(stx, bob), willReturn())

        val atx = aliceNode.getValidatedTransaction(stx)
        val btx = bobNode.getValidatedTransaction(stx)

        // Cannot upgrade contract without prior authorisation from counterparty
        assertThat(
                rpcA.initiateDummyContractUpgrade(atx),
                willThrow<CordaRuntimeException>())

        // Party B authorises the contract state upgrade, and immediately deauthorises the same.
        assertThat(rpcB.authoriseDummyContractUpgrade(btx), willReturn())
        assertThat(rpcB.deauthoriseContractUpgrade(btx), willReturn())

        // Cannot upgrade contract if counterparty has deauthorised a previously-given authority
        assertThat(
                rpcA.initiateDummyContractUpgrade(atx),
                willThrow<CordaRuntimeException>())

        // Party B authorise the contract state upgrade.
        assertThat(rpcB.authoriseDummyContractUpgrade(btx), willReturn())

        // Party A initiates contract upgrade flow, expected to succeed this time.
        assertThat(
                rpcA.initiateDummyContractUpgrade(atx),
                willReturn(
                        aliceNode.hasDummyContractUpgradeTransaction()
                                and bobNode.hasDummyContractUpgradeTransaction()))
    }

    //region RPC DSL
    private fun RPCDriverDSL.startProxy(node: TestStartedNode, user: User): CordaRPCOps {
        return startRpcClient<CordaRPCOps>(
                rpcAddress = startRpcServer(
                        rpcUser = user,
                        ops = node.cordaRPCOps
                ).get().broker.hostAndPort!!,
                username = user.username,
                password = user.password
        ).get()
    }

    private fun createTestUser() = rpcTestUser.copy(permissions = setOf(
            startFlow<WithFinality.FinalityInvoker>(),
            startFlow<ContractUpgradeFlow.Initiate<*, *>>(),
            startFlow<ContractUpgradeFlow.Authorise>(),
            startFlow<ContractUpgradeFlow.Deauthorise>()
    ))
    //endregion

    //region Operations
    private fun CordaRPCOps.initiateDummyContractUpgrade(tx: SignedTransaction) =
            initiateContractUpgrade(tx, DummyContractV2::class)

    private fun CordaRPCOps.authoriseDummyContractUpgrade(tx: SignedTransaction) =
            authoriseContractUpgrade(tx, DummyContractV2::class)
    //endregion

    //region Matchers
    private fun TestStartedNode.hasDummyContractUpgradeTransaction() =
            hasContractUpgradeTransaction<DummyContract.State, DummyContractV2.State>()

    private inline fun <reified FROM : Any, reified TO : Any> TestStartedNode.hasContractUpgradeTransaction() =
            has<StateAndRef<ContractState>, ContractUpgradeLedgerTransaction>(
                    "a contract upgrade transaction",
                    { getContractUpgradeTransaction(it) },
                    isUpgrade<FROM, TO>())

    private fun TestStartedNode.getContractUpgradeTransaction(state: StateAndRef<ContractState>) =
            services.getRequiredTransaction(state.ref.txhash).resolveContractUpgradeTransaction(services)

    private inline fun <reified FROM : Any, reified TO : Any> isUpgrade() =
            isUpgradeFrom<FROM>() and isUpgradeTo<TO>()

    private inline fun <reified T : Any> isUpgradeFrom() =
            has<ContractUpgradeLedgerTransaction, Any>("input data", { it.inputs.single().state.data }, isA<T>(anything))

    private inline fun <reified T : Any> isUpgradeTo() =
            has<ContractUpgradeLedgerTransaction, Any>("output data", { it.outputs.single().data }, isA<T>(anything))
    //endregion
}
