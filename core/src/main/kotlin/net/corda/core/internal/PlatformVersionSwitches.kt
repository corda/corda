package net.corda.core.internal

/*
Constants for new features that can only be switched on at specific platform versions can be specified in this file.
The text constant describes the feature and the numeric specifies the platform version the feature is enabled at.
 */
object PlatformVersionSwitches {
    const val RECEIVE_FINALITY_FLOW = 2
    const val BELONGS_TO_CONTRACT_ENFORCEMENT = 4
    const val FETCH_MISSING_NETWORK_PARAMETERS = 4
    const val MIGRATE_HASH_TO_SIGNATURE_CONSTRAINTS = 4
    const val MIGRATE_ATTACHMENT_TO_SIGNATURE_CONSTRAINTS = 4
    const val PARAMETERS_GROUP_COMPONENTS = 4
    const val NETWORK_PARAMETER_ATTACHMENTS = 4
    const val STRONG_KEY_CONSTRAINTS = 5
    const val BATCH_DOWNLOAD_COUNTERPARTY_BACKCHAIN = 6
    const val ENABLE_P2P_COMPRESSION = 7
}