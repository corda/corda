package net.corda.node.services

import com.google.common.net.HostAndPort
import net.corda.core.contracts.DummyContract
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionType
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Party
import net.corda.core.crypto.composite
import net.corda.core.crypto.generateKeyPair
import net.corda.core.getOrThrow
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.node.services.ServiceInfo
import net.corda.core.random63BitValue
import net.corda.core.serialization.serialize
import net.corda.core.utilities.LogHelper
import net.corda.flows.NotaryError
import net.corda.flows.NotaryException
import net.corda.flows.NotaryFlow
import net.corda.node.internal.AbstractNode
import net.corda.node.internal.Node
import net.corda.node.services.config.ConfigHelper
import net.corda.node.services.config.FullNodeConfiguration
import net.corda.node.services.network.NetworkMapService
import net.corda.node.services.transactions.RaftValidatingNotaryService
import net.corda.node.utilities.databaseTransaction
import net.corda.testing.freeLocalHostAndPort
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.KeyPair
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// TODO: clean up and rewrite this using DriverDSL
class DistributedNotaryTests {
    private val folderName = DateTimeFormatter
            .ofPattern("yyyyMMddHHmmss")
            .withZone(ZoneOffset.UTC)
            .format(Instant.now())
    val baseDir = "build/notaryTest/$folderName"
    val notaryName = "Notary Service"
    val clusterSize = 3

    @Before
    fun setup() {
        LogHelper.setLevel("-org.apache.activemq")
        LogHelper.setLevel(NetworkMapService::class)
        File(baseDir).deleteRecursively()
        File(baseDir).mkdirs()
    }

    @After
    fun tearDown() {
        LogHelper.reset("org.apache.activemq")
        LogHelper.reset(NetworkMapService::class)
        File(baseDir).deleteRecursively()
    }

    @Test
    fun `should detect double spend`() {
        val masterNode = createNotaryCluster()
        val alice = createAliceNode(masterNode.net.myAddress)

        val notaryParty = alice.netMapCache.getAnyNotary(RaftValidatingNotaryService.type)!!

        val stx = run {
            val notaryNodeKeyPair = databaseTransaction(masterNode.database) { masterNode.services.notaryIdentityKey }
            val inputState = issueState(alice, notaryParty, notaryNodeKeyPair)
            val tx = TransactionType.General.Builder(notaryParty).withItems(inputState)
            val aliceKey = databaseTransaction(alice.database) { alice.services.legalIdentityKey }
            tx.signWith(aliceKey)
            tx.toSignedTransaction(false)
        }

        val buildFlow = { NotaryFlow.Client(stx) }

        val firstSpend = alice.services.startFlow(buildFlow())
        firstSpend.resultFuture.getOrThrow()

        val secondSpend = alice.services.startFlow(buildFlow())

        val ex = assertFailsWith(NotaryException::class) { secondSpend.resultFuture.getOrThrow() }
        val error = ex.error as NotaryError.Conflict
        assertEquals(error.tx, stx.tx)
    }

    private fun createNotaryCluster(): Node {
        val notaryClusterAddress = freeLocalHostAndPort()
        val keyPairs = (1..clusterSize).map { generateKeyPair() }

        val notaryKeyTree = CompositeKey.Builder().addKeys(keyPairs.map { it.public.composite }).build(1)
        val notaryParty = Party(notaryName, notaryKeyTree).serialize()

        var networkMapAddress: SingleMessageRecipient? = null

        val cluster = keyPairs.mapIndexed { i, keyPair ->
            val dir = Paths.get(baseDir, "notaryNode$i")
            Files.createDirectories(dir)

            val privateKeyFile = RaftValidatingNotaryService.type.id + "-private-key"
            val publicKeyFile = RaftValidatingNotaryService.type.id + "-public"

            notaryParty.writeToFile(dir.resolve(publicKeyFile))
            keyPair.serialize().writeToFile(dir.resolve(privateKeyFile))

            val node: Node
            if (networkMapAddress == null) {
                val config = generateConfig(dir, "node" + random63BitValue(), notaryClusterAddress)
                node = createNotaryNode(config)
                networkMapAddress = node.net.myAddress
            } else {
                val config = generateConfig(dir, "node" + random63BitValue(), freeLocalHostAndPort(), notaryClusterAddress)
                node = createNotaryNode(config, networkMapAddress)
            }

            node
        }

        return cluster.first()
    }

    private fun createNotaryNode(config: FullNodeConfiguration, networkMapAddress: SingleMessageRecipient? = null): Node {
        val extraAdvertisedServices = if (networkMapAddress == null) setOf(ServiceInfo(NetworkMapService.type, "NMS")) else emptySet<ServiceInfo>()

        val notaryNode = Node(
                configuration = config,
                advertisedServices = extraAdvertisedServices + ServiceInfo(RaftValidatingNotaryService.type, notaryName),
                networkMapAddress = networkMapAddress)

        notaryNode.setup().start()
        thread { notaryNode.run() }
        notaryNode.networkMapRegistrationFuture.getOrThrow()
        return notaryNode
    }

    private fun createAliceNode(networkMapAddress: SingleMessageRecipient): Node {
        val aliceDir = Paths.get(baseDir, "alice")
        val alice = Node(
                configuration = generateConfig(aliceDir, "Alice"),
                advertisedServices = setOf(),
                networkMapAddress = networkMapAddress)
        alice.setup().start()
        thread { alice.run() }
        alice.networkMapRegistrationFuture.getOrThrow()
        return alice
    }

    private fun issueState(node: AbstractNode, notary: Party, notaryKey: KeyPair): StateAndRef<*> {
        return databaseTransaction(node.database) {
            val tx = DummyContract.generateInitial(node.info.legalIdentity.ref(0), Random().nextInt(), notary)
            tx.signWith(node.services.legalIdentityKey)
            tx.signWith(notaryKey)
            val stx = tx.toSignedTransaction()
            node.services.recordTransactions(listOf(stx))
            StateAndRef(tx.outputStates().first(), StateRef(stx.id, 0))
        }
    }

    private fun generateConfig(dir: Path, name: String, notaryNodeAddress: HostAndPort? = null, notaryClusterAddress: HostAndPort? = null) = FullNodeConfiguration(
            ConfigHelper.loadConfig(dir,
                    allowMissingConfig = true,
                    configOverrides = mapOf(
                            "myLegalName" to name,
                            "basedir" to dir,
                            "artemisAddress" to freeLocalHostAndPort().toString(),
                            "webAddress" to freeLocalHostAndPort().toString(),
                            "notaryNodeAddress" to notaryNodeAddress?.toString(),
                            "notaryClusterAddresses" to (if (notaryClusterAddress == null) emptyList<String>() else listOf(notaryClusterAddress.toString()))
                    )))
}