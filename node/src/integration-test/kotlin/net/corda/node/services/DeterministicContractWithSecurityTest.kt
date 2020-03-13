package net.corda.node.services

import net.corda.contracts.djvm.security.DeterministicSecureContract
import net.corda.core.crypto.SecureHash.Companion.allOnesHash
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.flows.djvm.security.DeterministicSecureFlow
import net.corda.node.DeterministicSourcesRule
import net.corda.node.OutOfProcessSecurityRule
import net.corda.node.internal.djvm.DeterministicVerificationException
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.TestCordapp
import net.corda.testing.node.internal.cordappWithPackages
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Test
import org.junit.jupiter.api.assertThrows

@Suppress("FunctionName")
class DeterministicContractWithSecurityTest {
    companion object {
        val logger = loggerFor<DeterministicContractWithSecurityTest>()

        @ClassRule
        @JvmField
        val djvmSources = DeterministicSourcesRule()

        @ClassRule
        @JvmField
        val security = OutOfProcessSecurityRule()

        @JvmField
        val flowCordapp = cordappWithPackages("net.corda.flows.djvm.security").signed()

        @JvmField
        val contractCordapp = cordappWithPackages("net.corda.contracts.djvm.security").signed()

        fun parametersFor(djvmSources: DeterministicSourcesRule, vararg cordapps: TestCordapp): DriverParameters {
            return DriverParameters(
                portAllocation = incrementalPortAllocation(),
                startNodesInProcess = false,
                systemProperties = security.systemProperties,
                notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME, validating = true)),
                cordappsForAllNodes = cordapps.toList(),
                djvmBootstrapSource = djvmSources.bootstrap,
                djvmCordaSource = djvmSources.corda
            )
        }
    }

    @Test(timeout=300_000)
    fun `test security policy is enforced inside sandbox`() {
        driver(parametersFor(djvmSources, flowCordapp, contractCordapp)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val ex = assertThrows<DeterministicVerificationException> {
                alice.rpc.startFlow(::DeterministicSecureFlow, allOnesHash)
                    .returnValue.getOrThrow()
            }
            assertThat(ex)
                .hasMessageStartingWith("sandbox.net.corda.core.contracts.TransactionVerificationException\$ContractRejection -> ")
                .hasMessageContaining(" access denied (\"java.lang.RuntimePermission\" \"closeClassLoader\"), ")
                .hasMessageContaining(" contract: sandbox.${DeterministicSecureContract::class.java.name}, ")
        }
    }
}