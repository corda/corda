package net.corda.core.internal

/*
Constants for new features that can only be switched on at specific platform versions can be specified in this file.
The text constant describes the feature and the numeric specifies the platform version the feature is enabled at.
 */
object PlatformVersionSwitches {
    const val REMOVE_NO_OVERLAP_RULE_FOR_REFERENCE_DATA_ATTACHMENTS = 7
    const val ENABLE_P2P_COMPRESSION = 7
}