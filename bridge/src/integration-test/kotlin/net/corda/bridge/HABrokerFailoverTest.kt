package net.corda.bridge

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.internal.copyTo
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.core.internal.sumByLong
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.POUNDS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.node.services.config.EnterpriseConfiguration
import net.corda.node.services.config.MutualExclusionConfiguration
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.configureWithDevSSLCertificate
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.internal.rigorousMock
import net.corda.testing.internal.stubs.CertificateStoreStubs
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.User
import net.corda.testing.node.internal.FINANCE_CORDAPPS
import net.corda.testing.node.internal.ProcessUtilities
import net.corda.testing.node.internal.internalDriver
import org.apache.activemq.artemis.core.config.FileDeploymentManager
import org.apache.activemq.artemis.core.config.impl.FileConfiguration
import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration
import org.apache.activemq.artemis.core.server.ActiveMQServers
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager
import org.apache.activemq.artemis.spi.core.security.jaas.TextFileCertificateLoginModule
import org.apache.commons.io.FileUtils
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.lang.management.ManagementFactory
import java.nio.file.Path
import java.nio.file.Paths
import javax.security.auth.login.AppConfigurationEntry
import kotlin.concurrent.thread
import kotlin.test.assertEquals

@Ignore
// TODO: Investigate why node's artemis client can't failover to out of process slave artemis server.
class HABrokerFailoverTest : IntegrationTest() {
    companion object {
        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(ALICE_NAME, DUMMY_NOTARY_NAME)
    }

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()
    private val portAllocator = incrementalPortAllocation()

    private abstract class AbstractNodeConfiguration : NodeConfiguration

