package net.corda.serialization.internal

import net.corda.core.serialization.ClassWhitelist
import java.util.*

interface MutableClassWhitelist : ClassWhitelist {
    fun add(entry: Class<*>)
}

object AllWhitelist : ClassWhitelist {
    override fun hasListed(type: Class<*>): Boolean = true
}

class BuiltInExceptionsWhitelist : ClassWhitelist {
    companion object {
        private val packageName = "^(?:java|kotlin)(?:[.]|$)".toRegex()
    }

    override fun hasListed(type: Class<*>): Boolean {
        return Throwable::class.java.isAssignableFrom(type) && packageName.containsMatchIn(type.`package`.name)
    }
}

sealed class AbstractMutableClassWhitelist(private val whitelist: MutableSet<String>, private val delegate: ClassWhitelist) : MutableClassWhitelist {
    override fun hasListed(type: Class<*>): Boolean {
        /**
         * There are certain delegates like [net.corda.serialization.internal.AllButBlacklisted]
         * which may throw when asked whether the type is listed.
         * In such situations - it may be a good idea to ask [delegate] first before making a check against own [whitelist].
         */
        return delegate.hasListed(type) || (type.name in whitelist)
    }

    override fun add(entry: Class<*>) {
        whitelist += entry.name
    }
}

/**
 * A whitelist that can be customised via the [net.corda.core.serialization.SerializationWhitelist],
 * since it implements [MutableClassWhitelist].
 */
class TransientClassWhiteList(delegate: ClassWhitelist) : AbstractMutableClassWhitelist(Collections.synchronizedSet(mutableSetOf()), delegate)

// TODO: Need some concept of from which class loader
class GlobalTransientClassWhiteList(delegate: ClassWhitelist) : AbstractMutableClassWhitelist(whitelist, delegate) {
    companion object {
        private val whitelist: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    }
}
