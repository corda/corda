package com.r3.corda.networkmanage.common.utils

import com.google.common.base.CaseFormat
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import joptsimple.ArgumentAcceptingOptionSpec
import joptsimple.OptionParser
import net.corda.core.crypto.sha256
import net.corda.core.internal.SignedDataWithCert
import net.corda.nodeapi.internal.crypto.X509CertificateFactory
import net.corda.nodeapi.internal.network.NetworkMap
import net.corda.nodeapi.internal.network.NetworkParameters
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.CertPath
import java.security.cert.X509Certificate

const val CORDA_NETWORK_MAP = "cordanetworkmap"

// TODO These should be defined in node-api
typealias SignedNetworkParameters = SignedDataWithCert<NetworkParameters>
typealias SignedNetworkMap = SignedDataWithCert<NetworkMap>

data class CertPathAndKey(val certPath: List<X509Certificate>, val key: PrivateKey) {
    fun toKeyPair(): KeyPair = KeyPair(certPath[0].publicKey, key)
}

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

fun buildCertPath(vararg certificates: X509Certificate): CertPath = X509CertificateFactory().generateCertPath(certificates.asList())

fun buildCertPath(certPathBytes: ByteArray): CertPath = X509CertificateFactory().delegate.generateCertPath(certPathBytes.inputStream())

private fun String.toCamelcase(): String {
    return if (contains('_') || contains('-')) {
        CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, this.replace("-", "_"))
    } else this
}