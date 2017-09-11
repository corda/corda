@file:JvmName("X500NameUtils")

package net.corda.core.utilities

import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.style.BCStyle

val X500Name.commonName: String? get() = getRDNValueString(BCStyle.CN)

private fun X500Name.getRDNValueString(identifier: ASN1ObjectIdentifier): String? = getRDNs(identifier).firstOrNull()?.first?.value?.toString()
