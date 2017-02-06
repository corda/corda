package net.corda.demobench.model

open class NetworkMapConfig(val legalName: String, val artemisPort: Int) {

    private var keyValue = toKey(legalName)
    val key: String get() = keyValue

}

private val WHITESPACE = "\\s++".toRegex()

fun toKey(value: String) = value.replace(WHITESPACE, "").toLowerCase()
