package net.corda.notarydemo

import com.google.common.net.HostAndPort
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

fun main(args: Array<String>) {
    val api = NotaryDemoClientApi(HostAndPort.fromString("localhost:10003"))
    api.startNotarisation()
}

/** Interface for using the notary demo API from a client. */
private class NotaryDemoClientApi(val hostAndPort: HostAndPort) {
    companion object {
        private val API_ROOT = "api/notarydemo"
        private val TRANSACTION_COUNT = 10
    }

    /** Makes a call to the demo api to start transaction notarisation. */
    fun startNotarisation() {
        val request = buildRequest()
        val response = buildClient().newCall(request).execute()
        println(response.body().string())
        require(response.isSuccessful)
    }

    private fun buildRequest() = Request.Builder().url("http://$hostAndPort/$API_ROOT/notarise/$TRANSACTION_COUNT").build()
    private fun buildClient() = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
}
