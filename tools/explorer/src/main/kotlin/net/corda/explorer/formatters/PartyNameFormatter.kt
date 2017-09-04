package net.corda.explorer.formatters

import net.corda.core.utilities.organisation
import org.bouncycastle.asn1.x500.X500Name

object PartyNameFormatter {
    val short = object : Formatter<X500Name> {
        override fun format(value: X500Name) = value.organisation!!
    }

    val full = object : Formatter<X500Name> {
        override fun format(value: X500Name): String = value.toString()
    }
}
