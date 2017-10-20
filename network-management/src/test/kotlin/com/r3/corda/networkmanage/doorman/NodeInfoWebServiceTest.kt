package com.r3.corda.networkmanage.doorman

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.r3.corda.networkmanage.common.persistence.NodeInfoStorage
import com.r3.corda.networkmanage.common.utils.buildCertPath
import com.r3.corda.networkmanage.common.utils.toX509Certificate
import com.r3.corda.networkmanage.doorman.webservice.NodeInfoWebService
import net.corda.core.crypto.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.serialization.KryoServerSerializationScheme
import net.corda.node.utilities.CertificateType
import net.corda.node.utilities.X509Utilities
import net.corda.nodeapi.internal.serialization.*
import org.bouncycastle.asn1.x500.X500Name
import org.codehaus.jackson.map.ObjectMapper
import org.junit.BeforeClass
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.ws.rs.core.MediaType
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NodeInfoWebServiceTest {
    private val rootCAKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
    private val rootCACert = X509Utilities.createSelfSignedCACertificate(CordaX500Name(locality = "London", organisation = "R3 LTD", country = "GB", commonName = "Corda Node Root CA"), rootCAKey)
    private val intermediateCAKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
    private val intermediateCACert = X509Utilities.createCertificate(CertificateType.INTERMEDIATE_CA, rootCACert, rootCAKey, X500Name("CN=Corda Node Intermediate CA,L=London"), intermediateCAKey.public)

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

    @Test
    fun `submit nodeInfo`() {
        // Create node info.
        val keyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val clientCert = X509Utilities.createCertificate(CertificateType.CLIENT_CA, intermediateCACert, intermediateCAKey, CordaX500Name(organisation = "Test", locality = "London", country = "GB"), keyPair.public)
        val certPath = buildCertPath(clientCert.toX509Certificate(), intermediateCACert.toX509Certificate(), rootCACert.toX509Certificate())
        val nodeInfo = NodeInfo(listOf(NetworkHostAndPort("my.company.com", 1234)), listOf(PartyAndCertificate(certPath)), 1, serial = 1L)

        // Create digital signature.
        val digitalSignature = DigitalSignature.WithKey(keyPair.public, Crypto.doSign(keyPair.private, nodeInfo.serialize().bytes))

        val nodeInfoStorage: NodeInfoStorage = mock {
            on { getCertificatePath(any()) }.thenReturn(certPath)
        }

        DoormanServer(NetworkHostAndPort("localhost", 0), NodeInfoWebService(nodeInfoStorage)).use {
            it.start()
            val registerURL = URL("http://${it.hostAndPort}/api/${NodeInfoWebService.networkMapPath}/register")
            val nodeInfoAndSignature = SignedData(nodeInfo.serialize(), digitalSignature).serialize().bytes
            // Post node info and signature to doorman
            doPost(registerURL, nodeInfoAndSignature)
            verify(nodeInfoStorage, times(1)).getCertificatePath(any())
        }
    }

    @Test
    fun `submit nodeInfo with invalid signature`() {
        // Create node info.
        val keyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val clientCert = X509Utilities.createCertificate(CertificateType.CLIENT_CA, intermediateCACert, intermediateCAKey, CordaX500Name(organisation = "Test", locality = "London", country = "GB"), keyPair.public)
        val certPath = buildCertPath(clientCert.toX509Certificate(), intermediateCACert.toX509Certificate(), rootCACert.toX509Certificate())
        val nodeInfo = NodeInfo(listOf(NetworkHostAndPort("my.company.com", 1234)), listOf(PartyAndCertificate(certPath)), 1, serial = 1L)

        // Create digital signature.
        val attackerKeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val digitalSignature = DigitalSignature.WithKey(attackerKeyPair.public, Crypto.doSign(attackerKeyPair.private, nodeInfo.serialize().bytes))

        val nodeInfoStorage: NodeInfoStorage = mock {
            on { getCertificatePath(any()) }.thenReturn(certPath)
        }

        DoormanServer(NetworkHostAndPort("localhost", 0), NodeInfoWebService(nodeInfoStorage)).use {
            it.start()
            val registerURL = URL("http://${it.hostAndPort}/api/${NodeInfoWebService.networkMapPath}/register")
            val nodeInfoAndSignature = SignedData(nodeInfo.serialize(), digitalSignature).serialize().bytes
            // Post node info and signature to doorman
            assertFailsWith(IOException::class) {
                doPost(registerURL, nodeInfoAndSignature)
            }
            verify(nodeInfoStorage, times(1)).getCertificatePath(any())
        }
    }

    @Test
    fun `get network map`() {
        val networkMapList = listOf(SecureHash.randomSHA256().toString(), SecureHash.randomSHA256().toString())
        val nodeInfoStorage: NodeInfoStorage = mock {
            on { getNodeInfoHashes() }.thenReturn(networkMapList)
        }
        DoormanServer(NetworkHostAndPort("localhost", 0), NodeInfoWebService(nodeInfoStorage)).use {
            it.start()
            val conn = URL("http://${it.hostAndPort}/api/${NodeInfoWebService.networkMapPath}").openConnection() as HttpURLConnection
            val response = conn.inputStream.bufferedReader().use { it.readLine() }
            val list = ObjectMapper().readValue(response, List::class.java)
            verify(nodeInfoStorage, times(1)).getNodeInfoHashes()
            assertEquals(networkMapList, list)
        }
    }

    @Test
    fun `get node info`() {
        val keyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val clientCert = X509Utilities.createCertificate(CertificateType.CLIENT_CA, intermediateCACert, intermediateCAKey, CordaX500Name(organisation = "Test", locality = "London", country = "GB"), keyPair.public)
        val certPath = buildCertPath(clientCert.toX509Certificate(), intermediateCACert.toX509Certificate(), rootCACert.toX509Certificate())
        val nodeInfo = NodeInfo(listOf(NetworkHostAndPort("my.company.com", 1234)), listOf(PartyAndCertificate(certPath)), 1, serial = 1L)

        val nodeInfoHash = nodeInfo.serialize().sha256().toString()

        val nodeInfoStorage: NodeInfoStorage = mock {
            on { getNodeInfo(nodeInfoHash) }.thenReturn(nodeInfo)
        }

        DoormanServer(NetworkHostAndPort("localhost", 0), NodeInfoWebService(nodeInfoStorage)).use {
            it.start()
            val nodeInfoURL = URL("http://${it.hostAndPort}/api/${NodeInfoWebService.networkMapPath}/$nodeInfoHash")
            val conn = nodeInfoURL.openConnection()
            val nodeInfoResponse = conn.inputStream.readBytes().deserialize<NodeInfo>()
            verify(nodeInfoStorage, times(1)).getNodeInfo(nodeInfoHash)
            assertEquals(nodeInfo, nodeInfoResponse)

            assertFailsWith(FileNotFoundException::class) {
                URL("http://${it.hostAndPort}/api/${NodeInfoWebService.networkMapPath}/${SecureHash.randomSHA256()}").openConnection().getInputStream()
            }
        }
    }

    private fun doPost(url: URL, payload: ByteArray) {
        val conn = url.openConnection() as HttpURLConnection
        conn.doOutput = true
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", MediaType.APPLICATION_OCTET_STREAM)
        conn.outputStream.write(payload)

        return try {
            conn.inputStream.bufferedReader().use { it.readLine() }
        } catch (e: IOException) {
            throw IOException(conn.errorStream.bufferedReader().readLine(), e)
        }
    }
}