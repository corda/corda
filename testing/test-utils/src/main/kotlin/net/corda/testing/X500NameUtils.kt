@file:JvmName("X500NameUtils")

package net.corda.testing

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle

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