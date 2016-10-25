package com.r3corda.testing.http

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.net.HostAndPort
import java.net.URL

class HttpApi(val root: URL) {
    fun putJson(path: String, data: Any) = HttpUtils.putJson(URL(root, path), toJson(data))
    fun postJson(path: String, data: Any) = HttpUtils.postJson(URL(root, path), toJson(data))

    private fun toJson(any: Any) = ObjectMapper().writeValueAsString(any)

    companion object {
        fun fromHostAndPort(hostAndPort: HostAndPort, base: String, protocol: String = "http"): HttpApi
                = HttpApi(URL("$protocol://$hostAndPort/$base/"))
    }
}