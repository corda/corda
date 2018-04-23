package com.r3.corda.networkmanage.common.persistence.entity

import com.r3.corda.networkmanage.common.utils.buildCertPath
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.NetworkParameters
import net.corda.core.serialization.SerializationFactory
import net.corda.core.serialization.serialize
import net.corda.core.utilities.sequence
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.crypto.X509CertificateFactory
import net.corda.nodeapi.internal.network.NetworkMap
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.security.cert.CertPath
import java.security.cert.X509CRL
import java.security.cert.X509Certificate
import javax.persistence.AttributeConverter

class PKCS10CertificationRequestConverter : AttributeConverter<PKCS10CertificationRequest, ByteArray> {
    override fun convertToEntityAttribute(dbData: ByteArray?): PKCS10CertificationRequest? = dbData?.let(::PKCS10CertificationRequest)
    override fun convertToDatabaseColumn(attribute: PKCS10CertificationRequest?): ByteArray? = attribute?.encoded
}

class CertPathConverter : AttributeConverter<CertPath, ByteArray> {
    override fun convertToEntityAttribute(dbData: ByteArray?): CertPath? = dbData?.let(::buildCertPath)
    override fun convertToDatabaseColumn(attribute: CertPath?): ByteArray? = attribute?.encoded
}

class X509CertificateConverter : AttributeConverter<X509Certificate, ByteArray> {
    override fun convertToEntityAttribute(dbData: ByteArray?): X509Certificate? {
        return dbData?.let { X509CertificateFactory().generateCertificate(it.inputStream()) }
    }
    override fun convertToDatabaseColumn(attribute: X509Certificate?): ByteArray? = attribute?.encoded
}

class X509CRLConverter : AttributeConverter<X509CRL, ByteArray> {
    override fun convertToEntityAttribute(dbData: ByteArray?): X509CRL? {
        return dbData?.let { X509CertificateFactory().delegate.generateCRL(it.inputStream()) as X509CRL }
    }
    override fun convertToDatabaseColumn(attribute: X509CRL?): ByteArray? = attribute?.encoded
}

class NetworkParametersConverter : CordaSerializationConverter<NetworkParameters>(NetworkParameters::class.java)

class NetworkMapConverter : CordaSerializationConverter<NetworkMap>(NetworkMap::class.java)

class SignedNodeInfoConverter : CordaSerializationConverter<SignedNodeInfo>(SignedNodeInfo::class.java)

class CordaX500NameAttributeConverter : ToStringConverter<CordaX500Name>(CordaX500Name.Companion::parse)

class SecureHashAttributeConverter : ToStringConverter<SecureHash>(SecureHash.Companion::parse)

abstract class CordaSerializationConverter<T : Any>(private val clazz: Class<T>) : AttributeConverter<T, ByteArray> {
    override fun convertToEntityAttribute(dbData: ByteArray?): T? {
        return dbData?.let {
            val serializationFactory = SerializationFactory.defaultFactory
            serializationFactory.deserialize(it.sequence(), clazz, serializationFactory.defaultContext)
        }
    }

    override fun convertToDatabaseColumn(attribute: T?): ByteArray? = attribute?.serialize()?.bytes
}

abstract class ToStringConverter<T : Any>(private val parser: (String) -> T) : AttributeConverter<T, String> {
    override fun convertToDatabaseColumn(attribute: T?): String? = attribute?.toString()
    override fun convertToEntityAttribute(dbData: String?): T? = dbData?.let { parser(it) }
}
