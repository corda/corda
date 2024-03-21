package net.corda.coretests.transactions

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.TransactionState
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.internal.copyToDirectory
import net.corda.core.internal.hash
import net.corda.core.internal.mapToSet
import net.corda.core.internal.toPath
import net.corda.core.messaging.startFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.coretesting.internal.delete
import net.corda.coretesting.internal.modifyJarManifest
import net.corda.coretesting.internal.useZipFile
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.CommercialPaper
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashIssueAndPaymentFlow
import net.corda.finance.issuedBy
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.internal.JarSignatureTestUtils.generateKey
import net.corda.testing.core.internal.JarSignatureTestUtils.signJar
import net.corda.testing.core.internal.JarSignatureTestUtils.unsignJar
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.NodeParameters
import net.corda.testing.node.internal.DriverDSLImpl
import net.corda.testing.node.internal.FINANCE_WORKFLOWS_CORDAPP
import net.corda.testing.node.internal.enclosedCordapp
import net.corda.testing.node.internal.internalDriver
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.inputStream
import kotlin.io.path.isRegularFile
import kotlin.io.path.moveTo

class TransactionBuilderDriverTest {
    companion object {
        val currentFinanceContractsJar = this::class.java.getResource("/corda-finance-contracts.jar")!!.toPath()
        val legacyFinanceContractsJar = this::class.java.getResource("/corda-finance-contracts-4.11.jar")!!.toPath()
    }

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Before
    fun initJarSigner() {
        tempFolder.root.toPath().generateKey("testAlias", "testPassword", ALICE_NAME.toString())
    }

    private fun signJar(jar: Path) {
        tempFolder.root.toPath().signJar(jar.absolutePathString(), "testAlias", "testPassword")
    }

    @Test(timeout=300_000)
    fun `adds CorDapp dependencies`() {
        internalDriver(cordappsForAllNodes = listOf(FINANCE_WORKFLOWS_CORDAPP), startNodesInProcess = false) {
            val (cordapp, dependency) = splitFinanceContractCordapp(currentFinanceContractsJar)

            cordapp.inputStream().use(defaultNotaryNode.getOrThrow().rpc::uploadAttachment)
            dependency.inputStream().use(defaultNotaryNode.getOrThrow().rpc::uploadAttachment)

            // Start the node with the CorDapp but without the dependency
            cordapp.copyToDirectory((baseDirectory(ALICE_NAME) / "cordapps").createDirectories())
            val node = startNode(NodeParameters(ALICE_NAME)).getOrThrow()

            // First make sure the missing dependency causes an issue
            assertThatThrownBy {
                createTransaction(node)
            }.hasMessageContaining("Transaction being built has a missing attachment for class net/corda/finance/contracts/asset/")

            // Upload the missing dependency
            dependency.inputStream().use(node.rpc::uploadAttachment)

            val stx = createTransaction(node)
            assertThat(stx.tx.attachments).contains(cordapp.hash, dependency.hash)
        }
    }

