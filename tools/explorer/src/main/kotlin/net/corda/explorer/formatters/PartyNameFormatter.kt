package net.corda.explorer.formatters

import net.corda.core.identity.CordaX500Name

object PartyNameFormatter {
    val short = object : Formatter<CordaX500Name> {
        override fun format(value: CordaX500Name) = value.organisation
    }

    val full = object : Formatter<CordaX500Name> {
        override fun format(value: CordaX500Name): String = value.toString()
    }
}
