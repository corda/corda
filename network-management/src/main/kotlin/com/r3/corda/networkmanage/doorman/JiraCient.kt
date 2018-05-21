/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.doorman

import com.atlassian.jira.rest.client.api.IssueRestClient
import com.atlassian.jira.rest.client.api.JiraRestClient
import com.atlassian.jira.rest.client.api.domain.Comment
import com.atlassian.jira.rest.client.api.domain.Field
import com.atlassian.jira.rest.client.api.domain.Issue
import com.atlassian.jira.rest.client.api.domain.IssueType
import com.atlassian.jira.rest.client.api.domain.input.IssueInput
import com.atlassian.jira.rest.client.api.domain.input.TransitionInput
import com.r3.corda.networkmanage.doorman.JiraConstant.DONE_TRANSITION_KEY
import com.r3.corda.networkmanage.doorman.JiraConstant.START_TRANSITION_KEY
import com.r3.corda.networkmanage.doorman.JiraConstant.STOP_TRANSITION_KEY
import net.corda.core.internal.ConcurrentBox
import net.corda.core.utilities.contextLogger
import org.slf4j.Logger
import java.io.InputStream

/**
 *  The Jira client class manages concurrent access to the [JiraRestClient] to ensure atomic read and write for some operations to prevent race condition.
 */
abstract class JiraClient(restClient: JiraRestClient, protected val projectCode: String) {
    companion object {
        val logger = contextLogger()
    }

    private val restClientLock = ConcurrentBox(restClient)

    // The JIRA project must have a Request ID and reject reason field, and the Task issue type.
    protected val requestIdField: Field = requireNotNull(restClient.metadataClient.fields.claim().find { it.name == "Request ID" }) { "Request ID field not found in JIRA '$projectCode'" }
    protected val taskIssueType: IssueType = requireNotNull(restClient.metadataClient.issueTypes.claim().find { it.name == "Task" }) { "Task issue type field not found in JIRA '$projectCode'" }
    private val rejectReasonField: Field = requireNotNull(restClient.metadataClient.fields.claim().find { it.name == "Reject Reason" }) { "Reject Reason field not found in JIRA '$projectCode'" }

    private val transitions = mutableMapOf<String, Int>()

    fun getApprovedRequests(): List<ApprovedRequest> {
        return restClientLock.concurrent {
            val issues = searchClient.searchJql("project = $projectCode AND status = Approved").claim().issues
            issues.mapNotNull { issue ->
                val requestId = requireNotNull(issue.getField(requestIdField.id)?.value?.toString()) { "Error processing request '${issue.key}' : RequestId cannot be null." }
                // Issue retrieved via search doesn't contain change logs.
                val fullIssue = issueClient.getIssue(issue.key, listOf(IssueRestClient.Expandos.CHANGELOG)).claim()
                val approvedBy = fullIssue.changelog?.last { it.items.any { it.field == "status" && it.toString == "Approved" } }
                ApprovedRequest(requestId, approvedBy?.author?.displayName ?: "Unknown")
            }
        }
    }

    fun getRejectedRequests(): List<RejectedRequest> {
        return restClientLock.concurrent {
            val issues = searchClient.searchJql("project = $projectCode AND status = Rejected").claim().issues
            issues.mapNotNull { issue ->
                val requestId = requireNotNull(issue.getField(requestIdField.id)?.value?.toString()) { "Error processing request '${issue.key}' : RequestId cannot be null." }
                val rejectedReason = issue.getField(rejectReasonField.id)?.value?.toString()
                // Issue retrieved via search doesn't contain comments.
                val fullIssue = issueClient.getIssue(issue.key, listOf(IssueRestClient.Expandos.CHANGELOG)).claim()
                val rejectedBy = fullIssue.changelog?.last { it.items.any { it.field == "status" && it.toString == "Rejected" } }
                RejectedRequest(requestId, rejectedBy?.author?.displayName ?: "Unknown", rejectedReason)
            }
        }
    }

    fun updateRejectedRequest(requestId: String) {
        restClientLock.exclusive {
            val issue = requireNotNull(getIssueById(requestId)) { "Issue with the `request ID` = $requestId does not exist." }
            // Move status to in progress.
            issueClient.transition(issue, TransitionInput(getTransitionId(START_TRANSITION_KEY, issue))).fail { logger.error("Error processing request '${issue.key}' : Exception when transiting JIRA status.", it) }.claim()
            // Move status to stopped.
            issueClient.transition(issue, TransitionInput(getTransitionId(STOP_TRANSITION_KEY, issue))).fail { logger.error("Error processing request '${issue.key}' : Exception when transiting JIRA status.", it) }.claim()
            issueClient.addComment(issue.commentsUri, Comment.valueOf("Request cancelled by doorman.")).claim()
        }
    }

    private fun JiraRestClient.getIssueById(requestId: String): Issue? {
        // Jira only support ~ (contains) search for custom textfield.
        return searchClient.searchJql("'Request ID' ~ $requestId").claim().issues.firstOrNull()
    }

    private fun getTransitionId(transitionKey: String, issue: Issue): Int {
        return transitions.computeIfAbsent(transitionKey, { key ->
            restClientLock.concurrent {
                issueClient.getTransitions(issue.transitionsUri).claim().single { it.name == key }.id
            }
        })
    }

    protected fun createJiraTicket(requestId: String, issue: IssueInput, attachment: Pair<String, InputStream>? = null) {
        restClientLock.exclusive {
            if (getIssueById(requestId) != null) {
                logger.warn("There is already a ticket corresponding to request Id $requestId, not creating a new one.")
                return
            }
            // This will block until the issue is created.
            issueClient.createIssue(issue).fail { logger.error("Exception when creating JIRA issue for request: '$requestId'.", it) }.claim()
            attachment?.let { addAttachment(requestId, it) }
        }
    }

    fun transitRequestStatusToDone(requestId: String, attachment: Pair<String, InputStream>? = null) {
        restClientLock.exclusive {
            val issue = requireNotNull(getIssueById(requestId)) { "Cannot find the JIRA ticket `request ID` = $requestId" }
            issueClient.transition(issue, TransitionInput(getTransitionId(DONE_TRANSITION_KEY, issue))).fail { logger.error("Exception when transiting JIRA status.", it) }.claim()
            attachment?.let { addAttachment(requestId, it) }
        }
    }

    private fun JiraRestClient.addAttachment(requestId: String, attachment: Pair<String, InputStream>) {
        val createdIssue = checkNotNull(getIssueById(requestId)) { "Missing the JIRA ticket for the request ID: $requestId" }
        issueClient.addAttachment(createdIssue.attachmentsUri, attachment.second, attachment.first)
                .fail { logger.error("Error processing request '${createdIssue.key}' : Exception when uploading attachment to JIRA.", it) }.claim()
    }
}

data class ApprovedRequest(val requestId: String, val approvedBy: String)

data class RejectedRequest(val requestId: String, val rejectedBy: String, val reason: String?)

inline fun <T : Any> Iterable<T>.forEachWithExceptionLogging(logger: Logger, action: (T) -> Unit) {
    for (element in this) {
        try {
            action(element)
        } catch (e: Exception) {
            logger.error("Error while processing an element: $element", e)
        }
    }
}

object JiraConstant{
    const val DONE_TRANSITION_KEY = "Done"
    const val START_TRANSITION_KEY = "Start Progress"
    const val STOP_TRANSITION_KEY = "Stop Progress"
}
