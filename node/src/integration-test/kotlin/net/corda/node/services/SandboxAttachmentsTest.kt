package net.corda.node.services

import net.corda.contracts.djvm.attachment.SandboxAttachmentContract
import net.corda.contracts.djvm.attachment.SandboxAttachmentContract.ExtractFile
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.djvm.code.asResourcePath
import net.corda.flows.djvm.attachment.SandboxAttachmentFlow
import net.corda.node.DeterministicSourcesRule
import net.corda.node.OutOfProcessSecurityRule
import net.corda.node.internal.djvm.DeterministicVerificationException
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.internal.CustomCordapp
import net.corda.testing.node.internal.cordappWithPackages
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

@Suppress("FunctionName")
class SandboxAttachmentsTest {
    companion object {
        val logger = loggerFor<SandboxAttachmentsTest>()

        @ClassRule
        @JvmField
        val djvmSources = DeterministicSourcesRule()

        @ClassRule
        @JvmField
        val security = OutOfProcessSecurityRule()

        fun parametersFor(djvmSources: DeterministicSourcesRule): DriverParameters {
            return DriverParameters(
                portAllocation = incrementalPortAllocation(),
                startNodesInProcess = false,
                systemProperties = security.systemProperties,
                notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME, validating = true)),
                cordappsForAllNodes = listOf(
                    cordappWithPackages("net.corda.flows.djvm.attachment"),
                    CustomCordapp(
                        packages = setOf("net.corda.contracts.djvm.attachment"),
                        name = "sandbox-attachment-contract"
                    ).signed()
                ),
                djvmBootstrapSource = djvmSources.bootstrap,
                djvmCordaSource = djvmSources.corda
            )
        }
    }

    @Test(timeout=300_000)
	fun `test attachment accessible within sandbox`() {
        val extractFile = ExtractFile(SandboxAttachmentContract::class.java.name.asResourcePath + ".class")
        driver(parametersFor(djvmSources)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val txId = assertDoesNotThrow {
                alice.rpc.startFlow(::SandboxAttachmentFlow, extractFile)
                    .returnValue.getOrThrow()
            }
            logger.info("TX-ID: {}", txId)
        }
    }

    @Test(timeout=300_000)
	fun `test attachment file not found within sandbox`() {
        val extractFile = ExtractFile("does/not/Exist.class")
        driver(parametersFor(djvmSources)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val ex = assertThrows<DeterministicVerificationException> {
                alice.rpc.startFlow(::SandboxAttachmentFlow, extractFile)
                    .returnValue.getOrThrow()
            }
            assertThat(ex)
                .hasMessageStartingWith("sandbox.net.corda.core.contracts.TransactionVerificationException\$ContractRejection -> ")
                .hasMessageContaining(" Contract verification failed: does/not/Exist.class, ")
                .hasMessageContaining(" contract: sandbox.net.corda.contracts.djvm.attachment.SandboxAttachmentContract, ")
        }
    }
}
