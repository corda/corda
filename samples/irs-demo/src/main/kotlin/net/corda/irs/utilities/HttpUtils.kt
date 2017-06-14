package net.corda.irs.utilities

import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * A small set of utilities for making HttpCalls, aimed at demos.
 */
private val client by lazy {
    OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS).build()
}

fun putJson(url: URL, data: String): Boolean {
    val body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), data)
    return makeRequest(Request.Builder().url(url).put(body).build())
}

fun postJson(url: URL, data: String): Boolean {
    val body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), data)
    return makeRequest(Request.Builder().url(url).post(body).build())
}

fun uploadFile(url: URL, file: String): Boolean {
    val body = RequestBody.create(MediaType.parse("text/plain; charset=utf-8"), file)
    return makeRequest(Request.Builder().url(url).post(body).build())
}

private fun makeRequest(request: Request): Boolean {
    val response = client.newCall(request).execute()

    if (!response.isSuccessful) {
        println("Could not fulfill HTTP request. Status Code: ${response.code()}. Message: ${response.body().string()}")
    }
    response.close()
    return response.isSuccessful
}
