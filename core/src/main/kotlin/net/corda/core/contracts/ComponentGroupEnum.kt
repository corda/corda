package net.corda.core.contracts

/**
 * An enum, for which each property corresponds to a transaction component group. The position in the enum class
 * declaration (ordinal) is used for component-leaf ordering when computing the Merkle tree.
 */
enum class ComponentGroupEnum {
    INPUTS_GROUP, // ordinal = 0.
    OUTPUTS_GROUP, // ordinal = 1.
    COMMANDS_GROUP, // ordinal = 2.
    ATTACHMENTS_GROUP, // ordinal = 3.
    NOTARY_GROUP, // ordinal = 4.
    TIMEWINDOW_GROUP, // ordinal = 5.
    SIGNERS_GROUP, // ordinal = 6.
    UNSPENDABLE_INPUTS_GROUP // ordinal = 7.
}
