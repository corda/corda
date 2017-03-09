package net.corda.core.node

import net.corda.core.serialization.CordaSerializable
import java.util.regex.Pattern

/**
 * Versions of the same [major] version but with different [minor] versions are considered compatible with each other. One
 * exception to this is when the major version is 0 - each different minor version should be considered incompatible.
 *
 * If two [Version]s are equal (i.e. [equals] returns true) but they are both [snapshot] then they may refer to different
 * versions of the node. [NodeVersionInfo.revision] would be required to different them.
 */
@CordaSerializable
data class Version(val major: Int, val minor: Int, val snapshot: Boolean) {
    companion object {
        private val pattern = Pattern.compile("""(\d+)\.(\d+)(-SNAPSHOT)?""")

        fun parse(string: String): Version {
            val matcher = pattern.matcher(string)
            require(matcher.matches())
            return Version(matcher.group(1).toInt(), matcher.group(2).toInt(), matcher.group(3) != null)
        }
    }

    override fun toString(): String = if (snapshot) "$major.$minor-SNAPSHOT" else "$major.$minor"
}

data class NodeVersionInfo(val version: Version, val revision: String, val vendor: String)