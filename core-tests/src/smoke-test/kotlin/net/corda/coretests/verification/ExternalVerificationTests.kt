package net.corda.coretests.verification

import net.corda.client.rpc.CordaRPCClientConfiguration
import net.corda.client.rpc.notUsed
import net.corda.core.contracts.Amount
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.UnexpectedFlowEndException
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.PlatformVersionSwitches.MIGRATE_ATTACHMENT_TO_SIGNATURE_CONSTRAINTS
import net.corda.core.internal.toPath
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.node.NodeInfo
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.coretests.verification.VerificationType.BOTH
import net.corda.coretests.verification.VerificationType.EXTERNAL
import net.corda.finance.DOLLARS
import net.corda.finance.USD
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.AbstractCashFlow
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.finance.workflows.getCashBalance
import net.corda.nodeapi.internal.config.User
import net.corda.smoketesting.NodeParams
import net.corda.smoketesting.NodeProcess
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.cordapps.workflows411.IssueAndChangeNotaryFlow
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.internal.JarSignatureTestUtils.unsignJar
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import rx.Observable
import java.net.InetAddress
import java.nio.file.Path
import java.util.Currency
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.Path
import kotlin.io.path.copyTo
import kotlin.io.path.div
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText

class ExternalVerificationSignedCordappsTest {
    private companion object {
        private val factory = NodeProcess.Factory(testNetworkParameters(minimumPlatformVersion = MIGRATE_ATTACHMENT_TO_SIGNATURE_CONSTRAINTS))

        private lateinit var notaries: List<NodeProcess>
        private lateinit var oldNode: NodeProcess
        private lateinit var currentNode: NodeProcess

        @BeforeClass
        @JvmStatic
        fun startNodes() {
            val (legacyContractsCordapp, legacyWorkflowsCordapp) = listOf("contracts", "workflows").map { smokeTestResource("corda-finance-$it-4.11.jar") }
            // The current version finance CorDapp jars
            val currentCordapps = listOf("contracts", "workflows").map { smokeTestResource("corda-finance-$it.jar") }

            notaries = factory.createNotaries(
                    nodeParams(DUMMY_NOTARY_NAME, cordappJars = currentCordapps, legacyContractJars = listOf(legacyContractsCordapp)),
                    nodeParams(CordaX500Name("Notary Service 2", "Zurich", "CH"), currentCordapps)
            )
            oldNode = factory.createNode(nodeParams(
                    CordaX500Name("Old", "Delhi", "IN"),
                    listOf(legacyContractsCordapp, legacyWorkflowsCordapp, smokeTestResource("4.11-workflows-cordapp.jar")),
                    clientRpcConfig = CordaRPCClientConfiguration(minimumServerProtocolVersion = 13),
                    version = "4.11"
            ))
            currentNode = factory.createNode(nodeParams(
                    CordaX500Name("New", "York", "US"),
                    currentCordapps,
                    listOf(legacyContractsCordapp)
            ))
        }

        @AfterClass
        @JvmStatic
        fun close() {
            factory.close()
        }
    }

    @Test(timeout=300_000)
    fun `transaction containing 4_11 contract attachment only sent to current node`() {
        val (issuanceTx, paymentTx) = cashIssuanceAndPayment(issuer = oldNode, recipient = currentNode)
        notaries[0].assertTransactionsWereVerified(EXTERNAL, paymentTx.id)
        currentNode.assertTransactionsWereVerified(EXTERNAL, issuanceTx.id, paymentTx.id)
    }

    @Test(timeout=300_000)
    fun `transaction containing 4_11 and 4_12 contract attachments sent to old node`() {
        val (issuanceTx, paymentTx) = cashIssuanceAndPayment(issuer = currentNode, recipient = oldNode)
        notaries[0].assertTransactionsWereVerified(BOTH, paymentTx.id)
        currentNode.assertTransactionsWereVerified(BOTH, issuanceTx.id, paymentTx.id)
    }

    @Test(timeout=300_000)
    fun `notary change transaction`() {
        val oldRpc = oldNode.connect(superUser).proxy
        val oldNodeInfo = oldRpc.nodeInfo()
        val notaryIdentities = oldRpc.notaryIdentities()
        for (notary in notaries) {
            notary.connect(superUser).use { it.proxy.waitForVisibility(oldNodeInfo) }
        }
        oldRpc.startFlow(::IssueAndChangeNotaryFlow, notaryIdentities[0], notaryIdentities[1]).returnValue.getOrThrow()
    }

    private fun cashIssuanceAndPayment(issuer: NodeProcess, recipient: NodeProcess): Pair<SignedTransaction, SignedTransaction> {
        val issuerRpc = issuer.connect(superUser).proxy
        val recipientRpc = recipient.connect(superUser).proxy
        val recipientNodeInfo = recipientRpc.nodeInfo()
        val notaryIdentity = issuerRpc.notaryIdentities()[0]

        val beforeAmount = recipientRpc.getCashBalance(USD)

        val (issuanceTx) = issuerRpc.startFlow(
                ::CashIssueFlow,
                10.DOLLARS,
                OpaqueBytes.of(0x01),
                notaryIdentity
        ).returnValue.getOrThrow()

        issuerRpc.waitForVisibility(recipientNodeInfo)
        recipientRpc.waitForVisibility(issuerRpc.nodeInfo())

        val (_, update) = recipientRpc.vaultTrack(Cash.State::class.java)
        val cashArrived = update.waitForFirst { true }

        val (paymentTx) = issuerRpc.startFlow(
                ::CashPaymentFlow,
                10.DOLLARS,
                recipientNodeInfo.legalIdentities[0],
                false,
        ).returnValue.getOrThrow()

        cashArrived.getOrThrow()
        assertThat(recipientRpc.getCashBalance(USD) - beforeAmount).isEqualTo(10.DOLLARS)

        return Pair(issuanceTx, paymentTx)
    }
}

