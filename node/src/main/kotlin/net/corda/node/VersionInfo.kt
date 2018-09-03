package net.corda.node

/**
 * Encapsulates various pieces of version information of the node.
 */
data class VersionInfo(
        /**
         * Platform version of the node which is an integer value which increments on any release where any of the public
         * API of the entire Corda platform changes. This includes messaging, serialisation, node APIs, etc.
         */
        val platformVersion: Int,
        /** Release version string of the node. */
        val releaseVersion: String,
        /** The exact version control commit ID of the node build. */
        val revision: String,
        /** The node vendor */
        val vendor: String) {

        companion object {
                val UNKNOWN = VersionInfo(1, "Unknown", "Unknown", "Unknown")
        }
}