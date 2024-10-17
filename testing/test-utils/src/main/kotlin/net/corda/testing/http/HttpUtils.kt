package net.corda.testing.http

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * A small set of utilities for making HttpCalls, aimed at demos and tests.
 */
object HttpUtils {
    private val client by lazy {
        OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS).build()
    }

    val defaultMapper: ObjectMapper by lazy {
        net.corda.client.jackson.JacksonSupport.createNonRpcMapper()
    }

    fun putJson(url: URL, data: String) {
        val body = data.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        makeRequest(Request.Builder().url(url).header("Content-Type", "application/json").put(body).build())
    }

    fun postJson(url: URL, data: String) {
        val body = data.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        makeRequest(Request.Builder().url(url).header("Content-Type", "application/json").post(body).build())
    }

    fun postPlain(url: URL, data: String) {
        val body = data.toRequestBody("text/plain; charset=utf-8".toMediaTypeOrNull())
        makeRequest(Request.Builder().url(url).post(body).build())
    }

    inline fun <reified T : Any> getJson(url: URL, params: Map<String, String> = mapOf(), mapper: ObjectMapper = defaultMapper): T {
        val paramString = if (params.isEmpty()) "" else "?" + params.map { "${it.key}=${it.value}" }.joinToString("&")
        val parameterisedUrl = URL(url.toExternalForm() + paramString)
        return mapper.readValue(parameterisedUrl, T::class.java)
    }

    private fun makeRequest(request: Request) {
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("${request.method} to ${request.url} returned a ${response.code}: ${response.body?.string()}")
        }
    }
}
