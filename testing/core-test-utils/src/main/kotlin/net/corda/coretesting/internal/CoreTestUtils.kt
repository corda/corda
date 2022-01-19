package net.corda.coretesting.internal

import net.corda.core.identity.CordaX500Name
import net.corda.coretesting.internal.stubs.CertificateStoreStubs
import net.corda.nodeapi.internal.config.MutualSslConfiguration
import net.corda.nodeapi.internal.loadDevCaTrustStore
import net.corda.nodeapi.internal.registerDevP2pCertificates
import java.nio.file.Files

fun configureTestSSL(legalName: CordaX500Name): MutualSslConfiguration {

    val certificatesDirectory = Files.createTempDirectory("certs")
    val config = CertificateStoreStubs.P2P.withCertificatesDirectory(certificatesDirectory)
    if (config.trustStore.getOptional() == null) {
        loadDevCaTrustStore().copyTo(config.trustStore.get(true))
    }
    if (config.keyStore.getOptional() == null) {
        config.keyStore.get(true).registerDevP2pCertificates(legalName)
    }
    return config
}
