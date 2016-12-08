package net.corda.attachmentdemo

import com.google.common.net.HostAndPort
import net.corda.testing.http.HttpApi
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Interface for using the attachment demo API from a client.
 */
class AttachmentDemoClientApi(val hostAndPort: HostAndPort) {
    private val api = HttpApi.fromHostAndPort(hostAndPort, apiRoot)

    fun runRecipient(): Boolean {
        return api.postJson("await-transaction")
    }

    fun runSender(otherSide: String): Boolean {
        return api.postJson("$otherSide/send")
    }

    fun getOtherSideKey(): String {
        // TODO: Add getJson to the API utils
        val client = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
        val request = Request.Builder().url("http://$hostAndPort/$apiRoot/other-side-key").build()
        val response = client.newCall(request).execute()
        require(response.isSuccessful) // TODO: Handle more gracefully.
        return response.body().string()
    }

    private companion object {
        private val apiRoot = "api/attachmentdemo"
    }
}