    @Test(timeout=300_000)
    fun `adds legacy contracts CorDapp dependencies`() {
        internalDriver(
                cordappsForAllNodes = listOf(FINANCE_WORKFLOWS_CORDAPP),
                startNodesInProcess = false,
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4)
        ) {
            val (legacyContracts, legacyDependency) = splitFinanceContractCordapp(legacyFinanceContractsJar)
            // Re-sign the current finance contracts CorDapp with the same key as the split legacy CorDapp
            val currentContracts = currentFinanceContractsJar.copyTo(Path("${currentFinanceContractsJar.toString().substringBeforeLast(".")}-RESIGNED.jar"), overwrite = true)
            currentContracts.unsignJar()
            signJar(currentContracts)

            currentContracts.inputStream().use(defaultNotaryNode.getOrThrow().rpc::uploadAttachment)

            // Start the node with the legacy CorDapp but without the dependency
            legacyContracts.copyToDirectory((baseDirectory(ALICE_NAME) / "legacy-contracts").createDirectories())
            currentContracts.copyToDirectory((baseDirectory(ALICE_NAME) / "cordapps").createDirectories())
            val node = startNode(NodeParameters(ALICE_NAME)).getOrThrow()

            // First make sure the missing dependency causes an issue
            assertThatThrownBy {
                createTransaction(node)
            }.hasMessageContaining("Transaction being built has a missing legacy attachment for class net/corda/finance/contracts/asset/")

            // Upload the missing dependency
            legacyDependency.inputStream().use(node.rpc::uploadAttachment)

            val stx = createTransaction(node)
            assertThat(stx.tx.legacyAttachments).contains(legacyContracts.hash, legacyDependency.hash)
        }
    }

    @Test(timeout=300_000)
    fun `prevents transaction which is multi-contract but not backwards compatible because one of the contracts has missing legacy attachment`() {
        internalDriver(
                cordappsForAllNodes = listOf(FINANCE_WORKFLOWS_CORDAPP, enclosedCordapp()),
                startNodesInProcess = false,
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4),
                isDebug = true
        ) {
            val (currentCashContract, currentCpContract) = splitJar(currentFinanceContractsJar) { "CommercialPaper" in it.absolutePathString() }
            val (legacyCashContract, _) = splitJar(legacyFinanceContractsJar) { "CommercialPaper" in it.absolutePathString() }

            currentCashContract.inputStream().use(defaultNotaryNode.getOrThrow().rpc::uploadAttachment)
            currentCpContract.inputStream().use(defaultNotaryNode.getOrThrow().rpc::uploadAttachment)

            // The node has the legacy CommericalPaper contract missing
            val cordappsDir = (baseDirectory(ALICE_NAME) / "cordapps").createDirectories()
            currentCashContract.copyToDirectory(cordappsDir)
            currentCpContract.copyToDirectory(cordappsDir)
            legacyCashContract.copyToDirectory((baseDirectory(ALICE_NAME) / "legacy-contracts").createDirectories())

            val node = startNode(NodeParameters(ALICE_NAME)).getOrThrow()
            assertThatThrownBy { node.rpc.startFlow(::TwoContractTransactionFlow).returnValue.getOrThrow() }
                    .hasMessageContaining("Transaction being built has a missing legacy attachment")
                    .hasMessageContaining("CommercialPaper")
        }
    }

    /**
     * Split the given finance contracts jar into two such that the second jar becomes a dependency to the first.
     */
    private fun DriverDSLImpl.splitFinanceContractCordapp(contractsJar: Path): Pair<Path, Path> {
        return splitJar(contractsJar) { it.absolutePathString() == "/net/corda/finance/contracts/asset/CashUtilities.class" }
    }

    private fun DriverDSLImpl.splitJar(path: Path, move: (Path) -> Boolean): Pair<Path, Path> {
        val jar1 = Files.createTempFile(driverDirectory, "jar1-", ".jar")
        val jar2 = Files.createTempFile(driverDirectory, "jar2-", ".jar")

        path.copyTo(jar1, overwrite = true)
        jar1.useZipFile { zipFs1 ->
            jar2.useZipFile { zipFs2 ->
                Files.walk(zipFs1.getPath("/")).filter { it.isRegularFile() && move(it) }.forEach { file ->
                    val target = zipFs2.getPath(file.absolutePathString())
                    target.parent?.createDirectories()
                    file.moveTo(target)
                }
            }
        }
        jar1.modifyJarManifest { manifest ->
            manifest.mainAttributes.delete("Sealed")
        }
        jar1.unsignJar()

        signJar(jar1)
        signJar(jar2)

        return Pair(jar1, jar2)
    }

    private fun DriverDSLImpl.createTransaction(node: NodeHandle): SignedTransaction {
        return node.rpc.startFlow(
                ::CashIssueAndPaymentFlow,
                1.DOLLARS,
                OpaqueBytes.of(0x00),
                defaultNotaryIdentity,
                false,
                defaultNotaryIdentity
        ).returnValue.getOrThrow().stx
    }


    @StartableByRPC
    class TwoContractTransactionFlow : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val notary = serviceHub.networkMapCache.notaryIdentities[0]
            val builder = TransactionBuilder(notary)
            val issuer = ourIdentity.ref(OpaqueBytes.of(0x00))
            val amount = 1.DOLLARS.issuedBy(issuer)
            val signers = Cash().generateIssue(builder, amount, ourIdentity, notary)
            builder.addOutputState(TransactionState(CommercialPaper.State(issuer, ourIdentity, amount, Instant.MAX), notary = notary))
            builder.addCommand(CommercialPaper.Commands.Issue(), signers.first())
            builder.setTimeWindow(Instant.now(), Duration.ofMinutes(1))
            require(builder.outputStates().mapToSet { it.contract }.size > 1)
            serviceHub.signInitialTransaction(builder, signers)
        }
    }
}
