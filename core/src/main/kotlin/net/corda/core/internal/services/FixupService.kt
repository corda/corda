package net.corda.core.internal.services

import net.corda.core.crypto.SecureHash
import net.corda.core.node.services.AttachmentFixup
import net.corda.core.node.services.AttachmentId
import net.corda.core.node.services.AttachmentStorage
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.contextLogger
import java.net.JarURLConnection
import java.util.ArrayList
import java.util.jar.JarFile

class FixupService(private val appClassLoader: ClassLoader) : SingletonSerializeAsToken() {
    companion object {
        private val log = contextLogger()
        private const val COMMENT_MARKER = '#'
    }

    private val attachmentFixups: List<AttachmentFixup> by lazy(::loadAttachmentFixups)

    /**
     * Apply this node's attachment fix-up rules to the given attachment IDs.
     *
     * @param attachmentIds A collection of [AttachmentId]s, e.g. as provided by a transaction.
     * @return The [attachmentIds] with the fix-up rules applied.
     */
    fun fixupAttachmentIds(attachmentIds: Collection<AttachmentId>): Set<AttachmentId> {
        val replacementIds = LinkedHashSet(attachmentIds)
        attachmentFixups.forEach { (source, target) ->
            if (replacementIds.containsAll(source)) {
                replacementIds.removeAll(source)
                replacementIds.addAll(target)
            }
        }
        return replacementIds
    }

    /**
     * Loads the "fixup" rules from all META-INF/Corda-Fixups files.
     * These files have the following format:
     *     <AttachmentId>,<AttachmentId>...=><AttachmentId>,<AttachmentId>,...
     * where each <AttachmentId> is the SHA256 of a CorDapp JAR that
     * [net.corda.core.transactions.TransactionBuilder] will expect to find
     * inside [AttachmentStorage].
     *
     * These rules are for repairing broken CorDapps. A correctly written
     * CorDapp should not require them.
     */
    private fun loadAttachmentFixups(): List<AttachmentFixup> {
        return appClassLoader.getResources("META-INF/Corda-Fixups")
                .asSequence()
                .mapNotNull { it.openConnection() as? JarURLConnection }
                .filter { isValidFixup(it.jarFile) }
                .flatMapTo(ArrayList()) { fixupConnection ->
                    fixupConnection.inputStream.bufferedReader().useLines { lines ->
                        lines.map { it.substringBefore(COMMENT_MARKER) }.map(String::trim).filterNot(String::isEmpty).map { line ->
                            val tokens = line.split("=>")
                            require(tokens.size == 2) {
                                "Invalid fix-up line '$line' in '${fixupConnection.jarFile.name}'"
                            }
                            val source = parseIds(tokens[0])
                            require(source.isNotEmpty()) {
                                "Forbidden empty list of source attachment IDs in '${fixupConnection.jarFile.name}'"
                            }
                            val target = parseIds(tokens[1])
                            Pair(source, target)
                        }.toList().asSequence()
                    }
                }
    }

    private fun isValidFixup(jarFile: JarFile): Boolean {
        return jarFile.entries().asSequence().all { it.name.startsWith("META-INF/") }.also { isValid ->
            if (!isValid) {
                log.warn("FixUp '{}' contains files outside META-INF/ - IGNORING!", jarFile.name)
            }
        }
    }

    private fun parseIds(ids: String): Set<AttachmentId> {
        return ids.split(",").map(String::trim)
                .filterNot(String::isEmpty)
                .mapTo(LinkedHashSet(), SecureHash.Companion::create)
    }
}
