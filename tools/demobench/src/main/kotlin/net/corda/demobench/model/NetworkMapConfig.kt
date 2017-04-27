package net.corda.demobench.model

import net.corda.core.crypto.commonName
import org.bouncycastle.asn1.x500.X500Name

open class NetworkMapConfig(val legalName: X500Name, val p2pPort: Int) {

    val key: String = legalName.commonName.toKey()

}

private val WHITESPACE = "\\s++".toRegex()

fun String.stripWhitespace() = this.replace(WHITESPACE, "")
fun String.toKey() = this.stripWhitespace().toLowerCase()