class ExternalVerificationUnsignedCordappsTest {
    private companion object {
        private val factory = NodeProcess.Factory(testNetworkParameters(minimumPlatformVersion = MIGRATE_ATTACHMENT_TO_SIGNATURE_CONSTRAINTS))

        private lateinit var notary: NodeProcess
        private lateinit var oldNode: NodeProcess
        private lateinit var newNode: NodeProcess

        @BeforeClass
        @JvmStatic
        fun startNodes() {
            // The 4.11 finance CorDapp jars
            val legacyCordapps = listOf(unsignedResourceJar("corda-finance-contracts-4.11.jar"), smokeTestResource("corda-finance-workflows-4.11.jar"))
            // The current version finance CorDapp jars
            val currentCordapps = listOf(unsignedResourceJar("corda-finance-contracts.jar"), smokeTestResource("corda-finance-workflows.jar"))

            notary = factory.createNotaries(nodeParams(DUMMY_NOTARY_NAME, currentCordapps))[0]
            oldNode = factory.createNode(nodeParams(
                    CordaX500Name("Old", "Delhi", "IN"),
                    legacyCordapps,
                    clientRpcConfig = CordaRPCClientConfiguration(minimumServerProtocolVersion = 13),
                    version = "4.11"
            ))
            newNode = factory.createNode(nodeParams(CordaX500Name("New", "York", "US"), currentCordapps))
        }

        @AfterClass
        @JvmStatic
        fun close() {
            factory.close()
        }

        private fun unsignedResourceJar(name: String): Path {
            val signedJar = smokeTestResource(name)
            val copy = signedJar.copyTo(Path("${signedJar.toString().substringBeforeLast(".")}-UNSIGNED.jar"), overwrite = true)
            copy.unsignJar()
            return copy
        }
    }

    @Test(timeout = 300_000)
    fun `transactions can fail verification in external verifier`() {
        val issuerRpc = oldNode.connect(superUser).proxy
        val recipientRpc = newNode.connect(superUser).proxy
        val recipientNodeInfo = recipientRpc.nodeInfo()
        val notaryIdentity = issuerRpc.notaryIdentities()[0]

        val (issuanceTx) = issuerRpc.startFlow(
                ::CashIssueFlow,
                10.DOLLARS,
                OpaqueBytes.of(0x01),
                notaryIdentity
        ).returnValue.getOrThrow()

        issuerRpc.waitForVisibility(recipientNodeInfo)
        recipientRpc.waitForVisibility(issuerRpc.nodeInfo())

        assertThatExceptionOfType(UnexpectedFlowEndException::class.java).isThrownBy {
            issuerRpc.startFlow<AbstractCashFlow.Result, Amount<Currency>, Party, Boolean, CashPaymentFlow>(
                    ::CashPaymentFlow,
                    10.DOLLARS,
                    recipientNodeInfo.legalIdentities[0],
                    false,
            ).returnValue.getOrThrow()
        }

        assertThat(newNode.externalVerifierLogs()).contains("$issuanceTx failed to verify")
    }
}

private val superUser = User("superUser", "test", permissions = setOf("ALL"))
private val portCounter = AtomicInteger(15100)

private fun smokeTestResource(name: String): Path = ExternalVerificationSignedCordappsTest::class.java.getResource("/$name")!!.toPath()

private fun nodeParams(
        legalName: CordaX500Name,
        cordappJars: List<Path> = emptyList(),
        legacyContractJars: List<Path> = emptyList(),
        clientRpcConfig: CordaRPCClientConfiguration = CordaRPCClientConfiguration.DEFAULT,
        version: String? = null
): NodeParams {
    return NodeParams(
            legalName = legalName,
            p2pPort = portCounter.andIncrement,
            rpcPort = portCounter.andIncrement,
            rpcAdminPort = portCounter.andIncrement,
            users = listOf(superUser),
            cordappJars = cordappJars,
            legacyContractJars = legacyContractJars,
            clientRpcConfig = clientRpcConfig,
            version = version
    )
}

private fun CordaRPCOps.waitForVisibility(other: NodeInfo) {
    val (snapshot, updates) = networkMapFeed()
    if (other in snapshot) {
        updates.notUsed()
    } else {
        updates.waitForFirst { it.node == other }.getOrThrow()
    }
}

private fun <T> Observable<T>.waitForFirst(predicate: (T) -> Boolean): CompletableFuture<Unit> {
    val found = CompletableFuture<Unit>()
    val subscription = subscribe {
        if (predicate(it)) {
            found.complete(Unit)
        }
    }
    return found.whenComplete { _, _ -> subscription.unsubscribe() }
}

private fun NodeProcess.assertTransactionsWereVerified(verificationType: VerificationType, vararg txIds: SecureHash) {
    val nodeLogs = logs("node")!!
    val externalVerifierLogs = externalVerifierLogs()
    for (txId in txIds) {
        assertThat(nodeLogs).contains("Transaction $txId has verification type $verificationType")
        if (verificationType != VerificationType.IN_PROCESS) {
            assertThat(externalVerifierLogs).describedAs("External verifier was not started").isNotNull()
            assertThat(externalVerifierLogs).contains("SignedTransaction(id=$txId) verified")
        }
    }
}

private fun NodeProcess.externalVerifierLogs(): String? = logs("verifier")

private fun NodeProcess.logs(name: String): String? {
    return (nodeDir / "logs")
            .listDirectoryEntries("$name-${InetAddress.getLocalHost().hostName}.log")
            .singleOrNull()
            ?.readText()
}

private enum class VerificationType {
    IN_PROCESS, EXTERNAL, BOTH
}