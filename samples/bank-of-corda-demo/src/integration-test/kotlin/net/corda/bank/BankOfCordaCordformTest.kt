package net.corda.bank

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.withoutIssuer
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.utils.sumCash
import net.corda.testing.core.BOC_NAME
import net.corda.testing.node.internal.demorun.nodeRunner
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class BankOfCordaCordformTest {
    @Test
    fun `run demo`() {
        BankOfCordaCordform().nodeRunner().scanPackages(listOf("net.corda.finance")).deployAndRunNodesAndThen {
            IssueCash.requestRpcIssue(20000.DOLLARS)
            CordaRPCClient(NetworkHostAndPort("localhost", BOC_RPC_PORT)).use(BOC_RPC_USER, BOC_RPC_PWD) {
                assertThat(it.proxy.vaultQuery(Cash.State::class.java).states).isEmpty()  // All of the issued cash is transferred
            }
            CordaRPCClient(NetworkHostAndPort("localhost", BIGCORP_RPC_PORT)).use(BIGCORP_RPC_USER, BIGCORP_RPC_PWD) {
                val cashStates = it.proxy.vaultQuery(Cash.State::class.java).states.map { it.state.data }
                val knownOwner = it.proxy.wellKnownPartyFromAnonymous(cashStates.map { it.owner }.toSet().single())
                assertThat(knownOwner?.name).isEqualTo(BIGCORP_NAME)
                val totalCash = cashStates.sumCash()
                assertThat(totalCash.token.issuer.party.nameOrNull()).isEqualTo(BOC_NAME)
                assertThat(totalCash.withoutIssuer()).isEqualTo(20000.DOLLARS)
            }
        }
    }
}
