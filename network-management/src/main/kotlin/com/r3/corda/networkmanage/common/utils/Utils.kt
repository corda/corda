/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.common.utils

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import com.typesafe.config.ConfigRenderOptions
import net.corda.core.CordaOID
import net.corda.core.internal.CertRole
import net.corda.core.serialization.internal.SerializationEnvironmentImpl
import net.corda.core.serialization.internal.nodeSerializationEnv
import net.corda.nodeapi.internal.config.parseAs
import net.corda.nodeapi.internal.crypto.X509CertificateFactory
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.serialization.AMQP_P2P_CONTEXT
import net.corda.nodeapi.internal.serialization.SerializationFactoryImpl
import net.corda.nodeapi.internal.serialization.amqp.AMQPClientSerializationScheme
import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.security.KeyPair
import java.security.PrivateKey
import java.security.cert.CertPath
import java.security.cert.X509Certificate

const val CORDA_NETWORK_MAP = "cordanetworkmap"

val logger: Logger = LoggerFactory.getLogger("com.r3.corda.networkmanage.common.utils")

data class CertPathAndKey(val certPath: List<X509Certificate>, val key: PrivateKey) {
    fun toKeyPair(): KeyPair = KeyPair(certPath[0].publicKey, key)
}

inline fun <reified T : Any> parseConfig(file: Path): T {
    val config = ConfigFactory.parseFile(file.toFile(), ConfigParseOptions.defaults().setAllowMissing(true)).resolve()
    logger.info(config.root().render(ConfigRenderOptions.defaults()))
    return config.parseAs(strict = false)
}

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
fun PKCS10CertificationRequest.getEmail(): String = firstAttributeValue(BCStyle.E).toString()

fun <K, V, U> Map<K, V>.join(otherMap: Map<K, U>): Map<K, Pair<V?, U?>> = (keys + otherMap.keys).map { it to Pair(get(it), otherMap[it]) }.toMap()
