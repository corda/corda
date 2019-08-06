package net.corda.nodeapi.internal.utilities

import java.util.*

/**
 * A [KeyOwningIdentity] represents an entity that owns a public key.
 */
sealed class KeyOwningIdentity {

    abstract val uuid: UUID?

    /**
     * [NodeIdentity] is used for keys that belong to the node identity.
     */
    object NodeIdentity: KeyOwningIdentity() {
        override fun toString(): String {
            return "NODE_IDENTITY"
        }
        override val uuid: Nothing? = null
    }

    /**
     * [ExternalIdentity] is used for keys that have an assigned external UUID.
     */
    data class ExternalIdentity(override val uuid: UUID): KeyOwningIdentity() {
        override fun toString(): String {
            return uuid.toString()
        }
    }

    companion object {
        fun fromUUID(uuid: UUID?): KeyOwningIdentity {
            return if (uuid != null) {
                ExternalIdentity(uuid)
            } else {
                NodeIdentity
            }
        }

        fun fromString(id: String): KeyOwningIdentity {
            val uuid = try {
                UUID.fromString(id)
            } catch (e: IllegalArgumentException) {
                null
            }

            return fromUUID(uuid)
        }
    }
}