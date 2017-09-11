package net.corda.demobench.model

import net.corda.core.identity.CordaX500Name

open class NetworkMapConfig(val legalName: CordaX500Name, val p2pPort: Int) {
    val key: String = legalName.organisation.toKey()
}

fun String.stripWhitespace() = String(this.filter { !it.isWhitespace() }.toCharArray())
fun String.toKey() = stripWhitespace().toLowerCase()
