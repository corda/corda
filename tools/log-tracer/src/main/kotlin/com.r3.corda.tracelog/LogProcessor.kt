package com.r3.corda.tracelog

import io.opentracing.Span
import io.opentracing.Tracer
import io.opentracing.tag.Tags
import java.nio.file.Files
import java.nio.file.Path

class LogProcessor(
        private val tracer: Tracer,
        private val logPath: Path,
        private val parentSpan: Span,
        private val timeWindow: TimeWindow
) {
    companion object {
        private const val maxTagValueLength = 1024

        private val sessions = mutableMapOf<String, LogSpan?>()
        private val sessionStartTimes = mutableMapOf<String, Timestamp>()
        private val sessionEndTimes = mutableMapOf<String, Timestamp>()
        private val flowStartTimes = mutableMapOf<String, Timestamp>()
        private val flowEndTimes = mutableMapOf<String, Timestamp>()

        fun finalizeSessions() = sessions.values.filterNotNull().forEach(LogSpan::finish)
    }

    data class Flow(val id: String, val name: String)

    private val statementPatterns = listOf(
            "CryptoService(", "Session(", "Message(", "Attachment(", "Flow(", "Transaction(", "Contract(",
            "State(", "States(", "Party("
    )

    private val reStatementName = "([A-Za-z]+)\\(.*\\)$".toRegex()
    private val flows = mutableMapOf<String, LogSpan>()
    private val flowToMessageSessionMap = mutableMapOf<String, String>()
    private val parentLogSpan: LogSpan? = LogSpan(parentSpan, "Root", timeWindow.start, timeWindow.end)

    val flowNames: List<Flow>
        get() = flows.map { Flow(it.key, it.value.name) }.toList()

    fun findSessions(): LogProcessor = this.apply {
        Files.lines(logPath).forEach { findSessions(it) }
        flowToMessageSessionMap.values.distinct().forEach { sessionId ->
            flowToMessageSessionMap.filter { it.value == sessionId }.map { it.key }.forEach { flowId ->
                val (start, end) = flowStartTimes.getValue(flowId) to flowEndTimes.getValue(flowId)
                if (sessionStartTimes[sessionId] == null || sessionStartTimes[sessionId]!! > start) {
                    sessionStartTimes[sessionId] = start
                }
                if (sessionEndTimes[sessionId] == null || sessionEndTimes[sessionId]!! < end) {
                    sessionEndTimes[sessionId] = end
                }
            }
        }
    }

    fun start(): LogProcessor = this.apply {
        Files.lines(logPath).forEach { matchLine(it) }
        flows.forEach { (_, span) -> span.finish() }
    }

    private class LogSpan(internal val span: Span, val name: String, val startTime: Timestamp, endTime: Timestamp? = null) {
        private var hasFailed = false

        var lastTimestamp: Timestamp? = endTime
            private set

        fun markAsFailed() = Tags.ERROR.set(span, true)

        fun log(log: Map<String, String>) {
            val timestamp = Timestamp(log["timestamp"] ?: return)
            lastTimestamp = lastTimestamp ?: timestamp
            if (timestamp > lastTimestamp!!) {
                lastTimestamp = timestamp
            }
            span.log(timestamp.timestampInMicroseconds, log - "timestamp")
        }

        fun finish() {
            val lastTimestamp = lastTimestamp ?: return
            finish(lastTimestamp)
        }

        fun finish(timestamp: Timestamp) {
            if (hasFailed) return
            hasFailed = true
            span.finish(timestamp.timestampInMicroseconds)
        }
    }

    private fun span(timestamp: Timestamp, name: String, tags: Map<String, String>, parent: Span? = null, endTime: Timestamp? = null): LogSpan {
        var span = tracer.buildSpan(name).withStartTimestamp(timestamp.timestampInMicroseconds)
        if (parent != null) {
            span = span.asChildOf(parent)
        }
        for ((k, v) in tags - "timestamp") {
            span = span.withTag(k, v)
        }
        return LogSpan(span.start(), name, timestamp, endTime)
    }

    private fun matchLine(line: String) {
        val tags = TagExtractor.extractTags(line) + TagExtractor.extractTagsFromStatement(line)
        when {
            "Flow(action=start" in line -> startFlow(tags)
            containsStatement(line) -> logStatement(line, tags)
            "[TRACE" in line -> logStatement(line, tags, severity = "TRACE")
            "[INFO" in line -> logStatement(line, tags, severity = "INFO")
            "[WARN" in line -> logStatement(line, tags, severity = "WARN")
            "[ERROR" in line -> logStatement(line, tags, severity = "ERROR")
        }
    }

    private fun startFlow(tags: Map<String, String>) {
        val timestamp = tags["timestamp"] ?: return
        val flowId = tags["flowId"] ?: return
        val logic = tags["logic"] ?: return
        val sessionId = (flowToMessageSessionMap[flowId] ?: "Unknown")
        val sessionTags = tagsOf("messageSession" to sessionId)
        val sessionStart = sessionStartTimes[sessionId] ?: timeWindow.start
        val sessionEnd = sessionEndTimes[sessionId] ?: timeWindow.end
        val sessionSpan = sessions[sessionId] ?: if (sessionId == "Unknown") { parentLogSpan } else { span(sessionStart, "Session $sessionId", emptyMap(), parentSpan, sessionEnd) }
        sessions[sessionId] = if (sessionSpan == parentLogSpan) { null } else { sessionSpan }
        val span = span(Timestamp(timestamp), "$logic - ${logPath.fileName}", tags + sessionTags, sessionSpan?.span)
        flows[flowId] = span
    }

    private fun findSession(line: String, mdcTags: Map<String, String>) {
        val tags = mdcTags + TagExtractor.extractTagsFromStatement(line)
        val flowId = tags["flowId"]
        val sessionId = tags["messageSession"]
        val timestamp = tags["timestamp"]
        if (flowId != null && timestamp != null) {
            val ts = Timestamp(timestamp)
            if (flowStartTimes[flowId] == null || flowStartTimes[flowId]!! > ts) {
                flowStartTimes[flowId] = ts
            }
            if (flowEndTimes[flowId] == null || flowEndTimes[flowId]!! < ts) {
                flowEndTimes[flowId] = ts
            }
        }
        if (flowId != null && sessionId != null) {
            flowToMessageSessionMap[flowId] = sessionId
        }
    }

    private fun logStatement(line: String, tags: Map<String, String>, severity: String = "INFO") {
        val statement = reStatementName.find(line)?.groupValues?.getOrNull(1)
        val flowId = TagExtractor.extractFlowId(line)
        val sessionId = tags["messageSession"]
        val span = when {
            flowId != null -> flows[flowId] ?: return
            sessionId != null -> sessions[sessionId] ?: return
            else -> parentLogSpan ?: return
        }
        val timestamp = tags["timestamp"] ?: return
        val message = line.substringAfter("Z ").substringBeforeLast('{').trim()
        val logTags = tags + tagsOf("logFile" to "${logPath.fileName}")
        if (!containsStatement(line) && message.isNotEmpty() && severity in listOf("WARN", "ERROR")) {
            span.log(tagsOf("message" to message, "severity" to severity, "timestamp" to timestamp) + logTags)
        } else if (containsStatement(line)) {
            span.log(linkedMapOf("event" to (statement ?: condenseString(message)), "timestamp" to timestamp) + logTags)
        }
        if ("action=propagate_error" in line || severity == "ERROR") {
            span.markAsFailed()
        }
    }

    private fun containsStatement(line: String) = statementPatterns.any { it in line }

    private fun findSessions(line: String) = TagExtractor.extractTags(line).apply { findSession(line, this) }

    private fun tagsOf(vararg pairs: Pair<String, String>) = pairs.map { it.first to condenseString(it.second) }.toMap()

    private fun condenseString(message: String) = when {
        message.length > maxTagValueLength -> message.substring(0, maxTagValueLength)
        else -> message
    }
}