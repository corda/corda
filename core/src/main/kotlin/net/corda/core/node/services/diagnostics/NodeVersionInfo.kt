package net.corda.core.node.services.diagnostics

/**
 * Version info about the node. Note that this data should be used for diagnostics purposes only - it is unsafe to rely on this for
 * functional decisions.
 *
 * @param releaseVersion The release version string of the node, e.g. 4.3, 4.4-SNAPSHOT.
 * @param revision The git commit hash this node was built from
 * @param platformVersion The platform version of this node, representing the released API version.
 * @param vendor The vendor of this node
 */
data class NodeVersionInfo(val releaseVersion: String,
                           val revision: String,
                           val platformVersion: Int,
                           val vendor: String)