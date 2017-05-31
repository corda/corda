package net.corda.nodeapi.serialization

import net.corda.core.node.CordaPluginRegistry
import net.corda.core.serialization.SerializationCustomization
import java.util.*

class DefaultBlacklist : CordaPluginRegistry() {
    override fun customizeSerialization(custom: SerializationCustomization): Boolean {
        custom.apply {
            // TODO: Turn this into an array and use map {}.
            addToBlacklist(HashSet::class.java)
            addToBlacklist(HashMap::class.java)
            addToBlacklist(Thread::class.java)
            addToBlacklist(ClassLoader::class.java)
            // TODO: add more blacklisted classes such as anything related to files/IO, lang.invoke.* etc.
        }
        return true
    }
}