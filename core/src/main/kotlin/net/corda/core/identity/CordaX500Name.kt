package net.corda.core.identity

import net.corda.core.serialization.CordaSerializable
import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle

/**
 * X.500 distinguished name data type customised to how Corda uses names. This restricts the attributes to those Corda
 * supports, and requires that organsation, locality and country attributes are specified. See also RFC 4519 for
 * the underlying attribute type definitions
 *
 * @property commonName optional name by the which the entity is usually known. Used only for services (for
 * organisations, the [organisation] property is the name). Corresponds to the "CN" attribute type.
 * @property organisationalUnit optional name of a unit within the [organisation]. Corresponds to the "OU" attribute type.
 * @property organisation name of the organisation. Corresponds to the "O" attribute type.
 * @property locality locality of the organisation, typically nearest major city. For distributed services this would be
 * where one of the organisations is based. Corresponds to the "L" attribute type.
 * @property state the full name of the state or province the organisation is based in. Corresponds to the "ST"
 * attribute type.
 * @property country country the organisation is in, as an ISO 3166-1 2-letter country code. Corresponds to the "C"
 * attribute type.
 */
@CordaSerializable
data class CordaX500Name(val commonName: String?,
                         val organisationalUnit: String?,
                         val organisation: String,
                         val locality: String,
                         val state: String?,
                         val country: String) {
    init {
        // TODO: validateX500Name(config.myLegalName)
    }
    constructor(commonName: String, organisation: String, locality: String, country: String) : this(null, commonName, organisation, locality, null, country)
    /**
     * @param organisation name of the organisation.
     * @param locality locality of the organisation, typically nearest major city.
     * @param country country the organisation is in, as an ISO 3166-1 2-letter country code.
     */
    constructor(organisation: String, locality: String, country: String) : this(null, null, organisation, locality, null, country)

    companion object {
        val VALID_ATTRIBUTE_TYPES = setOf(BCStyle.CN, BCStyle.OU, BCStyle.O, BCStyle.L, BCStyle.ST, BCStyle.C)
        val REQUIRED_ATTRIBUTE_TYPES = setOf(BCStyle.O, BCStyle.L, BCStyle.C)

        @JvmStatic
        fun build(x500: X500Name) : CordaX500Name {
            val attrsMap = HashMap<ASN1ObjectIdentifier, ASN1Encodable>()
            x500.rdNs.forEach { rdn ->
                require(!rdn.isMultiValued) { "Corda X.500 names must not include multi-valued attributes" }
                val attr = rdn.first
                if (attr.type in VALID_ATTRIBUTE_TYPES) {
                    attrsMap[attr.type] = attr.value
                } else {
                    throw IllegalArgumentException("Corda X.500 names do not support the ${attr.type.id} attribute.")
                }
            }
            REQUIRED_ATTRIBUTE_TYPES.forEach { attrType ->
                require(attrType in attrsMap.keys) { "Corda X.500 names must include an $attrType attribute" }
            }
            val CN = attrsMap[BCStyle.CN]?.toString()
            val OU = attrsMap[BCStyle.OU]?.toString()
            val O = attrsMap[BCStyle.O]?.toString() ?: throw IllegalArgumentException("Corda X.500 names must include an O attribute")
            val L = attrsMap[BCStyle.L]?.toString() ?: throw IllegalArgumentException("Corda X.500 names must include an L attribute")
            val ST = attrsMap[BCStyle.ST]?.toString()
            val C = attrsMap[BCStyle.C]?.toString() ?: throw IllegalArgumentException("Corda X.500 names must include an C attribute")
            return CordaX500Name(CN, OU, O, L, ST, C)
        }
        @JvmStatic
        fun parse(name: String) : CordaX500Name = build(X500Name(name))

        private fun appendAttr(builder: StringBuilder, id: ASN1ObjectIdentifier, value: String?): StringBuilder {
            if (value != null) {
                if (builder.isNotEmpty()) {
                    builder.append(",")
                }
                // TODO: Should we be doing any encoding of values?
                builder.append(id.id).append("=").append(value)
            }
            return builder
        }
    }

    @Transient
    private var x500Cache: X500Name? = null

    override fun toString(): String = x500Name.toString()

    /**
     * Return the underlying X.500 name from this Corda-safe X.500 name. These are guaranteed to have a consistent
     * ordering, such that their `toString()` function returns the same value every time for the same [CordaX500Name].
     */
    val x500Name: X500Name
        get() {
            if (x500Cache == null) {
                x500Cache = X500NameBuilder(BCStyle.INSTANCE).apply {
                    addRDN(BCStyle.C, country)
                    state?.let { addRDN(BCStyle.ST, it) }
                    addRDN(BCStyle.L, locality)
                    addRDN(BCStyle.O, organisation)
                    organisationalUnit?.let { addRDN(BCStyle.OU, it) }
                    commonName?.let { addRDN(BCStyle.CN, it) }
                }.build()
            }
            return x500Cache!!
        }
}