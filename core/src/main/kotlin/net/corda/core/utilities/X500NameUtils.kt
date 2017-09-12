@file:JvmName("X500NameUtils")

package net.corda.core.utilities

import net.corda.core.identity.CordaX500Name
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle

val X500Name.commonName: String? get() = getRDNValueString(BCStyle.CN)
val X500Name.state: String? get() = getRDNValueString(BCStyle.ST)
val X500Name.organisation: String get() = getRDNValueString(BCStyle.O) ?: throw IllegalArgumentException("Malformed X500 name, organisation attribute (O) cannot be empty.")
val X500Name.locality: String get() = getRDNValueString(BCStyle.L) ?: throw IllegalArgumentException("Malformed X500 name, locality attribute (L) cannot be empty.")
val X500Name.country: String get() = getRDNValueString(BCStyle.C) ?: throw IllegalArgumentException("Malformed X500 name, country attribute (C) cannot be empty.")

private fun X500Name.getRDNValueString(identifier: ASN1ObjectIdentifier): String? = getRDNs(identifier).firstOrNull()?.first?.value?.toString()


/**
 * Return the underlying X.500 name from this Corda-safe X.500 name. These are guaranteed to have a consistent
 * ordering, such that their `toString()` function returns the same value every time for the same [CordaX500Name].
 */
val CordaX500Name.x500Name: X500Name
    get() {
        return X500NameBuilder(BCStyle.INSTANCE).apply {
            addRDN(BCStyle.C, country)
            state?.let { addRDN(BCStyle.ST, it) }
            addRDN(BCStyle.L, locality)
            addRDN(BCStyle.O, organisation)
            organisationUnit?.let { addRDN(BCStyle.OU, it) }
            commonName?.let { addRDN(BCStyle.CN, it) }
        }.build()
    }