@file:JvmName("X500NameUtils")
package net.corda.core.crypto

import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import java.security.KeyPair
import java.security.cert.X509Certificate

/**
 * Rebuild the distinguished name, adding a postfix to the common name. If no common name is present.
 * @throws IllegalArgumentException if the distinguished name does not contain a common name element.
 */
fun X500Name.appendToCommonName(commonName: String): X500Name = mutateCommonName { attr -> attr.toString() + commonName }

/**
 * Rebuild the distinguished name, replacing the common name with the given value. If no common name is present, this
 * adds one.
 * @throws IllegalArgumentException if the distinguished name does not contain a common name element.
 */
fun X500Name.replaceCommonName(commonName: String): X500Name = mutateCommonName { _ -> commonName }

/**
 * Rebuild the distinguished name, replacing the common name with a value generated from the provided function.
 *
 * @param mutator a function to generate the new value from the previous one.
 * @throws IllegalArgumentException if the distinguished name does not contain a common name element.
 */
private fun X500Name.mutateCommonName(mutator: (ASN1Encodable) -> String): X500Name {
    val builder = X500NameBuilder(BCStyle.INSTANCE)
    var matched = false
    this.rdNs.forEach { rdn ->
        rdn.typesAndValues.forEach { typeAndValue ->
            when (typeAndValue.type) {
                BCStyle.CN -> {
                    matched = true
                    builder.addRDN(typeAndValue.type, mutator(typeAndValue.value))
                }
                else -> {
                    builder.addRDN(typeAndValue)
                }
            }
        }
    }
    require(matched) { "Input X.500 name must include a common name (CN) attribute: ${this}" }
    return builder.build()
}

val X500Name.commonName: String get() = getRDNs(BCStyle.CN).first().first.value.toString()
val X500Name.orgName: String? get() = getRDNs(BCStyle.O).firstOrNull()?.first?.value?.toString()
val X500Name.location: String get() = getRDNs(BCStyle.L).first().first.value.toString()
val X500Name.locationOrNull: String? get() = try {
    location
} catch (e: Exception) {
    null
}
val X509Certificate.subject: X500Name get() = X509CertificateHolder(encoded).subject
val X509CertificateHolder.cert: X509Certificate get() = JcaX509CertificateConverter().getCertificate(this)

/**
 * Generate a distinguished name from the provided values.
 */
@JvmOverloads
fun getX509Name(myLegalName: String, nearestCity: String, email: String, country: String? = null): X500Name {
    return X500NameBuilder(BCStyle.INSTANCE).let { builder ->
        builder.addRDN(BCStyle.CN, myLegalName)
        builder.addRDN(BCStyle.L, nearestCity)
        country?.let { builder.addRDN(BCStyle.C, it) }
        builder.addRDN(BCStyle.E, email)
        builder.build()
    }
}

data class CertificateAndKeyPair(val certificate: X509CertificateHolder, val keyPair: KeyPair)
