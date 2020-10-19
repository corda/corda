package net.corda.core.contracts

import net.corda.core.serialization.CordaSerializable

/**
 * An enum, for which each property corresponds to a transaction component group. The position in the enum class
 * declaration (ordinal) is used for component-leaf ordering when computing the Merkle tree.
 */
@CordaSerializable
enum class ComponentGroupEnum {
    INPUTS_GROUP, // ordinal = 0.
    OUTPUTS_GROUP, // ordinal = 1.
    COMMANDS_GROUP, // ordinal = 2.
    ATTACHMENTS_GROUP, // ordinal = 3.
    NOTARY_GROUP, // ordinal = 4.
    TIMEWINDOW_GROUP, // ordinal = 5.
    SIGNERS_GROUP, // ordinal = 6.
    REFERENCES_GROUP, // ordinal = 7.
    PARAMETERS_GROUP, // ordinal = 8.
    PRIVACY_SALT // ordinal = 9.
}
