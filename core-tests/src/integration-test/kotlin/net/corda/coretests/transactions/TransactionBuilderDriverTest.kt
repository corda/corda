package net.corda.coretests.transactions

import net.corda.core.internal.copyToDirectory
import net.corda.core.internal.hash
import net.corda.core.internal.toPath
import net.corda.core.messaging.startFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.coretesting.internal.delete
import net.corda.coretesting.internal.modifyJarManifest
import net.corda.coretesting.internal.useZipFile
import net.corda.finance.DOLLARS
import net.corda.finance.flows.CashIssueAndPaymentFlow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.internal.JarSignatureTestUtils.generateKey
import net.corda.testing.core.internal.JarSignatureTestUtils.signJar
import net.corda.testing.core.internal.JarSignatureTestUtils.unsignJar
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.NodeParameters
import net.corda.testing.node.internal.DriverDSLImpl
import net.corda.testing.node.internal.FINANCE_WORKFLOWS_CORDAPP
import net.corda.testing.node.internal.internalDriver
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.div
import kotlin.io.path.inputStream

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
        val (cordapp, dependency) = splitFinanceContractCordapp(currentFinanceContractsJar)
        internalDriver(cordappsForAllNodes = listOf(FINANCE_WORKFLOWS_CORDAPP), startNodesInProcess = false) {
            cordapp.inputStream().use(defaultNotaryNode.getOrThrow().rpc::uploadAttachment)
            dependency.inputStream().use(defaultNotaryNode.getOrThrow().rpc::uploadAttachment)

            // Start the node with the CorDapp but without the dependency
            cordapp.copyToDirectory((baseDirectory(ALICE_NAME) / "cordapps").createDirectories())
            val node = startNode(NodeParameters(ALICE_NAME)).getOrThrow()

            // First make sure the missing dependency causes an issue
            assertThatThrownBy {
                createTransaction(node)
            }.hasMessageContaining("java.lang.NoClassDefFoundError: net/corda/finance/contracts/asset")

            // Upload the missing dependency
            dependency.inputStream().use(node.rpc::uploadAttachment)

            val stx = createTransaction(node)
            assertThat(stx.tx.attachments).contains(cordapp.hash, dependency.hash)
        }
    }

    @Test(timeout=300_000)
    fun `adds legacy contracts CorDapp dependencies`() {
        val (legacyContracts, legacyDependency) = splitFinanceContractCordapp(legacyFinanceContractsJar)

        // Re-sign the current finance contracts CorDapp with the same key as the split legacy CorDapp
        val currentContracts = currentFinanceContractsJar.copyTo(Path("${currentFinanceContractsJar.toString().substringBeforeLast(".")}-RESIGNED.jar"), overwrite = true)
        currentContracts.unsignJar()
        signJar(currentContracts)

        internalDriver(
                cordappsForAllNodes = listOf(FINANCE_WORKFLOWS_CORDAPP),
                startNodesInProcess = false,
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4)
        ) {
            currentContracts.inputStream().use(defaultNotaryNode.getOrThrow().rpc::uploadAttachment)

            // Start the node with the legacy CorDapp but without the dependency
            legacyContracts.copyToDirectory((baseDirectory(ALICE_NAME) / "legacy-contracts").createDirectories())
            currentContracts.copyToDirectory((baseDirectory(ALICE_NAME) / "cordapps").createDirectories())
            val node = startNode(NodeParameters(ALICE_NAME)).getOrThrow()

            // First make sure the missing dependency causes an issue
            assertThatThrownBy {
                createTransaction(node)
            }.hasMessageContaining("java.lang.NoClassDefFoundError: net/corda/finance/contracts/asset")

            // Upload the missing dependency
            legacyDependency.inputStream().use(node.rpc::uploadAttachment)

            val stx = createTransaction(node)
            assertThat(stx.tx.legacyAttachments).contains(legacyContracts.hash, legacyDependency.hash)
        }
    }

    /**
     * Split the given finance contracts jar into two such that the second jar becomes a dependency to the first.
     */
    private fun splitFinanceContractCordapp(contractsJar: Path): Pair<Path, Path> {
        val cordapp = tempFolder.newFile("cordapp.jar").toPath()
        val dependency = tempFolder.newFile("cordapp-dep.jar").toPath()

        // Split the CorDapp into two
        contractsJar.copyTo(cordapp, overwrite = true)
        cordapp.useZipFile { cordappZipFs ->
            dependency.useZipFile { depZipFs ->
                val targetDir = depZipFs.getPath("net/corda/finance/contracts/asset").createDirectories()
                // CashUtilities happens to be a class that is only invoked in Cash.verify and so it's absence is only detected during
                // verification
                val clazz = cordappZipFs.getPath("net/corda/finance/contracts/asset/CashUtilities.class")
                clazz.copyToDirectory(targetDir)
                clazz.deleteExisting()
            }
        }
        cordapp.modifyJarManifest { manifest ->
            manifest.mainAttributes.delete("Sealed")
        }
        cordapp.unsignJar()

        // Sign both current and legacy CorDapps with the same key
        signJar(cordapp)
        // The dependency needs to be signed as it contains a package from the main jar
        signJar(dependency)

        return Pair(cordapp, dependency)
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
}
