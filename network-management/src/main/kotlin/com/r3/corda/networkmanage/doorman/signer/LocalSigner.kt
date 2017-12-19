package com.r3.corda.networkmanage.doorman.signer

import com.r3.corda.networkmanage.common.signer.Signer
import com.r3.corda.networkmanage.common.utils.buildCertPath
import com.r3.corda.networkmanage.common.utils.withCert
import net.corda.core.crypto.sign
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.cert
import net.corda.core.internal.toX509CertHolder
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.network.DigitalSignatureWithCert
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralSubtree
import org.bouncycastle.asn1.x509.NameConstraints
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest
import java.security.KeyPair
import java.security.cert.CertPath
import java.security.cert.X509Certificate

/**
 *  The [LocalSigner] class signs [PKCS10CertificationRequest] using provided CA key pair and certificate path.
 *  This is intended to be used in testing environment where hardware signing module is not available.
 */
class LocalSigner(private val caKeyPair: KeyPair, private val caCertPath: Array<X509Certificate>) : Signer {
    fun createSignedClientCertificate(certificationRequest: PKCS10CertificationRequest): CertPath {
        // The sub certs issued by the client must satisfy this directory name (or legal name in Corda) constraints, sub certs' directory name must be within client CA's name's subtree,
        // please see [sun.security.x509.X500Name.isWithinSubtree()] for more information.
        // We assume all attributes in the subject name has been checked prior approval.
        // TODO: add validation to subject name.
        val request = JcaPKCS10CertificationRequest(certificationRequest)
        val nameConstraints = NameConstraints(
                arrayOf(GeneralSubtree(GeneralName(GeneralName.directoryName, request.subject))),
                arrayOf())
        val nodeCaCert = X509Utilities.createCertificate(
                CertificateType.NODE_CA,
                caCertPath.first().toX509CertHolder(),
                caKeyPair,
                CordaX500Name.parse(request.subject.toString()),
                request.publicKey,
                nameConstraints = nameConstraints)
        return buildCertPath(nodeCaCert.cert, *caCertPath)
    }

    override fun sign(data: ByteArray): DigitalSignatureWithCert {
        return caKeyPair.sign(data).withCert(caCertPath.first())
    }
}
