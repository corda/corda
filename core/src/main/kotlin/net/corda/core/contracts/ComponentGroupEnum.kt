package net.corda.core.contracts

/**
 * An enum, for which each property corresponds to a transaction component group. The position in the enum class
 * declaration (ordinal) is used for component-leaf ordering when computing the Merkle tree.
 */
enum class ComponentGroupEnum {
    INPUTS_GROUP, // ordinal = 0.
    OUTPUTS_GROUP, // ordinal = 1.
    COMMANDS_GROUP, // ordinal = 2.
    ATTACHMENTS_GROUP, // ordinal = 3. This is for legacy attachments. It's not been renamed for backwards compatibility.
    NOTARY_GROUP, // ordinal = 4.
    TIMEWINDOW_GROUP, // ordinal = 5.
    SIGNERS_GROUP, // ordinal = 6.
    REFERENCES_GROUP, // ordinal = 7.
    PARAMETERS_GROUP, // ordinal = 8.
    ATTACHMENTS_V2_GROUP // ordinal = 9. From 4.12+ this group is used for attachments.
}
