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
import com.atlassian.jira.rest.client.api.domain.input.TransitionInput
import net.corda.core.utilities.contextLogger
import org.slf4j.Logger

abstract class JiraClient(protected val restClient: JiraRestClient, protected val projectCode: String) {
    companion object {
        val logger = contextLogger()

        const val DONE_TRANSITION_KEY = "Done"
        const val START_TRANSITION_KEY = "Start Progress"
        const val STOP_TRANSITION_KEY = "Stop Progress"

    }

    // The JIRA project must have a Request ID and reject reason field, and the Task issue type.
    protected val requestIdField: Field = requireNotNull(restClient.metadataClient.fields.claim().find { it.name == "Request ID" }) { "Request ID field not found in JIRA '$projectCode'" }
    protected val taskIssueType: IssueType = requireNotNull(restClient.metadataClient.issueTypes.claim().find { it.name == "Task" }) { "Task issue type field not found in JIRA '$projectCode'" }
    protected val rejectReasonField: Field = requireNotNull(restClient.metadataClient.fields.claim().find { it.name == "Reject Reason" }) { "Reject Reason field not found in JIRA '$projectCode'" }

    private val transitions = mutableMapOf<String, Int>()

    fun getApprovedRequests(): List<ApprovedRequest> {
        val issues = restClient.searchClient.searchJql("project = $projectCode AND status = Approved").claim().issues
        return issues.mapNotNull { issue ->
            val requestId = requireNotNull(issue.getField(requestIdField.id)?.value?.toString()) { "Error processing request '${issue.key}' : RequestId cannot be null." }
            // Issue retrieved via search doesn't contain change logs.
            val fullIssue = restClient.issueClient.getIssue(issue.key, listOf(IssueRestClient.Expandos.CHANGELOG)).claim()
            val approvedBy = fullIssue.changelog?.last { it.items.any { it.field == "status" && it.toString == "Approved" } }
            ApprovedRequest(requestId, approvedBy?.author?.displayName ?: "Unknown")
        }
    }

    fun getRejectedRequests(): List<RejectedRequest> {
        val issues = restClient.searchClient.searchJql("project = $projectCode AND status = Rejected").claim().issues
        return issues.mapNotNull { issue ->
            val requestId = requireNotNull(issue.getField(requestIdField.id)?.value?.toString()) { "Error processing request '${issue.key}' : RequestId cannot be null." }
            val rejectedReason = issue.getField(rejectReasonField.id)?.value?.toString()
            // Issue retrieved via search doesn't contain comments.
            val fullIssue = restClient.issueClient.getIssue(issue.key, listOf(IssueRestClient.Expandos.CHANGELOG)).claim()
            val rejectedBy = fullIssue.changelog?.last { it.items.any { it.field == "status" && it.toString == "Rejected" } }
            RejectedRequest(requestId, rejectedBy?.author?.displayName ?: "Unknown", rejectedReason)
        }
    }

    fun updateRejectedRequest(requestId: String) {
        val issue = requireNotNull(getIssueById(requestId)) { "Issue with the `request ID` = $requestId does not exist." }
        // Move status to in progress.
        restClient.issueClient.transition(issue, TransitionInput(getTransitionId(START_TRANSITION_KEY, issue))).fail { logger.error("Error processing request '${issue.key}' : Exception when transiting JIRA status.", it) }.claim()
        // Move status to stopped.
        restClient.issueClient.transition(issue, TransitionInput(getTransitionId(STOP_TRANSITION_KEY, issue))).fail { logger.error("Error processing request '${issue.key}' : Exception when transiting JIRA status.", it) }.claim()
        restClient.issueClient.addComment(issue.commentsUri, Comment.valueOf("Request cancelled by doorman.")).claim()

    }

    protected fun getIssueById(requestId: String): Issue? {
        // Jira only support ~ (contains) search for custom textfield.
        return restClient.searchClient.searchJql("'Request ID' ~ $requestId").claim().issues.firstOrNull()
    }

    protected fun getTransitionId(transitionKey: String, issue: Issue): Int {
        return transitions.computeIfAbsent(transitionKey, { key ->
            restClient.issueClient.getTransitions(issue.transitionsUri).claim().single { it.name == key }.id
        })
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
