package net.corda.core.identity

import net.corda.core.serialization.CordaSerializable
import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle

/**
 * X.500 distinguished name data type customised to how Corda uses names. This restricts the attributes to those Corda
 * supports, and requires that O, L and C attributes are specified.
 *
 * @property O name of the organisation.
 * @property L locality of the organisation, typically nearest major city.
 * @property C country the organisation is in, as an ISO 3166-1 2-letter country code.
 */
@CordaSerializable
data class CordaX500Name(val CN: String?,
                         val OU: String?,
                         val O: String,
                         val L: String,
                         val ST: String?,
                         val C: String) {
    init {
        // TODO: validateX500Name(config.myLegalName)
    }
    constructor(CN: String, O: String, L: String, C: String) : this(null, CN, O, L, null, C)
    constructor(O: String, L: String, C: String) : this(null, null, O, L, null, C)

    companion object {
        @JvmStatic
        fun build(x500: X500Name) : CordaX500Name {
            val attrsMap = HashMap<ASN1ObjectIdentifier, ASN1Encodable>()
            x500.rdNs.forEach { rdn ->
                require(!rdn.isMultiValued) { "Corda X.500 names must not include multi-valued attributes" }
                val attr = rdn.first
                when (attr.type) {
                    BCStyle.CN -> { attrsMap[attr.type] = attr.value }
                    BCStyle.OU -> { attrsMap[attr.type] = attr.value }
                    BCStyle.O -> { attrsMap[attr.type] = attr.value }
                    BCStyle.L -> { attrsMap[attr.type] = attr.value }
                    BCStyle.ST -> { attrsMap[attr.type] = attr.value }
                    BCStyle.C -> { attrsMap[attr.type] = attr.value }
                    else -> {
                        throw IllegalArgumentException("Corda X.500 names do not support the ${attr.type.id} attribute.")
                    }
                }
            }
            val commonName = attrsMap[BCStyle.CN]?.toString()
            val OU = attrsMap[BCStyle.OU]?.toString()
            val O = attrsMap[BCStyle.O]?.toString() ?: throw IllegalArgumentException("Corda X.500 names must include an O attribute")
            val L = attrsMap[BCStyle.L]?.toString() ?: throw IllegalArgumentException("Corda X.500 names must include an L attribute")
            val ST = attrsMap[BCStyle.ST]?.toString()
            val C = attrsMap[BCStyle.C]?.toString() ?: throw IllegalArgumentException("Corda X.500 names must include an C attribute")
            return CordaX500Name(commonName, OU, O, L, ST, C)
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

    val commonName: String?
        get() = CN
    val organisationalUnit: String?
        get() = OU
    val organisation: String
        get() = O
    val locality: String
        get() = L
    val state: String?
        get() = ST
    val country: String
        get() = C

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
                    addRDN(BCStyle.C, C)
                    ST?.let { addRDN(BCStyle.ST, it) }
                    addRDN(BCStyle.L, L)
                    addRDN(BCStyle.O, O)
                    OU?.let { addRDN(BCStyle.OU, it) }
                    CN?.let { addRDN(BCStyle.CN, it) }
                }.build()
            }
            return x500Cache!!
        }
}