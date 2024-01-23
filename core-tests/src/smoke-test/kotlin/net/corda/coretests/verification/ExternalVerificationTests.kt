package net.corda.coretests.verification

import co.paralleluniverse.strands.concurrent.CountDownLatch
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
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.flows.AbstractCashFlow
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
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
import java.net.InetAddress
import java.nio.file.Path
import java.util.Currency
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.Path
import kotlin.io.path.copyTo
import kotlin.io.path.div
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText

class ExternalVerificationSignedCordappsTest {
    private companion object {
        private val factory = NodeProcess.Factory(testNetworkParameters(minimumPlatformVersion = MIGRATE_ATTACHMENT_TO_SIGNATURE_CONSTRAINTS))

        private lateinit var notaries: List<NodeProcess>
        private lateinit var oldNode: NodeProcess
        private lateinit var newNode: NodeProcess

        @BeforeClass
        @JvmStatic
        fun startNodes() {
            // The 4.11 finance CorDapp jars
            val oldCordapps = listOf("contracts", "workflows").map { smokeTestResource("corda-finance-$it-4.11.jar") }
            // The current version finance CorDapp jars
            val newCordapps = listOf("contracts", "workflows").map { smokeTestResource("corda-finance-$it.jar") }

            notaries = factory.createNotaries(
                    nodeParams(DUMMY_NOTARY_NAME, oldCordapps),
                    nodeParams(CordaX500Name("Notary Service 2", "Zurich", "CH"), newCordapps)
            )
            oldNode = factory.createNode(nodeParams(
                    CordaX500Name("Old", "Delhi", "IN"),
                    oldCordapps + listOf(smokeTestResource("4.11-workflows-cordapp.jar")),
                    CordaRPCClientConfiguration(minimumServerProtocolVersion = 13),
                    version = "4.11"
            ))
            newNode = factory.createNode(nodeParams(CordaX500Name("New", "York", "US"), newCordapps))
        }

        @AfterClass
        @JvmStatic
        fun close() {
            factory.close()
        }
    }

    @Test(timeout=300_000)
    fun `transaction containing 4_11 contract sent to new node`() {
        assertCashIssuanceAndPayment(issuer = oldNode, recipient = newNode)
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

    private fun assertCashIssuanceAndPayment(issuer: NodeProcess, recipient: NodeProcess) {
        val issuerRpc = issuer.connect(superUser).proxy
        val recipientRpc = recipient.connect(superUser).proxy
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

        val (paymentTx) = issuerRpc.startFlow(
                ::CashPaymentFlow,
                10.DOLLARS,
                recipientNodeInfo.legalIdentities[0],
                false,
        ).returnValue.getOrThrow()

        notaries[0].assertTransactionsWereVerifiedExternally(issuanceTx.id, paymentTx.id)
        recipient.assertTransactionsWereVerifiedExternally(issuanceTx.id, paymentTx.id)
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
            val oldCordapps = listOf(unsignedResourceJar("corda-finance-contracts-4.11.jar"), smokeTestResource("corda-finance-workflows-4.11.jar"))
            // The current version finance CorDapp jars
            val newCordapps = listOf(unsignedResourceJar("corda-finance-contracts.jar"), smokeTestResource("corda-finance-workflows.jar"))

            notary = factory.createNotaries(nodeParams(DUMMY_NOTARY_NAME, oldCordapps))[0]
            oldNode = factory.createNode(nodeParams(
                    CordaX500Name("Old", "Delhi", "IN"),
                    oldCordapps,
                    CordaRPCClientConfiguration(minimumServerProtocolVersion = 13),
                    version = "4.11"
            ))
            newNode = factory.createNode(nodeParams(CordaX500Name("New", "York", "US"), newCordapps))
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
            clientRpcConfig = clientRpcConfig,
            version = version
    )
}

private fun CordaRPCOps.waitForVisibility(other: NodeInfo) {
    val (snapshot, updates) = networkMapFeed()
    if (other in snapshot) {
        updates.notUsed()
    } else {
        val found = CountDownLatch(1)
        val subscription = updates.subscribe {
            if (it.node == other) {
                found.countDown()
            }
        }
        found.await()
        subscription.unsubscribe()
    }
}

private fun NodeProcess.assertTransactionsWereVerifiedExternally(vararg txIds: SecureHash) {
    val verifierLogContent = externalVerifierLogs()
    for (txId in txIds) {
        assertThat(verifierLogContent).contains("SignedTransaction(id=$txId) verified")
    }
}

private fun NodeProcess.externalVerifierLogs(): String {
    val verifierLogs = (nodeDir / "logs")
            .listDirectoryEntries()
            .filter { it.name == "verifier-${InetAddress.getLocalHost().hostName}.log" }
    assertThat(verifierLogs).describedAs("External verifier was not started").hasSize(1)
    return verifierLogs[0].readText()
}
