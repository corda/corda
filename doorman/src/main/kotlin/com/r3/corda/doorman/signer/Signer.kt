package com.r3.corda.doorman.signer

import com.r3.corda.doorman.buildCertPath
import com.r3.corda.doorman.toX509Certificate
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.toX509CertHolder
import net.corda.core.internal.x500Name
import net.corda.node.utilities.CertificateType
import net.corda.node.utilities.X509Utilities
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralSubtree
import org.bouncycastle.asn1.x509.NameConstraints
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest
import java.security.KeyPair
import java.security.cert.CertPath
import java.security.cert.Certificate

/**
 *  The [Signer] class signs [PKCS10CertificationRequest] using provided CA keypair and certificate path.
 *  This is intended to be used in testing environment where hardware signing module is not available.
 */
class Signer(private val caKeyPair: KeyPair, private val caCertPath: Array<Certificate>) {
    fun sign(certificationRequest: PKCS10CertificationRequest): CertPath {
        // The sub certs issued by the client must satisfy this directory name (or legal name in Corda) constraints, sub certs' directory name must be within client CA's name's subtree,
        // please see [sun.security.x509.X500Name.isWithinSubtree()] for more information.
        // We assume all attributes in the subject name has been checked prior approval.
        // TODO: add validation to subject name.
        val request = JcaPKCS10CertificationRequest(certificationRequest)
        val nameConstraints = NameConstraints(arrayOf(GeneralSubtree(GeneralName(GeneralName.directoryName, CordaX500Name.parse(request.subject.toString()).copy(commonName = null).x500Name))), arrayOf())
        val clientCertificate = X509Utilities.createCertificate(CertificateType.CLIENT_CA,
                caCertPath.first().toX509CertHolder(),
                caKeyPair,
                CordaX500Name.parse(request.subject.toString()).copy(commonName = X509Utilities.CORDA_CLIENT_CA_CN),
                request.publicKey,
                nameConstraints = nameConstraints).toX509Certificate()
        return buildCertPath(clientCertificate, *caCertPath)
    }
}
