package com.r3.corda.networkmanage.common.utils

import com.google.common.base.CaseFormat
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import joptsimple.ArgumentAcceptingOptionSpec
import joptsimple.OptionParser
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.sha256
import net.corda.nodeapi.internal.crypto.X509CertificateFactory
import net.corda.nodeapi.internal.network.DigitalSignatureWithCert
import org.bouncycastle.cert.X509CertificateHolder
import java.security.PublicKey
import java.security.cert.CertPath
import java.security.cert.Certificate
import java.security.cert.X509Certificate

// TODO: replace this with Crypto.hash when its available.
/**
 * Returns SHA256 hash of this public key
 */
fun PublicKey.hashString() = encoded.sha256().toString()

fun Array<out String>.toConfigWithOptions(registerOptions: OptionParser.() -> Unit): Config {
    val parser = OptionParser()
    val helpOption = parser.acceptsAll(listOf("h", "?", "help"), "show help").forHelp()
    registerOptions(parser)
    val optionSet = parser.parse(*this)
    // Print help and exit on help option.
    if (optionSet.has(helpOption)) {
        throw ShowHelpException(parser)
    }
    // Convert all command line options to Config.
    return ConfigFactory.parseMap(parser.recognizedOptions().mapValues {
        val optionSpec = it.value
        if (optionSpec is ArgumentAcceptingOptionSpec<*> && !optionSpec.requiresArgument() && optionSet.has(optionSpec)) true else optionSpec.value(optionSet)
    }.mapKeys { it.key.toCamelcase() }.filterValues { it != null })
}

class ShowHelpException(val parser: OptionParser, val errorMessage: String? = null) : Exception()

fun X509CertificateHolder.toX509Certificate(): X509Certificate = X509CertificateFactory().generateCertificate(encoded.inputStream())

fun buildCertPath(vararg certificates: Certificate): CertPath = X509CertificateFactory().delegate.generateCertPath(certificates.asList())

fun buildCertPath(certPathBytes: ByteArray): CertPath = X509CertificateFactory().delegate.generateCertPath(certPathBytes.inputStream())

fun DigitalSignature.WithKey.withCert(cert: X509Certificate): DigitalSignatureWithCert = DigitalSignatureWithCert(cert, bytes)

private fun String.toCamelcase(): String {
    return if (contains('_') || contains('-')) {
        CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, this.replace("-", "_"))
    } else this
}