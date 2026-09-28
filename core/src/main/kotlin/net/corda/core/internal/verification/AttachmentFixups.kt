package net.corda.core.internal.verification

import net.corda.core.crypto.SecureHash
import net.corda.core.internal.mapNotNull
import net.corda.core.node.services.AttachmentFixup
import net.corda.core.node.services.AttachmentId
import net.corda.core.node.services.AttachmentStorage
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.loggerFor
import java.net.JarURLConnection
import java.net.URL

class AttachmentFixups {
    private val fixupRules = ArrayList<AttachmentFixup>()

    /**
     * Loads the "fixup" rules from all META-INF/Corda-Fixups files.
     * These files have the following format:
     *     <AttachmentId>,<AttachmentId>...=><AttachmentId>,<AttachmentId>,...
     * where each <AttachmentId> is the SHA256 of a CorDapp JAR that [TransactionBuilder] will expect to find inside [AttachmentStorage].
     *
     * These rules are for repairing broken CorDapps. A correctly written CorDapp should not require them.
     */
    fun load(appClassLoader: ClassLoader) {
        for (url in appClassLoader.resources("META-INF/Corda-Fixups")) {
            val connection = toValidFixupResource(url) ?: continue
            connection.inputStream.bufferedReader().lines().use { lines ->
                lines.mapNotNull(::cleanLine).forEach { line ->
                    val tokens = line.split("=>")
                    require(tokens.size == 2) {
                        "Invalid fix-up line '$line' in '${connection.jarFile.name}'"
                    }
                    val sourceIds = parseIds(tokens[0])
                    require(sourceIds.isNotEmpty()) {
                        "Forbidden empty list of source attachment IDs in '${connection.jarFile.name}'"
                    }
                    val targetIds = parseIds(tokens[1])
                    fixupRules += AttachmentFixup(sourceIds, targetIds)
                }
            }
        }
    }

    private fun toValidFixupResource(url: URL): JarURLConnection? {
        val connection = url.openConnection() as? JarURLConnection ?: return null
        val isValid = connection.jarFile.stream().allMatch { it.name.startsWith("META-INF/") }
        if (!isValid) {
            loggerFor<AttachmentFixups>().warn("FixUp '{}' contains files outside META-INF/ - IGNORING!", connection.jarFile.name)
            return null
        }
        return connection
    }

    private fun cleanLine(line: String): String? = line.substringBefore('#').trim().takeIf(String::isNotEmpty)

    private fun parseIds(ids: String): Set<AttachmentId> {
        return ids.splitToSequence(",")
                .map(String::trim)
                .filter { it.isNotEmpty() }
                .mapTo(LinkedHashSet(), SecureHash.Companion::create)
    }

    /**
     * Apply this node's attachment fix-up rules to the given attachment IDs.
     *
     * @param attachmentIds A collection of [AttachmentId]s, e.g. as provided by a transaction.
     * @return The [attachmentIds] with the fix-up rules applied.
     */
    fun fixupAttachmentIds(attachmentIds: Collection<AttachmentId>): Set<AttachmentId> {
        val replacementIds = LinkedHashSet(attachmentIds)
        for ((sourceIds, targetIds) in fixupRules) {
            if (replacementIds.containsAll(sourceIds)) {
                replacementIds.removeAll(sourceIds)
                replacementIds.addAll(targetIds)
            }
        }
        return replacementIds
    }
}
