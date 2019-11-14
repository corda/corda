package net.corda.node.services

import net.corda.contracts.djvm.broken.NonDeterministicContract.CurrentTimeMillis
import net.corda.contracts.djvm.broken.NonDeterministicContract.InstantNow
import net.corda.contracts.djvm.broken.NonDeterministicContract.NanoTime
import net.corda.contracts.djvm.broken.NonDeterministicContract.NoOperation
import net.corda.contracts.djvm.broken.NonDeterministicContract.RandomUUID
import net.corda.contracts.djvm.broken.NonDeterministicContract.WithReflection
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.flows.djvm.broken.NonDeterministicFlow
import net.corda.node.DeterministicSourcesRule
import net.corda.node.internal.djvm.DeterministicVerificationException
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.internal.CustomCordapp
import net.corda.testing.node.internal.cordappWithPackages
import org.assertj.core.api.AssertionsForInterfaceTypes.assertThat
import org.junit.ClassRule
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

@Suppress("FunctionName")
class NonDeterministicContractVerifyTest {
    companion object {
        val logger = loggerFor<NonDeterministicContractVerifyTest>()

        @ClassRule
        @JvmField
        val djvmSources = DeterministicSourcesRule()

        fun parametersFor(djvmSources: DeterministicSourcesRule): DriverParameters {
            return DriverParameters(
                portAllocation = incrementalPortAllocation(),
                startNodesInProcess = false,
                notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME, validating = true)),
                cordappsForAllNodes = listOf(
                    cordappWithPackages("net.corda.flows.djvm.broken"),
                    CustomCordapp(
                        packages = setOf("net.corda.contracts.djvm.broken"),
                        name = "nondeterministic-contract"
                    ).signed()
                ),
                djvmBootstrapSource = djvmSources.bootstrap,
                djvmCordaSource = djvmSources.corda
            )
        }
    }

    @Test
    fun `test DJVM rejects contract that uses Instant now`() {
        driver(parametersFor(djvmSources)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val ex = assertThrows<DeterministicVerificationException> {
                alice.rpc.startFlow(::NonDeterministicFlow, InstantNow())
                        .returnValue.getOrThrow()
            }
            assertThat(ex)
                .hasMessageStartingWith("NoSuchMethodError: sandbox.java.time.Instant.now()Lsandbox/java/time/Instant;, ")
        }
    }

    @Test
    fun `test DJVM rejects contract that uses System currentTimeMillis`() {
        driver(parametersFor(djvmSources)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val ex = assertThrows<DeterministicVerificationException> {
                alice.rpc.startFlow(::NonDeterministicFlow, CurrentTimeMillis())
                        .returnValue.getOrThrow()
            }
            assertThat(ex)
                .hasMessageStartingWith("NoSuchMethodError: sandbox.java.lang.System.currentTimeMillis()J, ")
        }
    }

    @Test
    fun `test DJVM rejects contract that uses System nanoTime`() {
        driver(parametersFor(djvmSources)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val ex = assertThrows<DeterministicVerificationException> {
                alice.rpc.startFlow(::NonDeterministicFlow, NanoTime())
                        .returnValue.getOrThrow()
            }
            assertThat(ex)
                .hasMessageStartingWith("NoSuchMethodError: sandbox.java.lang.System.nanoTime()J, ")
        }
    }

    @Test
    fun `test DJVM rejects contract that uses UUID randomUUID`() {
        driver(parametersFor(djvmSources)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val ex = assertThrows<DeterministicVerificationException> {
                alice.rpc.startFlow(::NonDeterministicFlow, RandomUUID())
                        .returnValue.getOrThrow()
            }
            assertThat(ex)
                .hasMessageStartingWith("NoSuchMethodError: sandbox.java.util.UUID.randomUUID()Lsandbox/java/util/UUID;, ")
        }
    }

    @Test
    fun `test DJVM rejects contract that uses reflection`() {
        driver(parametersFor(djvmSources)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val ex = assertThrows<DeterministicVerificationException> {
                alice.rpc.startFlow(::NonDeterministicFlow, WithReflection())
                        .returnValue.getOrThrow()
            }
            assertThat(ex).hasMessageStartingWith(
                "RuleViolationError: Disallowed reference to API; java.lang.Class.getDeclaredConstructor(Class[]), "
            )
        }
    }

    @Test
    fun `test DJVM can succeed`() {
        driver(parametersFor(djvmSources)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val txId = assertDoesNotThrow {
                alice.rpc.startFlow(::NonDeterministicFlow, NoOperation())
                        .returnValue.getOrThrow()
            }
            logger.info("TX-ID: {}", txId)
        }
    }
}
