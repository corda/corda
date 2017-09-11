package net.corda.demobench.model

import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.WHITESPACE

open class NetworkMapConfig(val legalName: CordaX500Name, val p2pPort: Int) {
    val key: String = legalName.organisation.toKey()
}

fun String.stripWhitespace() = replace(WHITESPACE, "")
fun String.toKey() = stripWhitespace().toLowerCase()
