package net.corda.core.internal

/*
Constants for new features that can only be switched on at specific platform versions can be specified in this file.
The text constant describes the feature and the numeric specifies the platform version the feature is enabled at.
 */
object PlatformVersionSwitches {
    const val FIRST_VERSION = 1
    const val BELONGS_TO_CONTRACT_ENFORCEMENT = 4
    const val FETCH_MISSING_NETWORK_PARAMETERS = 4
    const val MIGRATE_HASH_TO_SIGNATURE_CONSTRAINTS = 4
    const val MIGRATE_ATTACHMENT_TO_SIGNATURE_CONSTRAINTS = 4
    const val NETWORK_PARAMETERS_COMPONENT_GROUP = 4
    const val IGNORE_JOLOKIA_JSON_SIMPLE_IN_CORDAPPS = 4
    const val MIN_PLATFORM_VERSION_FOR_BACKPRESSURE_MESSAGE = 4
    const val LIMIT_KEYS_IN_SIGNATURE_CONSTRAINTS = 5
    const val BATCH_DOWNLOAD_COUNTERPARTY_BACKCHAIN = 6
    const val ENABLE_P2P_COMPRESSION = 7
    const val CERTIFICATE_ROTATION = 9
}