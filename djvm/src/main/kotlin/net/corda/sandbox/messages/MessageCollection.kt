package net.corda.sandbox.messages

import net.corda.sandbox.references.MemberInformation

/**
 * Collection of captured problems and messages, grouped by class and member. The collection also handles de-duplication
 * of entries and keeps track of how many messages have been recorded at each severity level.
 *
 * @property minimumSeverity Only record messages of this severity or higher.
 * @property prefixFilters Only record messages where the originating class name matches one of the provided prefixes.
 * If none are provided, all messages will be reported.
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
class MessageCollection(
        private val minimumSeverity: Severity = Severity.INFORMATIONAL,
        private val prefixFilters: List<String> = emptyList()
) {

    private val seenEntries = mutableSetOf<String>()

    private val classMessages = mutableMapOf<String, MutableList<Message>>()

    private val memberMessages = mutableMapOf<String, MutableList<Message>>()

    private var cachedEntries: List<Message>? = null

    /**
     * Add a message to the collection.
     */
    fun add(message: Message) {
        if (message.severity < minimumSeverity) {
            return
        }
        if (prefixFilters.isNotEmpty() && !prefixFilters.any { message.location.className.startsWith(it) }) {
            return
        }
        val location = message.location
        val key = "${location.className}:${location.memberName}:${location.lineNumber}:${message.message}"
        if (seenEntries.contains(key)) {
            return
        }
        seenEntries.add(key)
        when {
            location.memberName.isBlank() ->
                messagesFor(location.className).add(message)
            else ->
                messagesFor(location.className, location.memberName, location.signature).add(message)
        }
        cachedEntries = null
    }

    /**
     * Add a list of messages to the collection.
     */
    fun addAll(messages: List<Message>) {
        for (message in messages) {
            add(message)
        }
    }

    /**
     * Get all recorded messages for a given class.
     */
    fun messagesFor(className: String) =
            classMessages.getOrPut(className) { mutableListOf() }

    /**
     * Get all recorded messages for a given class member.
     */
    fun messagesFor(className: String, memberName: String, signature: String) =
            memberMessages.getOrPut("$className.$memberName:$signature") { mutableListOf() }

    /**
     * Get all recorded messages for a given class or class member.
     */
    fun messagesFor(member: MemberInformation) = when {
        member.memberName.isBlank() -> messagesFor(member.className)
        else -> messagesFor(member.className, member.memberName, member.signature)
    }

    /**
     * Check whether the collection of messages is empty.
     */
    fun isEmpty() = count == 0

    /**
     * Check whether the collection of messages is non-empty.
     */
    fun isNotEmpty() = !isEmpty()

    /**
     * Get a consistently sorted list of messages of severity greater than or equal to [minimumSeverity].
     */
    fun sorted(): List<Message> {
        val entries = cachedEntries
        if (entries != null) {
            return entries
        }
        cachedEntries = this
                .all
                .filter { it.severity >= minimumSeverity }
                .distinctBy { "${it.severity} ${it.location.className}.${it.location.memberName} ${it.message}" }
                .sortedWith(compareBy(
                        { it.severity.precedence },
                        { it.location.sourceFile },
                        { it.location.lineNumber },
                        { "${it.location.className}${it.location.memberName}" }
                ))
        return cachedEntries!!
    }

    /**
     * The total number of messages that have been recorded.
     */
    val count: Int
        get() = sorted().count()

    /**
     * The total number of errors that have been recorded.
     */
    val errorCount: Int
        get() = sorted().count { it.severity == Severity.ERROR }

    /**
     * The total number of warnings that have been recorded.
     */
    val warningCount: Int
        get() = sorted().count { it.severity == Severity.WARNING }

    /**
     * The total number of information messages that have been recorded.
     */
    val infoCount: Int
        get() = sorted().count { it.severity == Severity.INFORMATIONAL }

    /**
     * The total number of trace messages that have been recorded.
     */
    val traceCount: Int
        get() = sorted().count { it.severity == Severity.TRACE }

    /**
     * The breakdown of numbers of messages per severity level.
     */
    val statistics: Map<Severity, Int>
        get() = mapOf(
                Severity.TRACE to traceCount,
                Severity.INFORMATIONAL to infoCount,
                Severity.WARNING to warningCount,
                Severity.ERROR to errorCount
        )

    /**
     * Get a list of all the messages and messages that have been recorded, across all classes and class members.
     */
    private val all: List<Message>
        get() =
            mutableListOf<Message>().apply {
                addAll(classMessages.filter { it.value.isNotEmpty() }.flatMap { it.value })
                addAll(memberMessages.filter { it.value.isNotEmpty() }.flatMap { it.value })
            }

}
