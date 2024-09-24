package net.corda.core.identity

import net.corda.core.CordaInternal
import net.corda.core.internal.LegalNameValidator
import net.corda.core.internal.toAttributesMap
import net.corda.core.internal.toX500Name
import net.corda.core.internal.unspecifiedCountry
import net.corda.core.internal.utilities.Internable
import net.corda.core.internal.utilities.PrivateInterner
import net.corda.core.serialization.CordaSerializable
import org.bouncycastle.asn1.x500.style.BCStyle
import java.util.*
import javax.security.auth.x500.X500Principal

/**
 * X.500 distinguished name data type customised to how Corda uses names. This restricts the attributes to those Corda
 * supports, and requires that organisation, locality and country attributes are specified. See also RFC 4519 for
 * the underlying attribute type definitions
 *
 * @property commonName optional name by the which the entity is usually known. Used only for services (for
 * organisations, the [organisation] property is the name). Corresponds to the "CN" attribute type.
 * @property organisationUnit optional name of a unit within the [organisation]. Corresponds to the "OU" attribute type.
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
                         val organisationUnit: String?,
                         val organisation: String,
                         val locality: String,
                         val state: String?,
                         val country: String) {
    constructor(commonName: String, organisation: String, locality: String, country: String) :
            this(commonName = commonName, organisationUnit = null, organisation = organisation, locality = locality, state = null, country = country)

    /**
     * @param organisation name of the organisation.
     * @param locality locality of the organisation, typically nearest major city.
     * @param country country the organisation is in, as an ISO 3166-1 2-letter country code.
     */
    constructor(organisation: String, locality: String, country: String) : this(null, null, organisation, locality, null, country)

    init {
        // Legal name checks.
        LegalNameValidator.validateOrganization(organisation, LegalNameValidator.Validation.MINIMAL)

        require(country in countryCodes) { "Invalid country code $country" }

        require(organisation.length < MAX_LENGTH_ORGANISATION) {
            "Organisation attribute (O) must contain less then $MAX_LENGTH_ORGANISATION characters."
        }
        require(locality.length < MAX_LENGTH_LOCALITY) { "Locality attribute (L) must contain less then $MAX_LENGTH_LOCALITY characters." }

        state?.let { require(it.length < MAX_LENGTH_STATE) { "State attribute (ST) must contain less then $MAX_LENGTH_STATE characters." } }
        organisationUnit?.let {
            require(it.length < MAX_LENGTH_ORGANISATION_UNIT) {
                "Organisation Unit attribute (OU) must contain less then $MAX_LENGTH_ORGANISATION_UNIT characters."
            }
        }
        commonName?.let {
            require(it.length < MAX_LENGTH_COMMON_NAME) {
                "Common Name attribute (CN) must contain less then $MAX_LENGTH_COMMON_NAME characters."
            }
        }
    }

    companion object : Internable<CordaX500Name> {
        @Deprecated("Not Used")
        const val LENGTH_COUNTRY = 2
        const val MAX_LENGTH_ORGANISATION = 128
        const val MAX_LENGTH_LOCALITY = 64
        const val MAX_LENGTH_STATE = 64
        const val MAX_LENGTH_ORGANISATION_UNIT = 64
        const val MAX_LENGTH_COMMON_NAME = 64

        private val supportedAttributes = setOf(BCStyle.O, BCStyle.C, BCStyle.L, BCStyle.CN, BCStyle.ST, BCStyle.OU)
        private val countryCodes: Set<String> = setOf(*Locale.getISOCountries(), unspecifiedCountry)

        @CordaInternal
        override val interner = PrivateInterner<CordaX500Name>()

        @JvmStatic
        fun build(principal: X500Principal): CordaX500Name {
            val attrsMap = principal.toAttributesMap(supportedAttributes)
            val CN = attrsMap[BCStyle.CN]
            val OU = attrsMap[BCStyle.OU]
            val O = requireNotNull(attrsMap[BCStyle.O]) { "Corda X.500 names must include an O attribute" }
            val L = requireNotNull(attrsMap[BCStyle.L]) { "Corda X.500 names must include an L attribute" }
            val ST = attrsMap[BCStyle.ST]
            val C = requireNotNull(attrsMap[BCStyle.C]) { "Corda X.500 names must include an C attribute" }
            return interner.intern(CordaX500Name(CN, OU, O, L, ST, C))
        }

        @JvmStatic
        fun parse(name: String): CordaX500Name = build(X500Principal(name))
    }

    @Transient
    private var _x500Principal: X500Principal? = null

    /** Return the [X500Principal] equivalent of this name. */
    val x500Principal: X500Principal
        get() {
            return _x500Principal ?: X500Principal(this.toX500Name().encoded).also { _x500Principal = it }
        }

    override fun toString(): String = x500Principal.toString()
}