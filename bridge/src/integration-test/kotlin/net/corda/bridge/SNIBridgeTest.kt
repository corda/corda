package net.corda.bridge

import co.paralleluniverse.fibers.Suspendable
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.internal.div
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.node.internal.artemis.BrokerJaasLoginModule
import net.corda.node.services.Permissions
import net.corda.node.services.config.EnterpriseConfiguration
import net.corda.node.services.config.MutualExclusionConfiguration
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.configureWithDevSSLCertificate
import net.corda.nodeapi.internal.ArtemisMessagingComponent
import net.corda.nodeapi.internal.ArtemisTcpTransport.Companion.p2pAcceptorTcpTransport
import net.corda.nodeapi.internal.config.CertificateStore
import net.corda.nodeapi.internal.config.MutualSslConfiguration
import net.corda.nodeapi.internal.crypto.loadOrCreateKeyStore
import net.corda.testing.core.*
import net.corda.testing.internal.rigorousMock
import net.corda.testing.internal.stubs.CertificateStoreStubs
import net.corda.testing.node.User
import net.corda.testing.node.internal.cordappsForPackages
import net.corda.testing.node.internal.internalDriver
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.core.config.Configuration
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl
import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration
import org.apache.activemq.artemis.core.security.Role
import org.apache.activemq.artemis.core.server.ActiveMQServer
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager
import org.apache.activemq.artemis.spi.core.security.jaas.TextFileCertificateLoginModule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import javax.security.auth.login.AppConfigurationEntry
import kotlin.test.assertEquals

class SNIBridgeTest {
    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    private abstract class AbstractNodeConfiguration : NodeConfiguration

