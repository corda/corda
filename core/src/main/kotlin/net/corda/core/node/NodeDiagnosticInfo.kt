package net.corda.core.node

import net.corda.core.cordapp.CordappInfo
import net.corda.core.serialization.CordaSerializable

/**
 * A [NodeDiagnosticInfo] holds information about the current node version.
 * @param version The current node version string, e.g. 4.3, 4.4-SNAPSHOT. Note that this string is effectively freeform, and so should only
 *                be used for providing diagnostic information. It should not be used to make functionality decisions (the platformVersion
 *                is a better fit for this).
 * @param revision The git commit hash this node was built from
 * @param platformVersion The platform version of this node. This number represents a released API version, and should be used to make
 *                        functionality decisions (e.g. enabling an app feature only if an underlying platform feature exists)
 * @param vendor The vendor of this node
 * @param cordapps A list of CorDapps currently installed on this node
 */
@CordaSerializable
data class NodeDiagnosticInfo(val version: String,
                              val revision: String,
                              val platformVersion: Int,
                              val vendor: String,
                              val cordapps: List<CordappInfo>)