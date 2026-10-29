package net.corda.coretests.transactions

import net.corda.core.contracts.SignatureAttachmentConstraint
import net.corda.core.contracts.TransactionState
import net.corda.core.identity.Party
import net.corda.core.internal.PlatformVersionSwitches
import net.corda.core.internal.PlatformVersionSwitches.MIGRATE_ATTACHMENT_TO_SIGNATURE_CONSTRAINTS
import net.corda.core.internal.RPC_UPLOADER
import net.corda.core.internal.copyToDirectory
import net.corda.core.internal.hash
import net.corda.core.internal.toPath
import net.corda.core.internal.verification.ContractGeneration
import net.corda.core.internal.verification.ContractGeneration.CORDA_4_11
import net.corda.core.internal.verification.ContractGeneration.CORDA_4_12
import net.corda.core.internal.verification.ContractGeneration.CORDA_4_16
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.issuedBy
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyState
import net.corda.testing.core.DummyCommandData
import net.corda.testing.core.internal.JarSignatureTestUtils.unsignJar
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.internal.FINANCE_CONTRACTS_CORDAPP
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.MockNodeArgs
import net.corda.testing.node.internal.TestStartedNode
import net.corda.testing.node.internal.cordappWithPackages
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.inputStream

class TransactionBuilderMockNetworkTest {
    companion object {
        val legacyFinanceContractsJar = this::class.java.getResource("/corda-finance-contracts-4.11.jar")!!.toPath()
        // Corda 4.12 to 4.15 (Kotlin 1.9) version of the finance contracts
        val legacy19FinanceContractsJar = this::class.java.getResource("/corda-finance-contracts-4.14.jar")!!.toPath()
    }

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private val mockNetwork = InternalMockNetwork(
            cordappsForAllNodes = setOf(
                    FINANCE_CONTRACTS_CORDAPP,
                    cordappWithPackages("net.corda.testing.contracts").signed()
            ),
            initialNetworkParameters = testNetworkParameters(minimumPlatformVersion = MIGRATE_ATTACHMENT_TO_SIGNATURE_CONSTRAINTS)
    )

    @After
    fun close() {
        mockNetwork.close()
    }

    @Test(timeout=300_000)
    fun `automatic signature constraint`() {
        val services = mockNetwork.notaryNodes[0].services

        val attachment = services.attachments.openAttachment(services.attachments.getLatestContractAttachments(DummyContract.PROGRAM_ID)[0])
        val attachmentSigner = attachment!!.signerKeys.single()

        val expectedConstraint = SignatureAttachmentConstraint(attachmentSigner)
        assertThat(expectedConstraint.isSatisfiedBy(attachment)).isTrue()

        val outputState = TransactionState(data = DummyState(), contract = DummyContract.PROGRAM_ID, notary = mockNetwork.defaultNotaryIdentity)
        val builder = TransactionBuilder()
                .addOutputState(outputState)
                .addCommand(DummyCommandData, mockNetwork.defaultNotaryIdentity.owningKey)
        val wtx = builder.toWireTransaction(services)

        assertThat(wtx.outputs).containsOnly(outputState.copy(constraint = expectedConstraint))
    }

    @Test(timeout=300_000)
    fun `contract overlap in explicit attachments`() {
        val duplicateJar = tempFolder.newFile("duplicate.jar").toPath()
        FINANCE_CONTRACTS_CORDAPP.jarFile.copyTo(duplicateJar, overwrite = true)
        duplicateJar.unsignJar()  // Change its hash

        val node = mockNetwork.createNode()
        val duplicateId = duplicateJar.inputStream().use {
            node.services.attachments.privilegedImportAttachment(it, RPC_UPLOADER, null)
        }
        assertThat(FINANCE_CONTRACTS_CORDAPP.jarFile.hash).isNotEqualTo(duplicateId)

        val builder = TransactionBuilder()
        builder.addAttachment(FINANCE_CONTRACTS_CORDAPP.jarFile.hash)
        builder.addAttachment(duplicateId)
        val identity = node.info.singleIdentity()
        Cash().generateIssue(builder, 10.DOLLARS.issuedBy(identity.ref(0x00)), identity, mockNetwork.defaultNotaryIdentity)
        assertThatIllegalArgumentException()
                .isThrownBy { builder.toWireTransaction(node.services) }
                .withMessageContaining("Multiple attachments specified for the same contract")
    }

