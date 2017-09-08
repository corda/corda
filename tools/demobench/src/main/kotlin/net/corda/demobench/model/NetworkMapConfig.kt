package net.corda.demobench.model

import net.corda.core.utilities.organisation
import net.corda.core.utilities.WHITESPACE
import org.bouncycastle.asn1.x500.X500Name

open class NetworkMapConfig(val legalName: X500Name, val p2pPort: Int) {
    val key: String = legalName.organisation.toKey()
}

fun String.stripWhitespace() = replace(WHITESPACE, "")
fun String.toKey() = stripWhitespace().toLowerCase()
