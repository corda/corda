package net.corda.node.internal.certificates

import net.corda.node.utilities.CertificateAndKeyPair
import net.corda.node.utilities.X509Utilities.CORDA_INTERMEDIATE_CA
import net.corda.node.utilities.X509Utilities.CORDA_ROOT_CA
import net.corda.node.utilities.getCertificateAndKeyPair
import net.corda.node.utilities.getX509Certificate
import net.corda.node.utilities.loadKeyStore
import java.security.KeyStore
import java.security.cert.Certificate
import java.security.cert.X509Certificate

interface CertificateSource {
    fun getCordaIntermediateCertificateAndKeyPair(keyPassword: String): CertificateAndKeyPair
    fun getCordaIntermediateCertificateChain(): Array<Certificate>?
    fun getCordaRootX509Certificate(): X509Certificate
    fun getCordaRootCertificate(): Certificate?
}

class CertificateSourceImpl(private val keyStore: KeyStore) : CertificateSource {
    override fun getCordaIntermediateCertificateAndKeyPair(keyPassword: String) = keyStore.getCertificateAndKeyPair(CORDA_INTERMEDIATE_CA, keyPassword)
    override fun getCordaIntermediateCertificateChain(): Array<Certificate>? = keyStore.getCertificateChain(CORDA_INTERMEDIATE_CA)
    override fun getCordaRootX509Certificate() = keyStore.getX509Certificate(CORDA_ROOT_CA)
    override fun getCordaRootCertificate(): Certificate? = keyStore.getCertificate(CORDA_ROOT_CA)
}

val caKeyStore = CertificateSourceImpl(loadKeyStore(CertificateSourceImpl::class.java.getResourceAsStream("cordadevcakeys.jks"), "cordacadevpass"))
