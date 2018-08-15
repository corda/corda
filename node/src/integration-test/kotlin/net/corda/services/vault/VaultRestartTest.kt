package net.corda.services.vault

import net.corda.core.CordaRuntimeException
import net.corda.core.contracts.FungibleAsset
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashIssueFlow
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.OutOfProcess
import net.corda.testing.driver.driver
import org.assertj.core.api.Assertions
import org.junit.Test

class VaultRestartTest {

    @Test
    fun `restart and query vault after adding some cash states`() {
        driver(DriverParameters(inMemoryDB = false, startNodesInProcess = false,
                                extraCordappPackagesToScan = listOf("net.corda.finance.contracts", "net.corda.finance.schemas"))) {
            val node = startNode(providedName = DUMMY_BANK_A_NAME, customOverrides = mapOf("p2pAddress" to "localhost:30000")).getOrThrow()

            val expected = 500.DOLLARS
            val ref = OpaqueBytes.of(0x01)
            val notary = node.rpc.notaryIdentities().firstOrNull() ?: throw CordaRuntimeException("Missing notary")
            val issueTx = node.rpc.startFlow(::CashIssueFlow, expected, ref, notary).returnValue.getOrThrow()
            println("Issued transaction: $issueTx")

            // Query vault
            Assertions.assertThat(node.rpc.vaultQueryBy<Cash.State>().states).hasSize(1)
            Assertions.assertThat(node.rpc.vaultQueryBy<FungibleAsset<*>>().states).hasSize(1)

            // Restart the node and re-query the vault
            println("Shutting down the node ...")
            (node as OutOfProcess).process.destroyForcibly()
            node.stop()

            println("Restarting the node ...")
            val restartedNode = startNode(providedName = DUMMY_BANK_A_NAME, customOverrides = mapOf("p2pAddress" to "localhost:30000")).getOrThrow()
            Assertions.assertThat(restartedNode.rpc.vaultQueryBy<Cash.State>().states).hasSize(1)
            Assertions.assertThat(restartedNode.rpc.vaultQueryBy<FungibleAsset<*>>().states).hasSize(1)
        }
    }
}
