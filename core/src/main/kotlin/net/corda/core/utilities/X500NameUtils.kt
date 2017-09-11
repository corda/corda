@file:JvmName("X500NameUtils")

package net.corda.core.utilities

import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle

val X500Name.commonName: String? get() = getRDNValueString(BCStyle.CN)
val X500Name.organisationUnit: String? get() = getRDNValueString(BCStyle.OU)
val X500Name.state: String? get() = getRDNValueString(BCStyle.ST)
val X500Name.organisation: String get() = getRDNValueString(BCStyle.O) ?: throw IllegalArgumentException("Malformed X500 name, organisation attribute (O) cannot be empty.")
val X500Name.locality: String get() = getRDNValueString(BCStyle.L) ?: throw IllegalArgumentException("Malformed X500 name, locality attribute (L) cannot be empty.")
val X500Name.country: String get() = getRDNValueString(BCStyle.C) ?: throw IllegalArgumentException("Malformed X500 name, country attribute (C) cannot be empty.")

private fun X500Name.getRDNValueString(identifier: ASN1ObjectIdentifier): String? = getRDNs(identifier).firstOrNull()?.first?.value?.toString()

/**
 * Generate a distinguished name from the provided X500 .
 *
 * @param O organisation name.
 * @param L locality.
 * @param C county.
 * @param CN common name.
 * @param OU organisation unit.
 * @param ST state.
 */
@JvmOverloads
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
    return getX500Name(organisation, locality, country, commonName, organisationUnit, state)
}

fun X500Name.toWellFormattedName(): X500Name {
    validateX500Name(this)
    return getX500Name(organisation, locality, country, commonName, organisationUnit, state)
}
