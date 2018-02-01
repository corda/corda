package net.corda.bridge


/**
 * Encapsulates various pieces of version information of the bridge.
 */
data class BridgeVersionInfo(
        /**
         * Platform version of the bridge which is an integer value which increments on any release where any of the public
         * API of the entire Corda platform changes. This includes messaging, serialisation, bridge APIs, etc.
         */
        val platformVersion: Int,
        /** Release version string of the bridge. */
        val releaseVersion: String,
        /** The exact version control commit ID of the bridge build. */
        val revision: String,
        /** The bridge vendor */
        val vendor: String)