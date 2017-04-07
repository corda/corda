package net.corda.core.node

import net.corda.core.serialization.CordaSerializable
import java.util.regex.Pattern

/**
 * Versions of the same [major] version but with different [minor] versions are considered compatible with each other. One
 * exception to this is when the major version is 0 - each different minor version should be considered incompatible.
 *
 * If two [Version]s are equal (i.e. [equals] returns true) but they are both [snapshot] then they may refer to different
 * builds of the node. [NodeVersionInfo.revision] would be required to differentiate the two.
 */
@CordaSerializable
data class Version(val major: Int, val minor: Int, val patch: Int?, val snapshot: Boolean) {
    companion object {
        private val pattern = Pattern.compile("""(\d+)\.(\d+)(.(\d+))?(-SNAPSHOT)?""")

        fun parse(string: String): Version {
            val matcher = pattern.matcher(string)
            require(matcher.matches())
            val patch = matcher.group(4)?.toInt()
            return Version(matcher.group(1).toInt(), matcher.group(2).toInt(), patch, matcher.group(5) != null)
        }
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append(major, ".", minor)
        if(patch != null) sb.append(".", patch)
        if(snapshot) sb.append("-SNAPSHOT")
        return sb.toString()
    }
}

data class NodeVersionInfo(val version: Version, val revision: String, val vendor: String)