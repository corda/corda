package net.corda.core.transactions

import net.corda.core.crypto.SecureHash

/**
 * A description of what extra attachments should be trusted by this node
 */
sealed class ExtraTrustedAttachments {

    /**
     * Public key hashes that are whitelisted. Attachments signed by any of these keys will be trusted. This is populated through node
     * configuration.
     */
    data class WhitelistedKeys(val keys: Collection<SecureHash>): ExtraTrustedAttachments()

    /**
     * All attachments should be trusted. This option is only set when the attachments are loaded via the DJVM.
     */
    object TrustAllAttachments: ExtraTrustedAttachments()

    companion object {
        fun default(): ExtraTrustedAttachments {
            return ExtraTrustedAttachments.WhitelistedKeys(listOf())
        }
    }
}