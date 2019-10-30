package net.corda.contracts

import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.node.ZoneVersionTooLowException
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
    fun `signature constraints with composite keys can be used successfully, when min platform version is high enough`() {
        tempFolder.root.toPath().let {path ->
            val financeCordapp = cordappWithPackages("net.corda.finance.contracts", "net.corda.finance.schemas")
                                                    .signed(keyStorePath = path, numberOfSignatures = 2)

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
    fun `signature constraints with composite keys are disallowed, when min platform version is lower than needed`() {
        tempFolder.root.toPath().let {path ->
            val financeCordapp = cordappWithPackages("net.corda.finance.contracts", "net.corda.finance.schemas")
                                                    .signed(keyStorePath = path, numberOfSignatures = 2)

            driver(DriverParameters(
                    networkParameters = testNetworkParameters().copy(minimumPlatformVersion = 4),
                    cordappsForAllNodes = setOf(financeCordapp, FINANCE_WORKFLOWS_CORDAPP),
                    startNodesInProcess = true,
                    inMemoryDB = true
            )) {
                val node = startNode().getOrThrow()

                Assertions.assertThatThrownBy {
                    node.rpc.startFlowDynamic(CashIssueFlow::class.java, 10.DOLLARS, OpaqueBytes.of(0), defaultNotaryIdentity)
                            .returnValue.getOrThrow()
                }
                .isInstanceOf(ZoneVersionTooLowException::class.java)
                .hasMessageContaining("Composite keys for signature constraints requires all nodes on the Corda compatibility zone to " +
                        "be running at least platform version 5. The current zone is only enforcing a minimum platform version of 4")
            }
        }
    }

    @Test
    fun `signature constraints can be used with up to the maximum allowed number of keys`() {
        tempFolder.root.toPath().let {path ->
            val financeCordapp = cordappWithPackages("net.corda.finance.contracts", "net.corda.finance.schemas")
                                                    .signed(keyStorePath = path, numberOfSignatures = 20)

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