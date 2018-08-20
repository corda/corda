package net.corda.core.cordapp

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.Crypto.generateKeyPair
import net.corda.core.crypto.sign
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.*
import net.corda.core.messaging.startFlow
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.serialize
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.nodeapi.internal.DEV_INTERMEDIATE_CA
import net.corda.nodeapi.internal.DEV_ROOT_CA
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.config.User
import net.corda.nodeapi.internal.createDevNodeCa
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.smoketesting.NodeConfig
import net.corda.smoketesting.NodeProcess
import net.corda.smoketesting.NodeProcess.Companion.CORDAPPS_DIR_NAME
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.util.concurrent.atomic.AtomicInteger
import kotlin.streams.toList

class CordappSmokeTest {
    private companion object {
        val user = User("user1", "test", permissions = setOf("ALL"))
        val port = AtomicInteger(15100)
    }

    private val factory = NodeProcess.Factory()

    private val aliceConfig = NodeConfig(
            legalName = CordaX500Name(organisation = "Alice Corp", locality = "Madrid", country = "ES"),
            p2pPort = port.andIncrement,
            rpcPort = port.andIncrement,
            rpcAdminPort = port.andIncrement,
            isNotary = false,
            users = listOf(user)
    )

    @Test
    fun `FlowContent appName returns the filename of the CorDapp jar`() {
        val baseDir = factory.baseDirectory(aliceConfig)
        val cordappsDir = (baseDir / CORDAPPS_DIR_NAME).createDirectories()
        // Find the jar file for the smoke tests of this module
        val selfCordapp = Paths.get("build", "libs").list {
            it.filter { "-smokeTests" in it.toString() }.toList().single()
        }
        selfCordapp.copyToDirectory(cordappsDir)

        // The `nodeReadyFuture` in the persistent network map cach will not complete unless there is at least one other
        // node in the network. We work around this limitation by putting another node info file in the additional-node-info
        // folder.
        val additionalNodeInfoDir = (baseDir / "additional-node-infos").createDirectories()
        createDummyNodeInfo(additionalNodeInfoDir)

        factory.create(aliceConfig).use { alice ->
            alice.connect().use { connectionToAlice ->
                val aliceIdentity = connectionToAlice.proxy.nodeInfo().legalIdentitiesAndCerts.first().party
                val future = connectionToAlice.proxy.startFlow(::GatherContextsFlow, aliceIdentity).returnValue
                val (sessionInitContext, sessionConfirmContext) = future.getOrThrow()
                val selfCordappName = selfCordapp.fileName.toString().removeSuffix(".jar")
                assertThat(sessionInitContext.appName).isEqualTo(selfCordappName)
                assertThat(sessionConfirmContext.appName).isEqualTo(selfCordappName)
            }
        }
    }

    @Test
    fun `empty cordapps directory`() {
        (factory.baseDirectory(aliceConfig) / CORDAPPS_DIR_NAME).createDirectories()
        factory.create(aliceConfig).close()
    }

    @InitiatingFlow
    @StartableByRPC
    class GatherContextsFlow(private val otherParty: Party) : FlowLogic<Pair<FlowInfo, FlowInfo>>() {
        @Suspendable
        override fun call(): Pair<FlowInfo, FlowInfo> {
            // This receive will kick off SendBackInitiatorFlowContext by sending a session-init with our app name.
            // SendBackInitiatorFlowContext will send back our context using the information from this session-init
            val session = initiateFlow(otherParty)
            val sessionInitContext = session.receive<FlowInfo>().unwrap { it }
            // This context is taken from the session-confirm message
            val sessionConfirmContext = session.getCounterpartyFlowInfo()
            return Pair(sessionInitContext, sessionConfirmContext)
        }
    }

    @Suppress("unused")
    @InitiatedBy(GatherContextsFlow::class)
    class SendBackInitiatorFlowContext(private val otherPartySession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            // An initiated flow calling getFlowContext on its initiator will get the context from the session-init
            val sessionInitContext = otherPartySession.getCounterpartyFlowInfo()
            otherPartySession.send(sessionInitContext)
        }
    }

    private fun createDummyNodeInfo(additionalNodeInfoDir: Path) {
        val dummyKeyPair = generateKeyPair()
        val nodeInfo = createNodeInfoWithSingleIdentity(CordaX500Name(organisation = "Bob Corp", locality = "Madrid", country = "ES"), dummyKeyPair, dummyKeyPair.public)
        val signedNodeInfo = signWith(nodeInfo, listOf(dummyKeyPair.private))
        (additionalNodeInfoDir / "nodeInfo-41408E093F95EAD51F6892C34DEB65AE1A3569A4B0E5744769A1B485AF8E04B5").write(signedNodeInfo.serialize().bytes)
    }

    private fun createNodeInfoWithSingleIdentity(name: CordaX500Name, nodeKeyPair: KeyPair, identityCertPublicKey: PublicKey): NodeInfo {
        val nodeCertificateAndKeyPair = createDevNodeCa(DEV_INTERMEDIATE_CA, name, nodeKeyPair)
        val identityCert = X509Utilities.createCertificate(
                CertificateType.LEGAL_IDENTITY,
                nodeCertificateAndKeyPair.certificate,
                nodeCertificateAndKeyPair.keyPair,
                nodeCertificateAndKeyPair.certificate.subjectX500Principal,
                identityCertPublicKey)
        val certPath = X509Utilities.buildCertPath(
                identityCert,
                nodeCertificateAndKeyPair.certificate,
                DEV_INTERMEDIATE_CA.certificate,
                DEV_ROOT_CA.certificate)
        val partyAndCertificate = PartyAndCertificate(certPath)
        return NodeInfo(
                listOf(NetworkHostAndPort("my.${partyAndCertificate.party.name.organisation}.com", 1234)),
                listOf(partyAndCertificate),
                1,
                1
        )
    }

    private fun signWith(nodeInfo: NodeInfo, keys: List<PrivateKey>): SignedNodeInfo {
        val serialized = nodeInfo.serialize()
        val signatures = keys.map { it.sign(serialized.bytes) }
        return SignedNodeInfo(serialized, signatures)
    }
}
