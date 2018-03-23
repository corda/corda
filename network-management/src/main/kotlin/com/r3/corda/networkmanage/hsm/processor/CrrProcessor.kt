package com.r3.corda.networkmanage.hsm.processor

import com.r3.corda.networkmanage.common.persistence.CrlIssuer
import com.r3.corda.networkmanage.common.signer.CertificateRevocationListSigner
import com.r3.corda.networkmanage.hsm.authentication.Authenticator
import com.r3.corda.networkmanage.hsm.authentication.createProvider
import com.r3.corda.networkmanage.hsm.configuration.DoormanCertificateConfig
import com.r3.corda.networkmanage.hsm.menu.Menu
import com.r3.corda.networkmanage.hsm.signer.HsmSigner
import com.r3.corda.networkmanage.hsm.sockets.SocketCertificateRevocationList
import com.r3.corda.networkmanage.hsm.sockets.CertificateRevocationRequestRetriever
import com.r3.corda.networkmanage.hsm.utils.HsmX509Utilities.getAndInitializeKeyStore
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_INTERMEDIATE_CA
import net.corda.nodeapi.internal.crypto.getX509Certificate
import java.time.Duration

class CrrProcessor(private val config: DoormanCertificateConfig,
                   private val device: String,
                   private val keySpecifier: Int) : Processor() {
    private companion object {
        private const val RESET = "\u001B[0m"
        private const val BLACK = "\u001B[30m"
        private const val RED = "\u001B[31m"
        private const val GREEN = "\u001B[32m"
        private const val YELLOW = "\u001B[33m"
        private const val BLUE = "\u001B[34m"
        private const val CYAN = "\u001B[36m"
        private const val WHITE = "\u001B[37m"
    }

    private val auth = config.authParameters

    fun showMenu() {
        val authenticator = Authenticator(
                provider = createProvider(config.keyGroup, keySpecifier, device),
                mode = auth.mode,
                authStrengthThreshold = auth.threshold)
        Menu().withExceptionHandler(this::processError).setExitOption("2", "Quit").addItem("1", "View current and sign a new certificate revocation list", {
            val crlTransceiver = SocketCertificateRevocationList(config.crlServerSocketAddress)
            val currentCrl = crlTransceiver.getCertificateRevocationList(CrlIssuer.DOORMAN)
            printlnColor("Current CRL:")
            printlnColor(currentCrl.toString(), YELLOW)
            val crrRetriever = CertificateRevocationRequestRetriever(config.crlServerSocketAddress)
            val approvedRequests = crrRetriever.retrieveApprovedCertificateRevocationRequests()
            if (approvedRequests.isEmpty()) {
                printlnColor("There are no approved Certificate Revocation Requests.", GREEN)
            } else {
                printlnColor("Following are the approved Certificate Revocation Requests which will be added to the CRL:")
                approvedRequests.forEach {
                    printlnColor("Certificate DN: ${it.legalName}, Certificate serial number: ${it.certificateSerialNumber}", GREEN)
                }
            }
            Menu().withExceptionHandler(this::processError).setExitOption("2", "Go back").
                    addItem("1", "Create and sign a new Certificate Revocation List", {
                        authenticator.connectAndAuthenticate { provider, signers ->
                            val keyStore = getAndInitializeKeyStore(provider)
                            val issuerCertificate = keyStore.getX509Certificate(CORDA_INTERMEDIATE_CA)
                            val crlSigner = CertificateRevocationListSigner(
                                    revocationListStorage = crlTransceiver,
                                    issuerCertificate = issuerCertificate,
                                    updateInterval = Duration.ofMillis(config.crlUpdatePeriod),
                                    endpoint = config.crlDistributionPoint,
                                    signer = HsmSigner(provider = provider, keyName = CORDA_INTERMEDIATE_CA))
                            val currentRequests = crrRetriever.retrieveDoneCertificateRevocationRequests()
                            crlSigner.createSignedCRL(approvedRequests, currentRequests, signers.toString())
                        }
                    })
        }).showMenu()
    }
}