    @Test
    fun `node can still send and recieve message after out-of-process broker restart`() {
        val p2pPort = portAllocator.nextPort()
        val rootDir = tempFolder.root.toPath()
        val nodeBaseDir = rootDir / "alice"

        val artemisDir = rootDir / "artemis"
        val masterConfigPath = artemisDir / "master" / "broker.xml"
        val slaveConfigPath = artemisDir / "slave" / "broker.xml"

        masterConfigPath.parent.createDirectories()
        slaveConfigPath.parent.createDirectories()

        ClassLoader.getSystemResourceAsStream("artemis/artemis-roles.properties").copyTo(masterConfigPath.parent / "artemis-roles.properties")
        ClassLoader.getSystemResourceAsStream("artemis/artemis-roles.properties").copyTo(slaveConfigPath.parent / "artemis-roles.properties")
        ClassLoader.getSystemResourceAsStream("artemis/artemis-users.properties").copyTo(masterConfigPath.parent / "artemis-users.properties")
        ClassLoader.getSystemResourceAsStream("artemis/artemis-users.properties").copyTo(slaveConfigPath.parent / "artemis-users.properties")
        ClassLoader.getSystemResourceAsStream("artemis/master/broker.xml").copyTo(masterConfigPath)
        ClassLoader.getSystemResourceAsStream("artemis/slave/broker.xml").copyTo(slaveConfigPath)

        val certificateDir = masterConfigPath.parent / "certificates"
        val signingCertificateStore = CertificateStoreStubs.Signing.withCertificatesDirectory(certificateDir)
        val p2pSslOptions = CertificateStoreStubs.P2P.withCertificatesDirectory(certificateDir)
        val artemisConfig = rigorousMock<AbstractNodeConfiguration>().also {
            doReturn(masterConfigPath.parent).whenever(it).baseDirectory
            doReturn(certificateDir).whenever(it).certificatesDirectory
            doReturn(signingCertificateStore).whenever(it).signingCertificateStore
            doReturn(p2pSslOptions).whenever(it).p2pSslOptions
            doReturn(ALICE_NAME).whenever(it).myLegalName
            doReturn(NetworkHostAndPort("localhost", p2pPort)).whenever(it).p2pAddress
            doReturn(EnterpriseConfiguration(MutualExclusionConfiguration(false, "", 20000, 40000), externalBridge = false)).whenever(it)
                    .enterpriseConfiguration
            doReturn(null).whenever(it).jmxMonitoringHttpPort
        }
        artemisConfig.configureWithDevSSLCertificate()
        val nodeCertDir = nodeBaseDir / "certificates"
        val slaveCertDir = slaveConfigPath.parent / "certificates"

        nodeCertDir.createDirectories()
        slaveCertDir.createDirectories()

        FileUtils.copyDirectory(certificateDir.toFile(), nodeCertDir.toFile())
        FileUtils.copyDirectory(certificateDir.toFile(), slaveCertDir.toFile())

        val master = startOutOfProcessBroker(baseDir = masterConfigPath.parent)
        val slave = startOutOfProcessBroker(baseDir = slaveConfigPath.parent)

        println(rootDir)

        val brokerPort = portAllocator.nextPort()
        val altBrokerPort = portAllocator.nextPort()
        val nodeConfiguration = mapOf(
                "baseDirectory" to "$nodeBaseDir",
                "p2pAddress" to NetworkHostAndPort("localhost", p2pPort).toString(),
                "devMode" to false,
                "messagingServerExternal" to true,
                "messagingServerAddress" to NetworkHostAndPort("localhost", brokerPort).toString(),
                "enterpriseConfiguration" to mapOf(
                        "externalBridge" to true,
                        "messagingServerBackupAddresses" to listOf("localhost:$altBrokerPort"),
                        "messagingServerConnectionConfiguration" to "CONTINUOUS_RETRY",
                        "messagingServerSslConfiguration" to mapOf(
                                "sslKeystore" to "$nodeBaseDir/certificates/sslkeystore.jks",
                                "keyStorePassword" to "cordacadevpass",
                                "trustStoreFile" to "$nodeBaseDir/certificates/truststore.jks",
                                "trustStorePassword" to "trustpass"
                        )))

        internalDriver(notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME, validating = false)), portAllocation = portAllocator) {
            val aliceUser = User("alice", "alice", permissions = setOf("ALL"))

            val aBridgeFuture = startBridge(ALICE_NAME, p2pPort, brokerPort, mapOf(
                    "firewallMode" to "SenderReceiver",
                    "outboundConfig" to mapOf(
                            "artemisBrokerAddress" to "localhost:$brokerPort",
                            "alternateArtemisBrokerAddresses" to listOf("localhost:$altBrokerPort")
                    ),
                    "inboundConfig" to mapOf(
                            "listeningAddress" to "0.0.0.0:$p2pPort"
                    )
            ), nodeDirectory = nodeBaseDir)

            val bobNode = startNode(NodeParameters(providedName = BOB_NAME, rpcUsers = listOf(aliceUser), additionalCordapps = FINANCE_CORDAPPS)).getOrThrow()
            val aliceNode = startNode(NodeParameters(providedName = ALICE_NAME, rpcUsers = listOf(aliceUser), customOverrides = nodeConfiguration, additionalCordapps = FINANCE_CORDAPPS)).getOrThrow()

            aBridgeFuture.getOrThrow()

            println("*** Issue cash")
            aliceNode.rpc.startFlow(::CashIssueFlow, 1000.POUNDS, OpaqueBytes.of(0), defaultNotaryIdentity).returnValue.getOrThrow()
            println("*** Spend cash")
            val flow = aliceNode.rpc.startFlow(::CashPaymentFlow, 50.POUNDS, bobNode.nodeInfo.singleIdentity()).returnValue
            flow.getOrThrow()

            assertEquals(95000, aliceNode.rpc.vaultTotal())

            println("*** Spend cash")
            master.destroyForcibly().waitFor()
            Thread.sleep(10000)
            val flow2 = aliceNode.rpc.startFlow(::CashPaymentFlow, 50.POUNDS, bobNode.nodeInfo.singleIdentity()).returnValue
            flow2.getOrThrow()
        }

        master.destroyForcibly().waitFor()
        slave.destroyForcibly().waitFor()
    }

    private fun CordaRPCOps.vaultTotal() = vaultQuery(Cash.State::class.java).states.sumByLong { it.state.data.amount.quantity }

    private fun startOutOfProcessBroker(baseDir: Path): Process {
        return ProcessUtilities.startJavaProcess<OutOfProcessArtemisBroker>(workingDirectory = baseDir, arguments = listOf())
    }
}

object OutOfProcessArtemisBroker {
    @JvmStatic
    fun main(args: Array<String>) {
        val config = FileConfiguration()
        FileDeploymentManager(Paths.get("broker.xml").toUri().toString()).addDeployable(config).readConfiguration()
        val server = ActiveMQServers.newActiveMQServer(config, ManagementFactory.getPlatformMBeanServer(), createArtemisSecurityManager())
        Runtime.getRuntime().addShutdownHook(thread(start = false) {
            server.stop()
        })
        server.start()
    }

    private fun createArtemisSecurityManager(): ActiveMQJAASSecurityManager {
        val securityConfiguration = object : SecurityConfiguration() {
            override fun getAppConfigurationEntry(name: String): Array<AppConfigurationEntry> {
                val options = mapOf(
                        "org.apache.activemq.jaas.textfiledn.user" to "artemis-users.properties",
                        "org.apache.activemq.jaas.textfiledn.role" to "artemis-roles.properties"
                )

                return arrayOf(AppConfigurationEntry(name, AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, options))
            }
        }
        return ActiveMQJAASSecurityManager(TextFileCertificateLoginModule::class.java.name, securityConfiguration)
    }
}