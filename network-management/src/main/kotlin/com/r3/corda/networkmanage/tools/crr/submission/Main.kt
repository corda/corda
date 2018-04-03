package com.r3.corda.networkmanage.tools.crr.submission

import com.r3.corda.networkmanage.common.utils.initialiseSerialization
import com.r3.corda.networkmanage.hsm.authentication.ConsoleInputReader
import com.r3.corda.networkmanage.hsm.authentication.InputReader
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.post
import net.corda.core.serialization.serialize
import net.corda.nodeapi.internal.network.CertificateRevocationRequest
import org.apache.logging.log4j.LogManager
import java.math.BigInteger
import java.net.URL
import java.security.cert.CRLReason

private val logger = LogManager.getLogger("com.r3.corda.networkmanage.common.tools.crr.Main")

fun main(args: Array<String>) {
    initialiseSerialization()
    try {
        submit(parseSubmissionUrl(*args))
    } catch (e: Exception) {
        logger.error("Error when submitting a certificate revocation request.", e)
        throw e
    }
}

fun submit(url: URL, inputReader: InputReader = ConsoleInputReader()) {
    val certificateSerialNumber = inputReader.getOptionalInput("certificate serial number")?.let { BigInteger(it) }
    val csrRequestId = inputReader.getOptionalInput("certificate signing request ID")
    val legalName = inputReader.getOptionalInput("node X.500 legal name")?.let { CordaX500Name.parse(it) }
    CertificateRevocationRequest.validateOptional(certificateSerialNumber, csrRequestId, legalName)
    val reason = inputReader.getRequiredInput("revocation reason").let { CRLReason.valueOf(it) }
    val reporter = inputReader.getRequiredInput("reporter of the revocation request")
    val request = CertificateRevocationRequest(certificateSerialNumber, csrRequestId, legalName, reason, reporter)
    logger.debug("POST to $url request: $request")
    val requestId = String(url.post(request.serialize()))
    logger.debug("Certificate revocation request successfully submitted. Request ID: $requestId")
    println("Successfully submitted certificate revocation request. Generated request ID: $requestId")
}

private fun InputReader.getOptionalInput(attributeName: String): String? {
    print("Type in $attributeName (press enter if not available):")
    return this.readLine()?.let {
        if (it.isBlank()) null else it
    }
}

private fun InputReader.getRequiredInput(attributeName: String): String {
    print("Type in $attributeName:")
    val line = this.readLine()
    return if (line == null || line.isNullOrBlank()) {
        throw IllegalArgumentException("The $attributeName needs to be specified.")
    } else {
        line
    }
}