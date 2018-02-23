package com.r3.corda.networkmanage.common.utils

import com.google.common.base.CaseFormat
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import joptsimple.ArgumentAcceptingOptionSpec
import joptsimple.OptionParser
import net.corda.core.CordaOID
import net.corda.core.crypto.sha256
import net.corda.core.internal.CertRole
import net.corda.core.internal.SignedDataWithCert
import net.corda.core.node.NetworkParameters
import net.corda.core.serialization.internal.SerializationEnvironmentImpl
import net.corda.core.serialization.internal.nodeSerializationEnv
import net.corda.nodeapi.internal.crypto.X509CertificateFactory
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.network.NetworkMap
import net.corda.nodeapi.internal.serialization.AMQP_P2P_CONTEXT
import net.corda.nodeapi.internal.serialization.SerializationFactoryImpl
import net.corda.nodeapi.internal.serialization.amqp.AMQPClientSerializationScheme
import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.pkcs.PKCS10CertificationRequest
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

fun buildCertPath(certPathBytes: ByteArray): CertPath = X509CertificateFactory().delegate.generateCertPath(certPathBytes.inputStream())

fun X509KeyStore.getCertPathAndKey(alias: String, privateKeyPassword: String): CertPathAndKey {
    return CertPathAndKey(getCertificateChain(alias), getPrivateKey(alias, privateKeyPassword))
}

fun initialiseSerialization() {
    val context = AMQP_P2P_CONTEXT
    nodeSerializationEnv = SerializationEnvironmentImpl(
            SerializationFactoryImpl().apply {
                registerScheme(AMQPClientSerializationScheme())
            },
            context)
}

private fun String.toCamelcase(): String {
    return if (contains('_') || contains('-')) {
        CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, this.replace("-", "_"))
    } else this
}

private fun PKCS10CertificationRequest.firstAttributeValue(identifier: ASN1ObjectIdentifier): ASN1Encodable? {
    return getAttributes(identifier).firstOrNull()?.attrValues?.firstOrNull()
}

/**
 * Helper method to extract cert role from certificate signing request. Default to NODE_CA if not exist for backward compatibility.
 */
fun PKCS10CertificationRequest.getCertRole(): CertRole {
    // Default cert role to Node_CA for backward compatibility.
    val encoded = firstAttributeValue(ASN1ObjectIdentifier(CordaOID.X509_EXTENSION_CORDA_ROLE))?.toASN1Primitive()?.encoded ?: return CertRole.NODE_CA
    return CertRole.getInstance(encoded)
}

/**
 * Helper method to extract email from certificate signing request.
 */
fun PKCS10CertificationRequest.getEmail(): String {
    // TODO: Add basic email check?
    return firstAttributeValue(BCStyle.E).toString()
}
