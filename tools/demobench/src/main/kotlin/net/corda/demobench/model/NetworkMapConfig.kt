package net.corda.demobench.model

open class NetworkMapConfig(val legalName: String, val p2pPort: Int) {

    val key: String = legalName.toKey()

}

private val WHITESPACE = "\\s++".toRegex()

fun String.stripWhitespace() = this.replace(WHITESPACE, "")
fun String.toKey() = this.stripWhitespace().toLowerCase()
