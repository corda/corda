package com.r3.corda.networkmanage.hsm.signer

import com.r3.corda.networkmanage.hsm.authentication.Authenticator
import com.r3.corda.networkmanage.hsm.authentication.readPassword
import com.r3.corda.networkmanage.hsm.persistence.ApprovedCertificateRequestData
import com.r3.corda.networkmanage.hsm.persistence.SignedCertificateRequestStorage
import com.r3.corda.networkmanage.hsm.utils.X509Utilities.buildCertPath
import com.r3.corda.networkmanage.hsm.utils.X509Utilities.createClientCertificate
import com.r3.corda.networkmanage.hsm.utils.X509Utilities.getAndInitializeKeyStore
import com.r3.corda.networkmanage.hsm.utils.X509Utilities.retrieveCertificateAndKeys

/**
 * Encapsulates certificate signing logic
 */
class HsmCsrSigner(private val storage: SignedCertificateRequestStorage,
                   private val caCertificateName: String,
                   private val caPrivateKeyPass: String?,
                   private val caParentCertificateName: String,
                   private val validDays: Int,
                   private val keyStorePassword: String?,
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
            val keyStore = getAndInitializeKeyStore(provider, keyStorePassword)
            // This should be changed once we allow for more certificates in the chain. Preferably we should use
            // keyStore.getCertificateChain(String) and assume entire chain is stored in the HSM (depending on the support).
            val caParentCertificate = keyStore.getCertificate(caParentCertificateName)
            val caPrivateKeyPass = caPrivateKeyPass ?: readPassword("CA Private Key Password: ", authenticator.console)
            val caCertAndKey = retrieveCertificateAndKeys(caCertificateName, caPrivateKeyPass, keyStore)
            toSign.forEach {
                it.certPath = buildCertPath(createClientCertificate(caCertAndKey, it.request, validDays, provider), caParentCertificate)
            }
            storage.store(toSign, signers)
            println("The following certificates have been signed by $signers:")
            toSign.forEachIndexed { index, data ->
                println("${index + 1} ${data.request.subject}")
            }
        }
    }
}