package net.corda.healthsurvey.collectors

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import net.corda.healthsurvey.cli.Console.yellow
import net.corda.healthsurvey.output.Report
import net.rcarz.jiraclient.BasicCredentials
import net.rcarz.jiraclient.JiraClient
import net.rcarz.jiraclient.RestException

class AttachmentUploaderJob(
        private val username: String,
        private val password: String,
        private val ticket: String
) : TrackedCollector("Uploading report to JIRA ticket ${yellow(ticket)} ...") {

    override fun collect(report: Report) {
        try {
            val credentials = BasicCredentials(username, password)
            val client = JiraClient("https://r3-cev.atlassian.net", credentials)
            val issue = client.getIssue(ticket)
            issue.addAttachment(report.path.toFile())
            complete("Uploaded report ${yellow(report.path)} to JIRA ticket ${yellow(ticket)}")
        } catch (throwable: Throwable) {
            fail("Failed to upload attachment. ${throwable.humanisedString()}")
        }
    }

    private fun Throwable.humanisedString(): String {
        val cause = cause
        return when {
            cause != null -> cause.humanisedString()
            this is RestException -> {
                if (this.httpStatusCode == 401) {
                    "Unable to log in to JIRA; bad credentials?"
                } else {
                    try {
                        val response = Gson().fromJson(this.httpResult, RestResponse::class.java)
                        response.errorMessages.first()
                    } catch (_: Exception) {
                        toString().firstLine()
                    }
                }
            }
            else -> toString().firstLine()
        }
    }

    private fun String.firstLine() = this.split("\n").first()

    private class RestResponse(
            @SerializedName("errorMessages") var errorMessages: Array<String>,
            @SerializedName("errors") var errors: Any
    )

}
