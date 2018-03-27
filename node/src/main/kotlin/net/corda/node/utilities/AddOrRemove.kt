package net.corda.node.utilities

import net.corda.annotations.serialization.CordaSerializable

/**
 * Enum for when adding/removing something, for example adding or removing an entry in a directory.
 */
@CordaSerializable
enum class AddOrRemove {
    ADD,
    REMOVE
}
