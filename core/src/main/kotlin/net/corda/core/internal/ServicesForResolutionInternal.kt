package net.corda.core.internal

import net.corda.core.crypto.SecureHash
import net.corda.core.node.ServicesForResolution

interface ServicesForResolutionInternal : ServicesForResolution {

    /**
     * If an attachment is signed with a public key with one of these hashes, it will automatically be trusted.
     */
    val whitelistedKeysForAttachments: Collection<SecureHash>
}