package net.corda.core.serialization.internal

import net.corda.core.contracts.Attachment
import java.net.URL

@Suppress("unused")
private class AttachmentsHolderImpl : AttachmentsHolder {
    private val attachments = LinkedHashMap<URL, Pair<URL, Attachment>>()

    override val size: Int get() = attachments.size

    override fun getKey(key: URL): URL? {
        return attachments[key]?.first
    }

    override fun get(key: URL): Attachment? {
        return attachments[key]?.second
    }

    override fun set(key: URL, value: Attachment) {
        attachments[key] = key to value
    }
}
