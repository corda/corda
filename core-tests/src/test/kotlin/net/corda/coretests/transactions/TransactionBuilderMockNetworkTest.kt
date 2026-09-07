package net.corda.coretests.transactions

import net.corda.core.contracts.SignatureAttachmentConstraint
import net.corda.core.contracts.TransactionState
import net.corda.core.internal.PlatformVersionSwitches.MIGRATE_ATTACHMENT_TO_SIGNATURE_CONSTRAINTS
import net.corda.core.internal.RPC_UPLOADER
import net.corda.core.internal.copyToDirectory
import net.corda.core.internal.hash
import net.corda.core.internal.toPath
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

@Suppress("INVISIBLE_MEMBER")
class TransactionBuilderMockNetworkTest {
    companion object {
        val legacyFinanceContractsJar = this::class.java.getResource("/corda-finance-contracts-4.11.jar")!!.toPath()
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
            args.copyToLegacyContracts(legacyFinanceContractsJar)
            InternalMockNetwork.MockNode(args)
        }
        val builder = TransactionBuilder()
        val identity = node.info.singleIdentity()
        Cash().generateIssue(builder, 10.DOLLARS.issuedBy(identity.ref(0x00)), identity, mockNetwork.defaultNotaryIdentity)
        val stx = node.services.signInitialTransaction(builder)
        assertThat(stx.tx.nonLegacyAttachments).contains(FINANCE_CONTRACTS_CORDAPP.jarFile.hash)
        assertThat(stx.tx.legacyAttachments).contains(legacyFinanceContractsJar.hash)
        stx.verify(node.services)
    }

    private fun MockNodeArgs.copyToLegacyContracts(vararg jars: Path) {
        val legacyContractsDir = (config.baseDirectory / "legacy-contracts").createDirectories()
        jars.forEach { it.copyToDirectory(legacyContractsDir) }
    }
}
