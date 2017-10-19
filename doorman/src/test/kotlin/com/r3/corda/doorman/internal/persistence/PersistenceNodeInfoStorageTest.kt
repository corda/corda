package com.r3.corda.doorman.internal.persistence

import com.r3.corda.doorman.buildCertPath
import com.r3.corda.doorman.hash
import com.r3.corda.doorman.persistence.*
import com.r3.corda.doorman.toX509Certificate
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.sha256
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.serialize
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.serialization.KryoServerSerializationScheme
import net.corda.node.utilities.CertificateType
import net.corda.node.utilities.CordaPersistence
import net.corda.node.utilities.X509Utilities
import net.corda.node.utilities.configureDatabase
import net.corda.nodeapi.internal.serialization.*
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import net.corda.testing.node.MockServices.Companion.makeTestDatabaseProperties
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PersistenceNodeInfoStorageTest {
    private lateinit var nodeInfoStorage: NodeInfoStorage
    private lateinit var requestStorage: CertificationRequestStorage
    private lateinit var persistence: CordaPersistence
    private val rootCAKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
    private val rootCACert = X509Utilities.createSelfSignedCACertificate(CordaX500Name(commonName = "Corda Node Root CA", locality = "London", organisation = "R3 LTD", country = "GB"), rootCAKey)
    private val intermediateCAKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
    private val intermediateCACert = X509Utilities.createCertificate(CertificateType.INTERMEDIATE_CA, rootCACert, rootCAKey, CordaX500Name(commonName = "Corda Node Intermediate CA", locality = "London", organisation = "R3 LTD", country = "GB"), intermediateCAKey.public)

    companion object {
        @BeforeClass
        @JvmStatic
        fun initSerialization() {
            try {
                SerializationDefaults.SERIALIZATION_FACTORY = SerializationFactoryImpl().apply {
                    registerScheme(KryoServerSerializationScheme())
                    registerScheme(AMQPServerSerializationScheme())
                }
                SerializationDefaults.P2P_CONTEXT = KRYO_P2P_CONTEXT
                SerializationDefaults.RPC_SERVER_CONTEXT = KRYO_RPC_SERVER_CONTEXT
                SerializationDefaults.STORAGE_CONTEXT = KRYO_STORAGE_CONTEXT
                SerializationDefaults.CHECKPOINT_CONTEXT = KRYO_CHECKPOINT_CONTEXT
            } catch (ignored: Exception) {
                // Ignored
            }
        }
    }

    @Before
    fun startDb() {
        persistence = configureDatabase(makeTestDataSourceProperties(), makeTestDatabaseProperties(), { throw UnsupportedOperationException() }, DoormanSchemaService())
        nodeInfoStorage = PersistenceNodeInfoStorage(persistence)
        requestStorage = DBCertificateRequestStorage(persistence)
    }

    @After
    fun closeDb() {
        persistence.close()
    }

    @Test
    fun `test get CertificatePath`() {
        // Create node info.
        val keyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val clientCert = X509Utilities.createCertificate(CertificateType.CLIENT_CA, intermediateCACert, intermediateCAKey, CordaX500Name(organisation = "Test", locality = "London", country = "GB"), keyPair.public)
        val certPath = buildCertPath(clientCert.toX509Certificate(), intermediateCACert.toX509Certificate(), rootCACert.toX509Certificate())
        val nodeInfo = NodeInfo(listOf(NetworkHostAndPort("my.company.com", 1234)), listOf(PartyAndCertificate(certPath)), 1, serial = 1L)

        val request = X509Utilities.createCertificateSigningRequest(nodeInfo.legalIdentities.first().name, "my@mail.com", keyPair)

        val requestId = requestStorage.saveRequest(request)
        requestStorage.approveRequest(requestId)

        assertNull(nodeInfoStorage.getCertificatePath(keyPair.public.hash()))

        requestStorage.putCertificatePath(requestId, buildCertPath(clientCert.toX509Certificate(), intermediateCACert.toX509Certificate(), rootCACert.toX509Certificate()))

        val storedCertPath = nodeInfoStorage.getCertificatePath(keyPair.public.hash())
        assertNotNull(storedCertPath)

        assertEquals(clientCert.toX509Certificate(), storedCertPath!!.certificates.first())
    }

    @Test
    fun `test getNodeInfoHashes`() {
        // Create node info.
        val keyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val clientCert = X509Utilities.createCertificate(CertificateType.CLIENT_CA, intermediateCACert, intermediateCAKey, CordaX500Name(organisation = "Test", locality = "London", country = "GB"), keyPair.public)
        val certPath = buildCertPath(clientCert.toX509Certificate(), intermediateCACert.toX509Certificate(), rootCACert.toX509Certificate())
        val clientCert2 = X509Utilities.createCertificate(CertificateType.CLIENT_CA, intermediateCACert, intermediateCAKey, CordaX500Name(organisation = "Test", locality = "London", country = "GB"), Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME).public)
        val certPath2 = buildCertPath(clientCert2.toX509Certificate(), intermediateCACert.toX509Certificate(), rootCACert.toX509Certificate())
        val nodeInfo = NodeInfo(listOf(NetworkHostAndPort("my.company.com", 1234)), listOf(PartyAndCertificate(certPath)), 1, serial = 1L)
        val nodeInfoSame = NodeInfo(listOf(NetworkHostAndPort("my.company.com", 1234)), listOf(PartyAndCertificate(certPath)), 1, serial = 1L)
        val nodeInfo2 = NodeInfo(listOf(NetworkHostAndPort("my.company.com", 1234)), listOf(PartyAndCertificate(certPath2)), 1, serial = 1L)

        nodeInfoStorage.putNodeInfo(nodeInfo)
        nodeInfoStorage.putNodeInfo(nodeInfoSame)

        // getNodeInfoHashes should contain 1 hash.
        assertEquals(listOf(nodeInfo.serialize().sha256().toString()), nodeInfoStorage.getNodeInfoHashes())

        nodeInfoStorage.putNodeInfo(nodeInfo2)
        // getNodeInfoHashes should contain 2 hash.
        assertEquals(listOf(nodeInfo2.serialize().sha256().toString(), nodeInfo.serialize().sha256().toString()).sorted(), nodeInfoStorage.getNodeInfoHashes().sorted())

        // Test retrieve NodeInfo.
        assertEquals(nodeInfo, nodeInfoStorage.getNodeInfo(nodeInfo.serialize().sha256().toString()))
        assertEquals(nodeInfo2, nodeInfoStorage.getNodeInfo(nodeInfo2.serialize().sha256().toString()))
    }

    @Test
    fun `same pub key with different node info`() {
        // Create node info.
        val keyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val clientCert = X509Utilities.createCertificate(CertificateType.CLIENT_CA, intermediateCACert, intermediateCAKey, CordaX500Name(organisation = "Test", locality = "London", country = "GB"), keyPair.public)
        val certPath = buildCertPath(clientCert.toX509Certificate(), intermediateCACert.toX509Certificate(), rootCACert.toX509Certificate())
        val nodeInfo = NodeInfo(listOf(NetworkHostAndPort("my.company.com", 1234)), listOf(PartyAndCertificate(certPath)), 1, serial = 1L)
        val nodeInfoSamePubKey = NodeInfo(listOf(NetworkHostAndPort("my.company2.com", 1234)), listOf(PartyAndCertificate(certPath)), 1, serial = 1L)

        nodeInfoStorage.putNodeInfo(nodeInfo)
        assertEquals(nodeInfo, nodeInfoStorage.getNodeInfo(nodeInfo.serialize().sha256().toString()))

        // This should replace the node info.
        nodeInfoStorage.putNodeInfo(nodeInfoSamePubKey)
        // Old node info should be removed.
        assertNull(nodeInfoStorage.getNodeInfo(nodeInfo.serialize().sha256().toString()))
        assertEquals(nodeInfoSamePubKey, nodeInfoStorage.getNodeInfo(nodeInfoSamePubKey.serialize().sha256().toString()))
    }
}