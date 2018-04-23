@file:JvmName("Program")

package net.corda.sgx.cli

import net.corda.sgx.attestation.AttestationManager
import net.corda.sgx.attestation.service.ISVHttpClient
import net.corda.sgx.bridge.attestation.NativeAttestationEnclave
import net.corda.sgx.sealing.SecretManager
import org.slf4j.LoggerFactory

object Flow

fun main(args: Array<String>) {
    val log = LoggerFactory.getLogger(Flow::class.java)

    val dir = System.getProperty("corda.sgx.enclave.path")
    val enclavePath = "$dir/corda_sgx_ra_enclave.so"
    val enclave = NativeAttestationEnclave(enclavePath)
    val isvClient = ISVHttpClient()
    val secretManager = SecretManager(enclave)
    val manager = AttestationManager(enclave, secretManager, isvClient)

    log.info("Request challenge ...")
    val challenge = manager.requestChallenge()

    log.info("Initialize remote attestation context ...")
    manager.initialize(challenge)

    try {
        log.info("Send public key and group identifier ...")
        val details = manager.sendPublicKeyAndGroupIdentifier()

        log.info("Process challenger details and generate quote ...")
        val quote = enclave
                .processChallengerDetailsAndGenerateQuote(details)

        log.info("Submit generated quote ...")
        val response = manager.submitQuote(challenge, quote)
        log.info("Quote status = ${response.quoteStatus}")

        log.info("Verify attestation response ...")
        val (validMac, sealedSecret) =
                manager.verifyAttestationResponse(response)
        log.info("Has valid CMAC = $validMac")
        log.info("Sealed secret size = ${sealedSecret.size}")
    } catch (ex : Exception) {
        log.error("Failed to complete remote attestation", ex)
    } finally {
        manager.cleanUp()
    }
}
