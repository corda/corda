/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

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