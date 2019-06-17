package net.corda.bridge

import com.typesafe.config.ConfigFactory
import net.corda.bridge.services.config.CryptoServiceConfigImpl
import net.corda.bridge.services.config.CryptoServiceFactory
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.copyToDirectory
import net.corda.core.internal.div
import net.corda.core.internal.toPath
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.Permissions
import net.corda.nodeapi.internal.DEV_CA_KEY_STORE_PASS
import net.corda.nodeapi.internal.DEV_CA_TRUST_STORE_PASS
import net.corda.nodeapi.internal.config.CertificateStore
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.cryptoservice.SupportedCryptoServices
import net.corda.nodeapi.internal.hsm.HsmSimulator
import net.corda.testing.core.*
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.node.User
import net.corda.testing.node.internal.cordappsForPackages
import net.corda.testing.node.internal.internalDriver
import org.apache.activemq.artemis.core.server.ActiveMQServer
import org.apache.commons.io.FileUtils
import org.junit.Rule
import org.junit.Test
import sun.security.x509.X500Name
import java.nio.charset.Charset
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import javax.security.auth.x500.X500Principal

const val ARTEMIS_HSM_KEYSTORE = "artemishsm.jks"  // Store only the public key for Bridge Artemis link

class BridgeHSMTest : IntegrationTest() {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)

    private val portAllocation = incrementalPortAllocation(13400)

    @Rule
    @JvmField
    val nodeAHSM = HsmSimulator(portAllocation)
    @Rule
    @JvmField
    val nodeBHSM = HsmSimulator(portAllocation)
    @Rule
    @JvmField
    val bridgeHSM = HsmSimulator(portAllocation)
    @Rule
    @JvmField
    val bridgeArtemisHSM = HsmSimulator(portAllocation)

    @Test
    fun `Nodes behind all in one bridge can communicate with external node when using HSM`() {
        val demoUser = User("demo", "demo", setOf(Permissions.startFlow<Ping>(), Permissions.all()))
        var artemis: ActiveMQServer? = null

        internalDriver(startNodesInProcess = true, cordappsForAllNodes = cordappsForPackages("net.corda.bridge"), notarySpecs = emptyList(), portAllocation = portAllocation) {
            val artemisPort = portAllocation.nextPort()
            val advertisedP2PPort = portAllocation.nextPort()
            val floatPort = portAllocation.nextPort()

            val bankAPath = driverDirectory / DUMMY_BANK_A_NAME.organisation / "node"
            val bankBPath = driverDirectory / DUMMY_BANK_B_NAME.organisation / "node"
            val bankCPath = driverDirectory / DUMMY_BANK_C_NAME.organisation / "node"
            val bridgePath = driverDirectory / "bridge"

            val nodeAUtimacoConfig = createTempUtimacoConfig(bankAPath, nodeAHSM.address)
            val nodeAcryptoService = CryptoServiceFactory.get(CryptoServiceConfigImpl(SupportedCryptoServices.UTIMACO, Paths.get(nodeAUtimacoConfig)))
            val nodeBUtimacoConfig = createTempUtimacoConfig(bankBPath, nodeBHSM.address)
            val nodeBCryptoService = CryptoServiceFactory.get(CryptoServiceConfigImpl(SupportedCryptoServices.UTIMACO, Paths.get(nodeBUtimacoConfig)))
            val bridgeUtimacoConfig = createTempUtimacoConfig(bridgePath, bridgeHSM.address)
            val bridgeCryptoService = CryptoServiceFactory.get(CryptoServiceConfigImpl(SupportedCryptoServices.UTIMACO, Paths.get(bridgeUtimacoConfig)))
            val bridgeArtemisUtimacoConfig = createTempUtimacoConfig(bridgePath, bridgeArtemisHSM.address)
            val bridgeArtemisCryptoService = CryptoServiceFactory.get(CryptoServiceConfigImpl(SupportedCryptoServices.UTIMACO, Paths.get(bridgeArtemisUtimacoConfig)))


            // Create node's certificates without starting up the nodes.
            createNodeDevCertificates(DUMMY_BANK_A_NAME, bankAPath, cryptoService = nodeAcryptoService)
            createNodeDevCertificates(DUMMY_BANK_B_NAME, bankBPath, cryptoService = nodeBCryptoService)
            createNodeDevCertificates(DUMMY_BANK_C_NAME, bankCPath)

            // create bridge SSL keys
            val bridgeCertPath = bridgePath / "certificates"
            val bridgeKeystore = CertificateStore.fromFile(bridgeCertPath / "bridge.jks", DEV_CA_KEY_STORE_PASS, DEV_CA_KEY_STORE_PASS, true)

            mapOf(bankAPath to nodeAcryptoService, bankBPath to nodeBCryptoService).forEach { path, cryptoService ->
                val nodeKeystore = X509KeyStore.fromFile(path / "certificates" / "nodekeystore.jks", DEV_CA_KEY_STORE_PASS)
                val caCertChain = nodeKeystore.getCertificateChain(X509Utilities.CORDA_CLIENT_CA)
                val caCert = caCertChain.first()

                val alias = getTLSKeyAlias(caCert.subjectX500Principal)
                println(alias)
                val bankTLSKey = bridgeCryptoService.generateKeyPair(alias, X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
                val tlsCert = X509Utilities.createCertificate(CertificateType.TLS,
                        caCert.subjectX500Principal,
                        caCert.publicKey,
                        cryptoService.getSigner(X509Utilities.CORDA_CLIENT_CA),
                        caCert.subjectX500Principal,
                        bankTLSKey,
                        X509Utilities.getCertificateValidityWindow(
                                X509Utilities.DEFAULT_VALIDITY_WINDOW.first,
                                X509Utilities.DEFAULT_VALIDITY_WINDOW.second,
                                caCert))

                val certChain = listOf(tlsCert) + caCertChain
                bridgeKeystore.setCertPathOnly(alias, certChain)
                require(bridgeCryptoService.containsKey(alias))
                requireNotNull(bridgeCryptoService.getPublicKey(alias))
            }

            // Copy truststore
            (bankAPath / "certificates" / "truststore.jks").copyToDirectory(bridgeCertPath)

            // create bridge artemis SSL key
            artemis = createArtemis(driverDirectory, artemisPort)
            val artemisCertDir = driverDirectory / "artemis"

            val rootCertStore = CertificateStore.fromFile(artemisCertDir / "artemis-root.jks", DEV_CA_KEY_STORE_PASS, DEV_CA_KEY_STORE_PASS, false)
            val root = rootCertStore.value.getCertificateAndKeyPair(X509Utilities.CORDA_ROOT_CA, DEV_CA_KEY_STORE_PASS)

            val publicKey = bridgeArtemisCryptoService.generateKeyPair(X509Utilities.CORDA_CLIENT_TLS, X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
            val cert = X509Utilities.createCertificate(CertificateType.TLS, root.certificate, root.keyPair, "CN=artemis,O=Corda,L=London,C=GB".let { X500Name(it).asX500Principal()}, publicKey)
            val artemisKeyStore = CertificateStore.fromFile(artemisCertDir / ARTEMIS_HSM_KEYSTORE, DEV_CA_KEY_STORE_PASS, DEV_CA_KEY_STORE_PASS, createNew = true)
            artemisKeyStore.setCertPathOnly(X509Utilities.CORDA_CLIENT_TLS, listOf(cert, root.certificate))
            artemisKeyStore.value.save()

            require(bridgeArtemisCryptoService.containsKey(X509Utilities.CORDA_CLIENT_TLS))
            requireNotNull(bridgeArtemisCryptoService.getPublicKey(X509Utilities.CORDA_CLIENT_TLS))

            // Start broker

            artemis!!.start()

            val artemisSSLConfig = mapOf(
                    "sslKeystore" to (artemisCertDir / ARTEMIS_KEYSTORE).toString(),
                    "keyStorePassword" to DEV_CA_KEY_STORE_PASS,
                    "trustStoreFile" to (artemisCertDir / ARTEMIS_TRUSTSTORE).toString(),
                    "trustStorePassword" to DEV_CA_TRUST_STORE_PASS
            )

            val aFuture = startNode(
                    providedName = DUMMY_BANK_A_NAME,
                    rpcUsers = listOf(demoUser),
                    customOverrides = mapOf(
                            "baseDirectory" to "$bankAPath",
                            "p2pAddress" to "localhost:$advertisedP2PPort",
                            "messagingServerAddress" to "0.0.0.0:$artemisPort",
                            "messagingServerExternal" to true,
                            "enterpriseConfiguration" to mapOf(
                                    "externalBridge" to true,
                                    "messagingServerSslConfiguration" to artemisSSLConfig
                            ),
                            "cryptoServiceName" to "UTIMACO",
                            "cryptoServiceConf" to nodeAUtimacoConfig
                    )
            )

            val a = aFuture.getOrThrow()

            val bFuture = startNode(
                    providedName = DUMMY_BANK_B_NAME,
                    rpcUsers = listOf(demoUser),
                    customOverrides = mapOf(
                            "baseDirectory" to "$bankBPath",
                            "p2pAddress" to "localhost:$advertisedP2PPort",
                            "messagingServerAddress" to "0.0.0.0:$artemisPort",
                            "messagingServerExternal" to true,
                            "enterpriseConfiguration" to mapOf(
                                    "externalBridge" to true,
                                    "messagingServerSslConfiguration" to artemisSSLConfig
                            ),
                            "cryptoServiceName" to "UTIMACO",
                            "cryptoServiceConf" to nodeBUtimacoConfig
                    )
            )

            val b = bFuture.getOrThrow()
            val outboundConfig = createBridgeOutboundConfig(artemisCertDir.toString(), artemisPort)

            startBridge(driverDirectory,
                    artemisPort,
                    advertisedP2PPort,
                    floatPort = floatPort,
                    configOverrides = mapOf("publicCryptoServiceConfig" to mapOf("name" to "UTIMACO", "conf" to bridgeUtimacoConfig),
                                            "artemisCryptoServiceConfig" to mapOf("name" to "UTIMACO", "conf" to bridgeArtemisUtimacoConfig),
                                            "outboundConfig" to outboundConfig)).getOrThrow()

            // Start a node on the other side of the bridge
            val c = startNode(providedName = DUMMY_BANK_C_NAME, rpcUsers = listOf(demoUser), customOverrides = mapOf("p2pAddress" to "localhost:${portAllocation.nextPort()}", "baseDirectory" to "$bankCPath")).getOrThrow()

            // BANK_C initiates flows with BANK_A and BANK_B
            CordaRPCClient(c.rpcAddress).use(demoUser.username, demoUser.password) {
                it.proxy.startFlow(::Ping, a.nodeInfo.singleIdentity(), 5).returnValue.getOrThrow()

                it.proxy.startFlow(::Ping, b.nodeInfo.singleIdentity(), 5).returnValue.getOrThrow()

            }

            CordaRPCClient(a.rpcAddress).use(demoUser.username, demoUser.password) {
                it.proxy.startFlow(::Ping, c.nodeInfo.singleIdentity(), 5).returnValue.getOrThrow()
            }

            CordaRPCClient(b.rpcAddress).use(demoUser.username, demoUser.password) {
                it.proxy.startFlow(::Ping, c.nodeInfo.singleIdentity(), 5).returnValue.getOrThrow()
            }
        }

        artemis?.stop(false, true)
    }

    private fun createBridgeOutboundConfig(artemisCertDir: String, brokerPort: Int): Map<String, Any>{
        val sslConfig = mapOf(
                "keyStorePassword" to DEV_CA_KEY_STORE_PASS,
                "trustStorePassword" to DEV_CA_TRUST_STORE_PASS,
                "sslKeystore" to (artemisCertDir / ARTEMIS_HSM_KEYSTORE).toString(),
                "trustStoreFile" to (artemisCertDir / ARTEMIS_TRUSTSTORE).toString(),
                "revocationConfig" to mapOf("mode" to "OFF")
        )

        val outboundConfig = mapOf(
                "artemisBrokerAddress" to "localhost:$brokerPort",
                "artemisSSLConfiguration" to sslConfig
        )
        return outboundConfig
    }

    private fun createTempUtimacoConfig(configFolder: Path, hsmAddress: NetworkHostAndPort): String {
        val utimacoConfig = ConfigFactory.parseFile(javaClass.getResource("/utimaco_config.yml").toPath().toFile())
        val portConfig = ConfigFactory.parseMap(mapOf("port" to hsmAddress.port))
        val config = portConfig.withFallback(utimacoConfig)
        val uuid = UUID.randomUUID().toString()
        val tmpConfigFile = (configFolder / (uuid + "utimaco_config.yml")).toFile()
        FileUtils.writeStringToFile(tmpConfigFile, config.root().render(), Charset.defaultCharset())
        return tmpConfigFile.absolutePath
    }

    private fun getTLSKeyAlias(x500Principal: X500Principal): String {
        val nameHash = SecureHash.sha256(x500Principal.toString()).toString()
        // Must also be lower case to prevent problems with .JKS aliases.
        return "${X509Utilities.CORDA_CLIENT_TLS}-$nameHash".toLowerCase()
    }
}