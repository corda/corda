package net.corda.node

import net.corda.core.CordaRuntimeException
import net.corda.core.contracts.TransactionVerificationException.ContractRejection
import net.corda.core.crypto.SecureHash.Companion.allOnesHash
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.flows.security.ForbiddenFlow
import net.corda.flows.security.SecureFlow
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
class SecureCordappTest {
    companion object {
        @JvmField
        val logger = loggerFor<SecureCordappTest>()

        @JvmField
        val contractCordapp = cordappWithPackages("net.corda.contracts.security").signed()

        @JvmField
        val workflowCordapp = cordappWithPackages("net.corda.flows.security").signed()

        @ClassRule
        @JvmField
        val security = OutOfProcessSecurityRule()

        fun parametersFor(vararg cordapps: TestCordapp): DriverParameters {
            return DriverParameters(
                portAllocation = incrementalPortAllocation(),
                startNodesInProcess = false,
                systemProperties = security.systemProperties,
                notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME, validating = true)),
                cordappsForAllNodes = cordapps.toList()
            )
        }
    }

    @Test(timeout = 300_000)
    fun `test contract with security manager`() {
        driver(parametersFor(workflowCordapp, contractCordapp)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val ex = assertThrows<ContractRejection> {
                alice.rpc.startFlow(::SecureFlow, allOnesHash)
                    .returnValue.getOrThrow()
            }
            assertThat(ex)
                .hasMessageStartingWith("Contract verification failed: access denied (\"java.lang.RuntimePermission\" \"getClassLoader\"),")
        }
    }

    @Test(timeout = 300_000)
    fun `test flow with security manager`() {
        driver(parametersFor(workflowCordapp, contractCordapp)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val ex = assertThrows<CordaRuntimeException> {
                alice.rpc.startFlow(::ForbiddenFlow)
                    .returnValue.getOrThrow()
            }
            assertThat(ex)
                .hasMessage("java.security.AccessControlException: access denied (\"java.lang.RuntimePermission\" \"getClassLoader\")")
        }
    }
}