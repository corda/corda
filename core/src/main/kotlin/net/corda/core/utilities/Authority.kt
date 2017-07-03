package net.corda.core.utilities

import java.net.URI

data class Authority(val host: String, val port: Int) {
    override fun toString() = if (':' in host) "[$host]:$port" else "$host:$port"
}

private val bracketedHost = "\\[(.*)]".toRegex()
fun String.parseAuthority() = run {
    val uri = URI(null, this, null, null, null)
    uri.host ?: throw IllegalArgumentException("Unparseable authority: $this")
    Authority(bracketedHost.matchEntire(uri.host)?.groupValues?.get(1) ?: uri.host, uri.port)
}
