package com.r3.corda.networkmanage.hsm.signer

import com.r3.corda.networkmanage.hsm.authentication.Authenticator
import com.r3.corda.networkmanage.hsm.persistence.ApprovedCertificateRequestData
import com.r3.corda.networkmanage.hsm.persistence.SignedCertificateRequestStorage
import com.r3.corda.networkmanage.hsm.utils.HsmX509Utilities.createClientCertificate
import com.r3.corda.networkmanage.hsm.utils.HsmX509Utilities.getAndInitializeKeyStore
import com.r3.corda.networkmanage.hsm.utils.HsmX509Utilities.retrieveCertAndKeyPair
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.X509Utilities
import org.bouncycastle.asn1.x500.X500Name

/**
 * Encapsulates certificate signing logic
 */
class HsmCsrSigner(private val storage: SignedCertificateRequestStorage,
                   private val rootKeyStore: X509KeyStore,
                   private val csrCertCrlDistPoint: String,
                   private val csrCertCrlIssuer: String?,
                   private val validDays: Int,
                   private val authenticator: Authenticator) : CertificateSigningRequestSigner {

    /**
     * Signs the provided list of approved certificate signing requests. By signature we mean creation of the client-level certificate
     * that is accompanied with a key pair (public + private) and signed by the intermediate CA (stored on the HSM)
     * using its private key.
     * That key (along with the certificate) is retrieved from the key store obtained from the provider given as a result of the
     * connectAndAuthenticate method of the authenticator.
     * The method iterates through the collection of the [ApprovedCertificateRequestData] instances passed as the method parameter
     * and sets the certificate field with an appropriate value.
     * @param toSign list of approved certificates to be signed
     */
    override fun sign(toSign: List<ApprovedCertificateRequestData>) {
        authenticator.connectAndAuthenticate { provider, signers ->
            // This should be changed once we allow for more certificates in the chain. Preferably we should use
            // keyStore.getCertificateChain(String) and assume entire chain is stored in the HSM (depending on the support).
            val rootCert = rootKeyStore.getCertificate(X509Utilities.CORDA_ROOT_CA)
            val keyStore = getAndInitializeKeyStore(provider)
            val doormanCertAndKey = retrieveCertAndKeyPair(X509Utilities.CORDA_INTERMEDIATE_CA, keyStore)
            toSign.forEach {
                val nodeCaCert = createClientCertificate(
                        CertificateType.NODE_CA,
                        doormanCertAndKey,
                        it.request,
                        validDays,
                        provider,
                        csrCertCrlDistPoint,
                        csrCertCrlIssuer?.let { X500Name(it) })
                it.certPath = X509Utilities.buildCertPath(nodeCaCert, doormanCertAndKey.certificate, rootCert)
            }
            storage.store(toSign, signers)
            println("The following certificates have been signed by $signers:")
            toSign.forEachIndexed { index, data ->
                println("${index + 1} ${data.request.subject}")
            }
        }
    }
}