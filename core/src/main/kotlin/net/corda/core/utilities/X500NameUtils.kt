@file:JvmName("X500NameUtils")

package net.corda.core.utilities

import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.style.BCStyle

val X500Name.commonName: String? get() = getRDNValueString(BCStyle.CN)
val X500Name.state: String? get() = getRDNValueString(BCStyle.ST)
val X500Name.organisation: String get() = getRDNValueString(BCStyle.O) ?: throw IllegalArgumentException("Malformed X500 name, organisation attribute (O) cannot be empty.")
val X500Name.locality: String get() = getRDNValueString(BCStyle.L) ?: throw IllegalArgumentException("Malformed X500 name, locality attribute (L) cannot be empty.")
val X500Name.country: String get() = getRDNValueString(BCStyle.C) ?: throw IllegalArgumentException("Malformed X500 name, country attribute (C) cannot be empty.")

private fun X500Name.getRDNValueString(identifier: ASN1ObjectIdentifier): String? = getRDNs(identifier).firstOrNull()?.first?.value?.toString()
