package net.corda.serialization.djvm

import net.corda.core.serialization.ClassWhitelist

class SandboxWhitelist : ClassWhitelist {
    companion object {
        private val packageName = "^sandbox\\.(?:java|kotlin)(?:[.]|$)".toRegex()
    }

    override fun hasListed(type: Class<*>): Boolean {
        return packageName.containsMatchIn(type.`package`.name)
    }
}
