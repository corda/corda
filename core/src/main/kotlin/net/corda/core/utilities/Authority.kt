package net.corda.core.utilities

import java.net.URI

data class Authority(val host: String, val port: Int) {
    init {
        require(port in (0..0xffff)) { invalidPortFormat.format(port) }
    }

    override fun toString() = if (':' in host) "[$host]:$port" else "$host:$port"
}

fun String.parseAuthority() = run {
    val uri = URI(null, this, null, null, null)
    require(uri.host != null) { unparseableAddressFormat.format(this) }
    require(uri.port != -1) { missingPortFormat.format(this) }
    Authority(bracketedHost.matchEntire(uri.host)?.groupValues?.get(1) ?: uri.host, uri.port)
}

private val bracketedHost = "\\[(.*)]".toRegex()
internal val invalidPortFormat = "Invalid port: %s"
internal val unparseableAddressFormat = "Unparseable address: %s"
internal val missingPortFormat = "Missing port: %s"
