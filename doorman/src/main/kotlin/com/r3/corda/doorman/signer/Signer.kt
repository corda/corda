package com.r3.corda.doorman.signer

import com.r3.corda.doorman.buildCertPath
import com.r3.corda.doorman.persistence.CertificationRequestStorage
import com.r3.corda.doorman.toX509Certificate
import net.corda.core.identity.CordaX500Name
import net.corda.node.utilities.CertificateAndKeyPair
import net.corda.node.utilities.CertificateType
import net.corda.node.utilities.X509Utilities
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralSubtree
import org.bouncycastle.asn1.x509.NameConstraints
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest
import java.security.cert.Certificate

interface Signer {
    fun sign(requestId: String)
}

class LocalSigner(private val storage: CertificationRequestStorage,
                  private val caCertAndKey: CertificateAndKeyPair,
                  private val rootCACert: Certificate) : Signer {

    override fun sign(requestId: String) {
        storage.signCertificate(requestId) {
            val request = JcaPKCS10CertificationRequest(request)
            // The sub certs issued by the client must satisfy this directory name (or legal name in Corda) constraints, sub certs' directory name must be within client CA's name's subtree,
            // please see [sun.security.x509.X500Name.isWithinSubtree()] for more information.
            // We assume all attributes in the subject name has been checked prior approval.
            // TODO: add validation to subject name.
            val nameConstraints = NameConstraints(arrayOf(GeneralSubtree(GeneralName(GeneralName.directoryName, CordaX500Name.build(request.subject).copy(commonName = null).x500Name))), arrayOf())
            val ourCertificate = caCertAndKey.certificate
            val clientCertificate = X509Utilities.createCertificate(CertificateType.CLIENT_CA,
                    caCertAndKey.certificate,
                    caCertAndKey.keyPair,
                    CordaX500Name.build(request.subject).copy(commonName = X509Utilities.CORDA_CLIENT_CA_CN),
                    request.publicKey,
                    nameConstraints = nameConstraints).toX509Certificate()
            buildCertPath(clientCertificate, ourCertificate.toX509Certificate(), rootCACert)
        }
    }
}

class ExternalSigner : Signer {
    override fun sign(requestId: String) {
        // Do nothing
    }
}