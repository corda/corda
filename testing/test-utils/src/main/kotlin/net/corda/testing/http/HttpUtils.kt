package net.corda.testing.http

import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
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

    fun putJson(url: URL, data: String) {
        val body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), data)
        makeRequest(Request.Builder().url(url).header("Content-Type", "application/json").put(body).build())
    }

    fun postJson(url: URL, data: String) {
        val body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), data)
        makeRequest(Request.Builder().url(url).header("Content-Type", "application/json").post(body).build())
    }

    fun postPlain(url: URL, data: String) {
        val body = RequestBody.create(MediaType.parse("text/plain; charset=utf-8"), data)
        makeRequest(Request.Builder().url(url).post(body).build())
    }

    private fun makeRequest(request: Request) {
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("${request.method()} to ${request.url()} returned a ${response.code()}: ${response.body()?.string()}")
        }
    }
}
