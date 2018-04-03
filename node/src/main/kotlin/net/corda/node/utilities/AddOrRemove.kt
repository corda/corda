package net.corda.node.utilities

import net.corda.annotations.serialization.Serializable

/**
 * Enum for when adding/removing something, for example adding or removing an entry in a directory.
 */
@Serializable
enum class AddOrRemove {
    ADD,
    REMOVE
}
