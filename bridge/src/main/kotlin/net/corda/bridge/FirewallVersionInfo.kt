package net.corda.bridge


/**
 * Encapsulates various pieces of version information of the firewall.
 */
data class FirewallVersionInfo(
        /**
         * Platform version of the firewall which is an integer value which increments on any release where any of the public
         * API of the entire Corda platform changes. This includes messaging, serialisation, firewall APIs, etc.
         */
        val platformVersion: Int,
        /** Release version string of the firewall. */
        val releaseVersion: String,
        /** The exact version control commit ID of the firewall build. */
        val revision: String,
        /** The firewall vendor */
        val vendor: String)