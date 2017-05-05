package net.corda.explorer.formatters

import net.corda.core.crypto.Party
import net.corda.core.crypto.commonName
import org.bouncycastle.asn1.x500.X500Name

object PartyNameFormatter {
    val short = object : Formatter<String> {
        override fun format(value: String) = X500Name(value).commonName
    }

    val full = object : Formatter<String> {
        override fun format(value: String): String = value
    }
}
