package net.corda.demobench.model

open class NetworkMapConfig(legalName: String, artemisPort: Int) {

    private var keyValue: String = toKey(legalName)
    val key : String
        get() { return keyValue }

    private var legalNameValue: String = legalName
    val legalName : String
        get() { return legalNameValue }

    private var artemisPortValue: Int = artemisPort
    val artemisPort : Int
        get() { return artemisPortValue }

}

private val WHITESPACE = "\\s++".toRegex()

fun toKey(value: String): String {
    return value.replace(WHITESPACE, "").toLowerCase()
}
