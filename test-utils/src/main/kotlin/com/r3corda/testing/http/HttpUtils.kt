package com.r3corda.testing.http

import com.r3corda.core.utilities.loggerFor
import okhttp3.*
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * A small set of utilities for making HttpCalls, aimed at demos and tests.
 */
object HttpUtils {
    private val logger = loggerFor<HttpUtils>()
    private val client by lazy {
        OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS).build()
    }

    fun putJson(url: URL, data: String) : Boolean {
        val body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), data)
        return makeRequest(Request.Builder().url(url).header("Content-Type", "application/json").put(body).build())
    }

    fun postJson(url: URL, data: String) : Boolean {
        val body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), data)
        return makeRequest(Request.Builder().url(url).header("Content-Type", "application/json").post(body).build())
    }

    private fun makeRequest(request: Request): Boolean {
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            logger.error("Could not fulfill HTTP request of type ${request.method()} to ${request.url()}. Status Code: ${response.code()}. Message: ${response.body().string()}")
        }
        return response.isSuccessful
    }
}