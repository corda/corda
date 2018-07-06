@file:KeepForDJVM

package net.corda.core.internal

import net.corda.core.KeepForDJVM
import net.corda.core.identity.CordaX500Name
import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x500.AttributeTypeAndValue
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import javax.security.auth.x500.X500Principal

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

/**
 * Converts the X500Principal instance to the X500Name object.
 */
fun X500Principal.toX500Name(): X500Name = X500Name.getInstance(this.encoded)

/**
 * Transforms the X500Principal to the attributes map.
 *
 * @param supportedAttributes list of supported attributes. If empty, it accepts all the attributes.
 *
 * @return attributes map for this principal
 * @throws IllegalArgumentException if this principal consists of duplicated attributes or the attribute is not supported.
 *
 */
fun X500Principal.toAttributesMap(supportedAttributes: Set<ASN1ObjectIdentifier> = emptySet()): Map<ASN1ObjectIdentifier, ASN1Encodable> {
    val x500Name = this.toX500Name()
    val attrsMap: Map<ASN1ObjectIdentifier, ASN1Encodable> = x500Name.rdNs
            .flatMap { it.typesAndValues.asList() }
            .groupBy(AttributeTypeAndValue::getType, AttributeTypeAndValue::getValue)
            .mapValues {
                require(it.value.size == 1) { "Duplicate attribute ${it.key}" }
                it.value[0]
            }
    if (supportedAttributes.isNotEmpty()) {
        (attrsMap.keys - supportedAttributes).let { unsupported ->
            require(unsupported.isEmpty()) {
                "The following attribute${if (unsupported.size > 1) "s are" else " is"} not supported in Corda: " +
                        unsupported.map { BCStyle.INSTANCE.oidToDisplayName(it) }
            }
        }
    }
    return attrsMap
}

fun X500Principal.equalX500NameParts(other: X500Principal): Boolean {
    return toAttributesMap() == other.toAttributesMap()
}

@VisibleForTesting
val CordaX500Name.Companion.unspecifiedCountry
    get() = "ZZ"
