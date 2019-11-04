package net.corda.contracts

import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.flows.CashIssueFlow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.internal.FINANCE_WORKFLOWS_CORDAPP
import net.corda.testing.node.internal.cordappWithPackages
import org.assertj.core.api.Assertions
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SignatureConstraintGatingTests {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Test
    fun `signature constraints can be used with up to the maximum allowed number of (RSA) keys`() {
        tempFolder.root.toPath().let {path ->
            val financeCordapp = cordappWithPackages("net.corda.finance.contracts", "net.corda.finance.schemas")
                                                    .signed(keyStorePath = path, numberOfSignatures = 20, keyAlgorithm = "RSA")

            driver(DriverParameters(
                    networkParameters = testNetworkParameters().copy(minimumPlatformVersion = 5),
                    cordappsForAllNodes = setOf(financeCordapp, FINANCE_WORKFLOWS_CORDAPP),
                    startNodesInProcess = true,
                    inMemoryDB = true
            )) {
                val node = startNode().getOrThrow()

                node.rpc.startFlowDynamic(CashIssueFlow::class.java, 10.DOLLARS, OpaqueBytes.of(0), defaultNotaryIdentity)
                        .returnValue.getOrThrow()
            }
        }
    }

    @Test
    fun `signature constraints can be used with up to the maximum allowed number of (EC) keys`() {
        tempFolder.root.toPath().let {path ->
            val financeCordapp = cordappWithPackages("net.corda.finance.contracts", "net.corda.finance.schemas")
                    .signed(keyStorePath = path, numberOfSignatures = 20, keyAlgorithm = "EC")

            driver(DriverParameters(
                    networkParameters = testNetworkParameters().copy(minimumPlatformVersion = 5),
                    cordappsForAllNodes = setOf(financeCordapp, FINANCE_WORKFLOWS_CORDAPP),
                    startNodesInProcess = true,
                    inMemoryDB = true
            )) {
                val node = startNode().getOrThrow()

                node.rpc.startFlowDynamic(CashIssueFlow::class.java, 10.DOLLARS, OpaqueBytes.of(0), defaultNotaryIdentity)
                        .returnValue.getOrThrow()
            }
        }
    }

    @Test
    fun `signature constraints cannot be used with more than the maximum allowed number of keys`() {
        tempFolder.root.toPath().let {path ->
            val financeCordapp = cordappWithPackages("net.corda.finance.contracts", "net.corda.finance.schemas")
                                                    .signed(keyStorePath = path, numberOfSignatures = 21)

            driver(DriverParameters(
                    networkParameters = testNetworkParameters().copy(minimumPlatformVersion = 5),
                    cordappsForAllNodes = setOf(financeCordapp, FINANCE_WORKFLOWS_CORDAPP),
                    startNodesInProcess = true,
                    inMemoryDB = true
            )) {
                val node = startNode().getOrThrow()

                Assertions.assertThatThrownBy {
                    node.rpc.startFlowDynamic(CashIssueFlow::class.java, 10.DOLLARS, OpaqueBytes.of(0), defaultNotaryIdentity)
                            .returnValue.getOrThrow()
                }
                .isInstanceOf(TransactionVerificationException.InvalidConstraintRejection::class.java)
                .hasMessageContaining("Signature constraint contains composite key with 21 leaf keys, " +
                                      "which is more than the maximum allowed number of keys (20).")
            }
        }
    }

}