    @Test(timeout=300_000)
    fun `populates legacy attachment group if legacy contract CorDapp is present`() {
        val node = mockNetwork.createNode { args ->
            args.copyToLegacyContracts(CORDA_4_11, legacyFinanceContractsJar)
            InternalMockNetwork.MockNode(args)
        }
        val builder = TransactionBuilder()
        val identity = node.info.singleIdentity()
        Cash().generateIssue(builder, 10.DOLLARS.issuedBy(identity.ref(0x00)), identity, mockNetwork.defaultNotaryIdentity)
        val stx = node.services.signInitialTransaction(builder)
        assertThat(stx.tx.attachmentsFor(ContractGeneration.CURRENT)).contains(FINANCE_CONTRACTS_CORDAPP.jarFile.hash)
        assertThat(stx.tx.legacyAttachments).contains(legacyFinanceContractsJar.hash)
        stx.verify(node.services)
    }

    @Test(timeout=300_000)
    fun `populates 4_12 to 4_15 legacy attachment group if Kotlin 1_9 legacy contract CorDapp is present`() {
        val node = mockNetwork.createNode { args ->
            args.copyToLegacyContracts(CORDA_4_12, legacy19FinanceContractsJar)
            InternalMockNetwork.MockNode(args)
        }
        val stx = signCashIssuance(node, mockNetwork.defaultNotaryIdentity)
        assertThat(stx.tx.attachmentGenerations).containsExactly(CORDA_4_12, CORDA_4_16)
        assertThat(stx.tx.attachmentsFor(CORDA_4_16)).contains(FINANCE_CONTRACTS_CORDAPP.jarFile.hash)
        assertThat(stx.tx.attachmentsFor(CORDA_4_12)).contains(legacy19FinanceContractsJar.hash)
        assertThat(stx.tx.legacyAttachments).isEmpty()
        // The 4.12 to 4.15 nodes only see the attachments group they understand
        assertThat(stx.tx.attachments).isEqualTo(stx.tx.attachmentsFor(CORDA_4_16))
        // Verified in-process and by the Kotlin 1.9 external verifier
        stx.verify(node.services)
    }

    @Test(timeout=300_000)
    fun `populates every legacy attachment group if legacy contract CorDapps of every generation are present`() {
        val node = mockNetwork.createNode { args ->
            args.copyToLegacyContracts(CORDA_4_11, legacyFinanceContractsJar)
            args.copyToLegacyContracts(CORDA_4_12, legacy19FinanceContractsJar)
            InternalMockNetwork.MockNode(args)
        }
        val stx = signCashIssuance(node, mockNetwork.defaultNotaryIdentity)
        assertThat(stx.tx.attachmentGenerations).containsExactly(CORDA_4_11, CORDA_4_12, CORDA_4_16)
        assertThat(stx.tx.attachmentsFor(CORDA_4_16)).contains(FINANCE_CONTRACTS_CORDAPP.jarFile.hash)
        assertThat(stx.tx.attachmentsFor(CORDA_4_12)).contains(legacy19FinanceContractsJar.hash)
        assertThat(stx.tx.attachmentsFor(CORDA_4_11)).contains(legacyFinanceContractsJar.hash)
        // Verified in-process and by both the Kotlin 1.2 and 1.9 external verifiers
        stx.verify(node.services)
    }
}

private fun signCashIssuance(node: TestStartedNode, notary: Party): SignedTransaction {
    val builder = TransactionBuilder()
    val identity = node.info.singleIdentity()
    Cash().generateIssue(builder, 10.DOLLARS.issuedBy(identity.ref(0x00)), identity, notary)
    return node.services.signInitialTransaction(builder)
}

private fun MockNodeArgs.copyToLegacyContracts(generation: ContractGeneration, vararg jars: Path) {
    val legacyContractsDir = (config.baseDirectory / generation.legacyContractsDirName!!).createDirectories()
    jars.forEach { it.copyToDirectory(legacyContractsDir) }
}

/**
 * Separate from [TransactionBuilderMockNetworkTest] as it needs its own mock network.
 */
class TransactionBuilderMinimumPlatformVersionMockNetworkTest {
    private val mockNetwork = InternalMockNetwork(
            cordappsForAllNodes = setOf(FINANCE_CONTRACTS_CORDAPP),
            // The network has no 4.11 nodes
            initialNetworkParameters = testNetworkParameters(minimumPlatformVersion = PlatformVersionSwitches.LEGACY_ATTACHMENTS)
    )

    @After
    fun close() {
        mockNetwork.close()
    }

    @Test(timeout=300_000)
    fun `legacy attachment groups are not populated if not needed by the network's minimum platform version`() {
        val node = mockNetwork.createNode { args ->
            args.copyToLegacyContracts(CORDA_4_11, TransactionBuilderMockNetworkTest.legacyFinanceContractsJar)
            args.copyToLegacyContracts(CORDA_4_12, TransactionBuilderMockNetworkTest.legacy19FinanceContractsJar)
            InternalMockNetwork.MockNode(args)
        }
        val stx = signCashIssuance(node, mockNetwork.defaultNotaryIdentity)
        assertThat(stx.tx.attachmentGenerations).containsExactly(CORDA_4_12, CORDA_4_16)
        stx.verify(node.services)
    }
}
