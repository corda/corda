@file:JvmName("X500NameUtils")
package net.corda.core.utilities

import net.corda.core.internal.toX509CertHolder
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import java.security.KeyPair
import java.security.cert.X509Certificate

val X500Name.commonName: String? get() = getRDNValueString(BCStyle.CN)
val X500Name.organisation: String? get() = getRDNValueString(BCStyle.O)
val X500Name.organisationUnit: String? get() = getRDNValueString(BCStyle.OU)
val X500Name.state: String? get() = getRDNValueString(BCStyle.ST)
val X500Name.locality: String? get() = getRDNValueString(BCStyle.L)
val X500Name.country: String? get() = getRDNValueString(BCStyle.C)

private fun X500Name.getRDNValueString(identifier: ASN1ObjectIdentifier): String? = getRDNs(identifier).firstOrNull()?.first?.value?.toString()

val X509Certificate.subject: X500Name get() = toX509CertHolder().subject
val X509CertificateHolder.cert: X509Certificate get() = JcaX509CertificateConverter().getCertificate(this)

/**
 * Generate a distinguished name from the provided values.
 */
fun getX500Name(O: String, L: String, C: String, CN: String? = null, OU: String? = null, ST: String? = null): X500Name {
    return X500NameBuilder(BCStyle.INSTANCE).apply {
        addRDN(BCStyle.C, C)
        ST?.let { addRDN(BCStyle.ST, it) }
        addRDN(BCStyle.L, L)
        addRDN(BCStyle.O, O)
        OU?.let { addRDN(BCStyle.OU, it) }
        CN?.let { addRDN(BCStyle.CN, it) }
    }.build()
}

fun X500Name.withCommonName(commonName: String?): X500Name {
    return getX500Name(organisation!!, locality!!, country!!, commonName, organisationUnit, state)
}

fun X500Name.toWellFormattedName(): X500Name {
    require(organisation != null) { "Organisation (O) attribute is mandatory." }
    require(locality != null) { "Locality (L) attribute is mandatory." }
    require(country != null) { "country (C) attribute is mandatory." }
    return getX500Name(organisation!!, locality!!, country!!, commonName, organisationUnit, state)
}

data class CertificateAndKeyPair(val certificate: X509CertificateHolder, val keyPair: KeyPair)
