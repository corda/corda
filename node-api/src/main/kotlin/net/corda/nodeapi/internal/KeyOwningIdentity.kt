package net.corda.nodeapi.internal

import java.util.*

/**
 * A [KeyOwningIdentity] represents an entity that owns a public key.
 */
sealed class KeyOwningIdentity {

    abstract val uuid: UUID?

    /**
     * [NodeLegalIdentity] is used for keys that belong to the node identity.
     */
    object NodeLegalIdentity : KeyOwningIdentity() {
        override fun toString(): String {
            return "NODE_LEGAL_IDENTITY"
        }

        override val uuid: UUID? = null
    }

    /**
     * [ExternalIdentity] is used for keys that have an assigned external UUID.
     */
    data class ExternalIdentity(override val uuid: UUID) : KeyOwningIdentity() {
        override fun toString(): String {
            return uuid.toString()
        }
    }

    companion object {
        fun fromUUID(uuid: UUID?): KeyOwningIdentity {
            return if (uuid != null) {
                ExternalIdentity(uuid)
            } else {
                NodeLegalIdentity
            }
        }
    }
}