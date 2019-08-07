package com.r3.corda.tracelog

object TagExtractor {
    private val timestampTag = Tag("^\\[[A-Z]+[ ]*\\] ([0-9]+-[0-9]+-[0-9]+T[0-9]+:[0-9]+:[0-9]+[,.][0-9]+Z)".toRegex())
    private val flowIdTag = Tag("flow(Id|-id)=([a-z0-9-]+)".toRegex())
    private val statementTags = "Z [A-Za-z]+\\((.*)\\)$".toRegex()

    private class Tag(private val regex: Regex) {
        fun find(message: String): String? = regex.find(message)?.groupValues?.last()
    }

    private val map = mapOf("timestamp" to timestampTag, "flowId" to flowIdTag)

    fun extractFlowId(message: String): String? = flowIdTag.find(message)

    fun extractTags(message: String): Map<String, String> = map
            .map { (k, v) -> k to v.find(message) }
            .filter { (_, v) -> v != null }
            .map { (k, v) -> k to v!! }
            .toMap()

    fun extractTimestamp(message: String): Timestamp? = timestampTag.find(message)?.let { Timestamp(it) }

    fun extractTagsFromStatement(message: String): Map<String, String> {
        val tagSection = statementTags.findAll(message).lastOrNull()?.groupValues?.getOrNull(1)?.trim() ?: ""
        val tags = tagSection
                .split(";")
                .filter { '=' in it }
                .map { tag -> tag.split('=', limit = 2).let { it[0] to it[1] } }
                .toMap()
        return if (tags["id"]?.startsWith("N-") == true) {
            val sessionId = tags.getValue("id").let { it.substring(4, it.indexOf('-', 5)) }
            tags + mapOf("messageSession" to sessionId)
        } else {
            tags
        }
    }
}