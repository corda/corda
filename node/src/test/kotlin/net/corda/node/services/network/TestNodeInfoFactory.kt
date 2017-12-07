package net.corda.node.services.network

import net.corda.core.crypto.Crypto
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.SignedData
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.serialize
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509CertificateFactory
import net.corda.nodeapi.internal.crypto.X509Utilities
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.X509CertificateHolder
import java.security.KeyPair
import java.security.cert.CertPath
import java.security.cert.Certificate
import java.security.cert.X509Certificate

object TestNodeInfoFactory {
    private val rootCAKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
    private val rootCACert = X509Utilities.createSelfSignedCACertificate(CordaX500Name(commonName = "Corda Node Root CA", organisation = "R3 LTD", locality = "London", country = "GB"), rootCAKey)
    private val intermediateCAKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
    private val intermediateCACert = X509Utilities.createCertificate(CertificateType.INTERMEDIATE_CA, rootCACert, rootCAKey, X500Name("CN=Corda Node Intermediate CA,L=London"), intermediateCAKey.public)

    fun createNodeInfo(organisation: String): SignedData<NodeInfo> {
        val keyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val clientCert = X509Utilities.createCertificate(CertificateType.NODE_CA, intermediateCACert, intermediateCAKey, CordaX500Name(organisation = organisation, locality = "London", country = "GB"), keyPair.public)
        val certPath = buildCertPath(clientCert.toX509Certificate(), intermediateCACert.toX509Certificate(), rootCACert.toX509Certificate())
        val nodeInfo = NodeInfo(listOf(NetworkHostAndPort("my.$organisation.com", 1234)), listOf(PartyAndCertificate(certPath)), 1, serial = 1L)
        return sign(keyPair, nodeInfo)
    }

    fun <T : Any> sign(keyPair: KeyPair, t: T): SignedData<T> {
        // Create digital signature.
        val digitalSignature = DigitalSignature.WithKey(keyPair.public, Crypto.doSign(keyPair.private, t.serialize().bytes))
        return SignedData(t.serialize(), digitalSignature)
    }

    private fun buildCertPath(vararg certificates: Certificate): CertPath {
        return X509CertificateFactory().delegate.generateCertPath(certificates.asList())
    }

    private fun X509CertificateHolder.toX509Certificate(): X509Certificate {
        return X509CertificateFactory().generateCertificate(encoded.inputStream())
    }

}