    @StartableByRPC
    @InitiatingFlow
    class Ping(val pongParty: Party, val times: Int) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val pongSession = initiateFlow(pongParty)
            pongSession.sendAndReceive<Unit>(times)
            BridgeRestartTest.pingStarted.getOrPut(runId) { openFuture() }.set(Unit)
            for (i in 1..times) {
                logger.info("PING $i")
                val j = pongSession.sendAndReceive<Int>(i).unwrap { it }
                assertEquals(i, j)
            }
        }
    }

    @InitiatedBy(Ping::class)
    class Pong(val pingSession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val times = pingSession.sendAndReceive<Int>(Unit).unwrap { it }
            for (i in 1..times) {
                logger.info("PONG $i $pingSession")
                val j = pingSession.sendAndReceive<Int>(i).unwrap { it }
                assertEquals(i, j)
            }
        }
    }

    @Test
    fun `Nodes behind all in one bridge can communicate with external node`() {
        val demoUser = User("demo", "demo", setOf(Permissions.startFlow<BridgeRestartTest.Ping>(), Permissions.all()))
        internalDriver(startNodesInProcess = true, cordappsForAllNodes = cordappsForPackages("net.corda.bridge")) {
            val artemisPort = portAllocation.nextPort()
            val advertisedP2PPort = portAllocation.nextPort()
            // We create a config for ALICE_NAME so we can use the dir lookup from the Driver when starting the bridge
            val nodeConfigs = createNodesConfigs(listOf(DUMMY_BANK_A_NAME, DUMMY_BANK_B_NAME, ALICE_NAME))
            // Remove the created trust and key stores
            val bridgePath = temporaryFolder.root.path / ALICE_NAME.organisation
            Files.delete(bridgePath / "node/certificates/truststore.jks")
            Files.delete(bridgePath / "node/certificates/sslkeystore.jks")
            // TODO: change bridge driver to use any provided base dir, not just look for one based on identity
            createAggregateStores(nodeConfigs.minus(ALICE_NAME).values.toList(), baseDirectory(ALICE_NAME))

            val bankAPath = temporaryFolder.root.path / DUMMY_BANK_A_NAME.organisation / "node"
            val bankBPath = temporaryFolder.root.path / DUMMY_BANK_B_NAME.organisation / "node"
            // Start broker
            val broker = createArtemisTextCertsLogin(artemisPort, nodeConfigs[DUMMY_BANK_B_NAME]!!.p2pSslOptions)
            broker.start()
            println(broker.isActive)
            val aFuture = startNode(
                    providedName = DUMMY_BANK_A_NAME,
                    rpcUsers = listOf(demoUser),
                    customOverrides = mapOf(
                            "baseDirectory" to "$bankAPath",
                            "p2pAddress" to "localhost:$advertisedP2PPort",
                            "messagingServerAddress" to "0.0.0.0:$artemisPort",
                            "messagingServerExternal" to true,
                            "enterpriseConfiguration" to mapOf(
                                    "externalBridge" to true
                            )
                    )
            )

            val a = aFuture.getOrThrow()
            println(a.nodeInfo)

            val bFuture = startNode(
                    providedName = DUMMY_BANK_B_NAME,
                    rpcUsers = listOf(demoUser),
                    customOverrides = mapOf(
                            "baseDirectory" to "$bankBPath",
                            "p2pAddress" to "localhost:$advertisedP2PPort",
                            "messagingServerAddress" to "0.0.0.0:$artemisPort",
                            "messagingServerExternal" to true,
                            "enterpriseConfiguration" to mapOf(
                                    "externalBridge" to true
                            )
                    )
            )

            val b = bFuture.getOrThrow()
            println(b.nodeInfo)


            val bridge = startBridge(ALICE_NAME, advertisedP2PPort, artemisPort, mapOf(
                    "outboundConfig" to mapOf(
                            "artemisBrokerAddress" to "localhost:$artemisPort"
                    ),
                    "inboundConfig" to mapOf(
                            "listeningAddress" to "0.0.0.0:$advertisedP2PPort"
                    )
            )).getOrThrow()
            println(bridge.brokerPort)

            // Start a node on the other side of the bridge
            val c = startNode(providedName = DUMMY_BANK_C_NAME, rpcUsers = listOf(demoUser), customOverrides = mapOf("p2pAddress" to "localhost:${portAllocation.nextPort()}")).getOrThrow()


            // BANK_C initiates flows with BANK_A and BANK_B
            CordaRPCClient(c.rpcAddress).use(demoUser.username, demoUser.password) {
                var handle = it.proxy.startFlow(::Ping, a.nodeInfo.singleIdentity(), 5)
                handle.returnValue.getOrThrow()

                handle = it.proxy.startFlow(::Ping, b.nodeInfo.singleIdentity(), 5)
                handle.returnValue.getOrThrow()
            }


            CordaRPCClient(a.rpcAddress).use(demoUser.username, demoUser.password) {
                val handle = it.proxy.startFlow(::Ping, c.nodeInfo.singleIdentity(), 5)
                handle.returnValue.getOrThrow()
            }

            CordaRPCClient(b.rpcAddress).use(demoUser.username, demoUser.password) {
                val handle = it.proxy.startFlow(::Ping, c.nodeInfo.singleIdentity(), 5)
                handle.returnValue.getOrThrow()
            }
        }
    }

    private fun createNodesConfigs(legalNames: List<CordaX500Name>): Map<CordaX500Name, NodeConfiguration> {
        val tempFolders = legalNames.map { it to temporaryFolder.root.toPath() / it.organisation }.toMap()
        val baseDirectories = tempFolders.mapValues { it.value / "node" }
        val certificatesDirectories = baseDirectories.mapValues { it.value / "certificates" }
        val signingCertificateStores = certificatesDirectories.mapValues { CertificateStoreStubs.Signing.withCertificatesDirectory(it.value) }
        val pspSslConfigurations = certificatesDirectories.mapValues { CertificateStoreStubs.P2P.withCertificatesDirectory(it.value, useOpenSsl = false) }
        val nodeConfigs = legalNames.map { name ->
            val serverConfig = rigorousMock<AbstractNodeConfiguration>().also {
                doReturn(baseDirectories[name]).whenever(it).baseDirectory
                doReturn(certificatesDirectories[name]).whenever(it).certificatesDirectory
                doReturn(name).whenever(it).myLegalName
                doReturn(signingCertificateStores[name]).whenever(it).signingCertificateStore
                doReturn(pspSslConfigurations[name]).whenever(it).p2pSslOptions
                doReturn(true).whenever(it).crlCheckSoftFail
                doReturn(true).whenever(it).messagingServerExternal
                doReturn(EnterpriseConfiguration(MutualExclusionConfiguration(false, "", 20000, 40000), externalBridge = true)).whenever(it).enterpriseConfiguration
            }
            serverConfig.configureWithDevSSLCertificate()
            name to serverConfig
        }.toMap()

        return nodeConfigs
    }

    private fun createAggregateStores(nodeConfigs: List<NodeConfiguration>, bridgeDirPath: Path) {
        val trustStore = nodeConfigs.first().p2pSslOptions.trustStore.get(true)
        val newKeyStore = loadOrCreateKeyStore(bridgeDirPath / "certificates/sslkeystore.jks", "cordacadevpass")

        nodeConfigs.forEach {
            mergeKeyStores(newKeyStore, it.p2pSslOptions.keyStore.get(true), it.myLegalName.toString())
        }

        // Save to disk and copy in the bridge directory
        trustStore.writeTo(FileOutputStream(File("$bridgeDirPath/certificates/truststore.jks")))
        newKeyStore.store(FileOutputStream(File("$bridgeDirPath/certificates/sslkeystore.jks")), "cordacadevpass".toCharArray())
    }


    private fun mergeKeyStores(newKeyStore: KeyStore, oldKeyStore: CertificateStore, newAlias: String) {
        val keyStore = oldKeyStore.value.internal
        keyStore.aliases().toList().forEach {
            val key = keyStore.getKey(it, oldKeyStore.password.toCharArray())
            val certs = keyStore.getCertificateChain(it)
            newKeyStore.setKeyEntry(newAlias, key, oldKeyStore.password.toCharArray(), certs)
        }
    }


    private fun ConfigurationImpl.configureAddressSecurity(): Configuration {
        val nodeInternalRole = Role("Node", true, true, true, true, true, true, true, true, true, true)
        securityRoles["${ArtemisMessagingComponent.INTERNAL_PREFIX}#"] = setOf(nodeInternalRole)  // Do not add any other roles here as it's only for the node
        securityRoles["${ArtemisMessagingComponent.P2P_PREFIX}#"] = setOf(nodeInternalRole, restrictedRole(BrokerJaasLoginModule.PEER_ROLE, send = true))
        securityRoles["*"] = setOf(Role("guest", true, true, true, true, true, true, true, true, true, true))
        return this
    }

    private fun restrictedRole(name: String, send: Boolean = false, consume: Boolean = false, createDurableQueue: Boolean = false,
                               deleteDurableQueue: Boolean = false, createNonDurableQueue: Boolean = false,
                               deleteNonDurableQueue: Boolean = false, manage: Boolean = false, browse: Boolean = false): Role {
        return Role(name, send, consume, createDurableQueue, deleteDurableQueue, createNonDurableQueue,
                deleteNonDurableQueue, manage, browse, createDurableQueue || createNonDurableQueue, deleteDurableQueue || deleteNonDurableQueue)
    }

    private fun createArtemisTextCertsLogin(p2pPort: Int, p2pSslOptions: MutualSslConfiguration): ActiveMQServer {
        val artemisDir = temporaryFolder.root.path / "artemis"
        val config = ConfigurationImpl().apply {
            bindingsDirectory = (artemisDir / "bindings").toString()
            journalDirectory = (artemisDir / "journal").toString()
            largeMessagesDirectory = (artemisDir / "large-messages").toString()
            acceptorConfigurations = mutableSetOf(p2pAcceptorTcpTransport(NetworkHostAndPort("0.0.0.0", p2pPort), p2pSslOptions))
            idCacheSize = 2000 // Artemis Default duplicate cache size i.e. a guess
            isPersistIDCache = true
            isPopulateValidatedUser = true
            journalBufferSize_NIO = MAX_MESSAGE_SIZE + ArtemisMessagingComponent.JOURNAL_HEADER_SIZE // Artemis default is 490KiB - required to address IllegalArgumentException (when Artemis uses Java NIO): Record is too large to store.
            journalBufferSize_AIO = MAX_MESSAGE_SIZE + ArtemisMessagingComponent.JOURNAL_HEADER_SIZE // Required to address IllegalArgumentException (when Artemis uses Linux Async IO): Record is too large to store.
            journalFileSize = MAX_MESSAGE_SIZE + ArtemisMessagingComponent.JOURNAL_HEADER_SIZE// The size of each journal file in bytes. Artemis default is 10MiB.
            managementNotificationAddress = SimpleString(ArtemisMessagingComponent.NOTIFICATIONS_ADDRESS)
        }.configureAddressSecurity()

        val usersPropertiesFilePath = ConfigTest::class.java.getResource("/net/corda/bridge/artemis/artemis-users.properties").path
        val rolesPropertiesFilePath = ConfigTest::class.java.getResource("/net/corda/bridge/artemis/artemis-roles.properties").path
        val securityConfiguration = object : SecurityConfiguration() {
            override fun getAppConfigurationEntry(name: String): Array<AppConfigurationEntry> {
                val options = mapOf(
                        "org.apache.activemq.jaas.textfiledn.user" to usersPropertiesFilePath,
                        "org.apache.activemq.jaas.textfiledn.role" to rolesPropertiesFilePath
                )

                return arrayOf(AppConfigurationEntry(name, AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, options))
            }
        }
        val securityManager = ActiveMQJAASSecurityManager(TextFileCertificateLoginModule::class.java.name, securityConfiguration)
        return ActiveMQServerImpl(config, securityManager)
    }
}