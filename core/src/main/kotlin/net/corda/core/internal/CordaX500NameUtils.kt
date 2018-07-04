@file:JvmName("CordaX500NameUtils")
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
        return buildOrderedX500Name(mapOf(
                BCStyle.C to BCStyle.INSTANCE.stringToValue(BCStyle.C, country),
                BCStyle.ST to state?.let { BCStyle.INSTANCE.stringToValue(BCStyle.ST, it) },
                BCStyle.L to BCStyle.INSTANCE.stringToValue(BCStyle.L, locality),
                BCStyle.O to BCStyle.INSTANCE.stringToValue(BCStyle.O, organisation),
                BCStyle.OU to organisationUnit?.let { BCStyle.INSTANCE.stringToValue(BCStyle.OU, it) },
                BCStyle.CN to commonName?.let { BCStyle.INSTANCE.stringToValue(BCStyle.CN, it) }
        ))
    }

private val X500_NAME_PARTS = listOf(
        BCStyle.C,
        BCStyle.ST,
        BCStyle.L,
        BCStyle.O,
        BCStyle.OU,
        BCStyle.CN,
        BCStyle.T,
        BCStyle.SN,
        BCStyle.EmailAddress,
        BCStyle.DC,
        BCStyle.UID,
        BCStyle.STREET,
        BCStyle.SURNAME,
        BCStyle.GIVENNAME,
        BCStyle.INITIALS,
        BCStyle.GENERATION,
        BCStyle.UnstructuredAddress,
        BCStyle.UnstructuredName,
        BCStyle.UNIQUE_IDENTIFIER,
        BCStyle.DN_QUALIFIER,
        BCStyle.PSEUDONYM,
        BCStyle.POSTAL_ADDRESS,
        BCStyle.NAME_AT_BIRTH,
        BCStyle.COUNTRY_OF_CITIZENSHIP,
        BCStyle.COUNTRY_OF_RESIDENCE,
        BCStyle.GENDER,
        BCStyle.PLACE_OF_BIRTH,
        BCStyle.DATE_OF_BIRTH,
        BCStyle.POSTAL_CODE,
        BCStyle.BUSINESS_CATEGORY,
        BCStyle.TELEPHONE_NUMBER,
        BCStyle.NAME)

fun X500Principal.toOrderedX500Name(supportedAttributes: Set<ASN1ObjectIdentifier> = emptySet()): X500Name {
    return buildOrderedX500Name(this.attributesMap(supportedAttributes))
}

private fun buildOrderedX500Name(attributes: Map<ASN1ObjectIdentifier, ASN1Encodable?>): X500Name {
    return X500NameBuilder(BCStyle.INSTANCE).apply {
        X500_NAME_PARTS.forEach {
            val key = it
            attributes[it]?.let {
                addRDN(key, it)
            }
        }
    }.build()
}

fun X500Principal.attributesMap(supportedAttributes: Set<ASN1ObjectIdentifier> = emptySet()): Map<ASN1ObjectIdentifier, ASN1Encodable> {
    val x500Name = X500Name.getInstance(this.encoded)
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

@Suppress("unused")
@VisibleForTesting
val CordaX500Name.Companion.unspecifiedCountry
    get() = "ZZ"
