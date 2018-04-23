/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.testing.http

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.client.jackson.JacksonSupport
import net.corda.core.utilities.NetworkHostAndPort
import java.net.URL

class HttpApi(val root: URL, val mapper: ObjectMapper = defaultMapper) {
    /**
     * Send a PUT with a payload to the path on the API specified.
     *
     * @param data String values are assumed to be valid JSON. All other values will be mapped to JSON.
     */
    fun putJson(path: String, data: Any = Unit) = HttpUtils.putJson(URL(root, path), toJson(data))

    /**
     * Send a POST with a payload to the path on the API specified.
     *
     * @param data String values are assumed to be valid JSON. All other values will be mapped to JSON.
     */
    fun postJson(path: String, data: Any = Unit) = HttpUtils.postJson(URL(root, path), toJson(data))

    /**
     * Send a POST with a payload to the path on the API specified.
     *
     * @param data String payload
     */
    fun postPlain(path: String, data: String = "") = HttpUtils.postPlain(URL(root, path), data)

    /**
     * Send a GET request to the path on the API specified.
     */
    inline fun <reified T : Any> getJson(path: String, params: Map<String, String> = mapOf()): T {
        return HttpUtils.getJson(URL(root, path), params, mapper)
    }

    private fun toJson(any: Any) = any as? String ?: HttpUtils.defaultMapper.writeValueAsString(any)

    companion object {
        fun fromHostAndPort(hostAndPort: NetworkHostAndPort, base: String, protocol: String = "http", mapper: ObjectMapper = defaultMapper): HttpApi {
            return HttpApi(URL("$protocol://$hostAndPort/$base/"), mapper)
        }

        private val defaultMapper: ObjectMapper by lazy { JacksonSupport.createNonRpcMapper() }
    }
}
