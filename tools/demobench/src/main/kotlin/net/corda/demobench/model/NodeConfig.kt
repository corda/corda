package net.corda.demobench.model

class NodeConfig(name: String, p2pPort: Int, artemisPort: Int, webPort: Int) {

    private var keyValue: String = toKey(name)
    val key : String
        get() { return keyValue }

    private var nameValue: String = name
    val name : String
        get() { return nameValue }

    private var p2pPortValue: Int = p2pPort
    val p2pPort : Int
        get() { return p2pPortValue }

    private var artemisPortValue: Int = artemisPort
    val artemisPort : Int
        get() { return artemisPortValue }

    private var webPortValue: Int = webPort
    val webPort : Int
        get() { return webPortValue }

    private fun toKey(value: String): String {
        return value.replace("\\s++", "").toLowerCase()
    }

}
