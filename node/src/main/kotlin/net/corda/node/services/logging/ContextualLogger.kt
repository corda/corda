package net.corda.node.services.logging

import net.corda.core.context.InvocationContext
import org.slf4j.Logger
import org.slf4j.Marker

// Wish we didn't expose org.slf4j.Logger from public API.
class ContextualLogger(private val delegate: Logger, private val context: InvocationContext, private val contextualise: (message: String?, context: InvocationContext) -> String = { message, ctx -> contextualiseDefault(message, ctx) }) : Logger by delegate {

    override fun warn(marker: Marker?, format: String?, vararg arguments: Any?) {
        delegate.warn(marker, contextualise(format, context), *arguments)
    }

    override fun warn(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {
        delegate.warn(marker, contextualise(format, context), arg1, arg2)
    }

    override fun warn(format: String?, arg1: Any?, arg2: Any?) {
        delegate.warn(contextualise(format, context), arg1, arg2)
    }

    override fun warn(msg: String?, t: Throwable?) {
        delegate.warn(contextualise(msg, context), t)
    }

    override fun warn(format: String?, vararg arguments: Any?) {
        delegate.warn(contextualise(format, context), *arguments)
    }

    override fun warn(msg: String?) {
        delegate.warn(contextualise(contextualise(msg, context), context))
    }

    override fun warn(marker: Marker?, msg: String?) {
        delegate.warn(marker, contextualise(msg, context))
    }

    override fun warn(marker: Marker?, format: String?, arg: Any?) {
        delegate.warn(marker, contextualise(format, context), arg)
    }

    override fun warn(marker: Marker?, msg: String?, t: Throwable?) {
        delegate.warn(marker, contextualise(msg, context), t)
    }

    override fun warn(format: String?, arg: Any?) {
        delegate.warn(contextualise(format, context), arg)
    }

    override fun info(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {
        delegate.info(marker, contextualise(format, context), arg1, arg2)
    }

    override fun info(format: String?, arg1: Any?, arg2: Any?) {
        delegate.info(contextualise(format, context), arg1, arg2)
    }

    override fun info(format: String?, vararg arguments: Any?) {
        delegate.info(contextualise(format, context), *arguments)
    }

    override fun info(marker: Marker?, format: String?, vararg arguments: Any?) {
        delegate.info(marker, contextualise(format, context), *arguments)
    }

    override fun info(marker: Marker?, msg: String?) {
        delegate.info(marker, contextualise(msg, context))
    }

    override fun info(msg: String?) {
        delegate.info(contextualise(msg, context))
    }

    override fun info(msg: String?, t: Throwable?) {
        delegate.info(contextualise(msg, context), t)
    }

    override fun info(marker: Marker?, format: String?, arg: Any?) {
        delegate.info(marker, contextualise(format, context), arg)
    }

    override fun info(format: String?, arg: Any?) {
        delegate.info(contextualise(format, context), arg)
    }

    override fun info(marker: Marker?, msg: String?, t: Throwable?) {
        delegate.info(marker, contextualise(msg, context), t)
    }

    override fun error(format: String?, arg: Any?) {
        delegate.error(contextualise(format, context), arg)
    }

    override fun error(marker: Marker?, format: String?, arg: Any?) {
        delegate.error(marker, contextualise(format, context), arg)
    }

    override fun error(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {
        delegate.error(marker, contextualise(format, context), arg1, arg2)
    }

    override fun error(msg: String?) {
        delegate.error(contextualise(msg, context))
    }

    override fun error(format: String?, vararg arguments: Any?) {
        delegate.error(contextualise(format, context), *arguments)
    }

    override fun error(marker: Marker?, msg: String?, t: Throwable?) {
        delegate.error(marker, contextualise(msg, context), t)
    }

    override fun error(format: String?, arg1: Any?, arg2: Any?) {
        delegate.error(contextualise(format, context), arg1, arg2)
    }

    override fun error(marker: Marker?, msg: String?) {
        delegate.error(marker, contextualise(msg, context))
    }

    override fun error(marker: Marker?, format: String?, vararg arguments: Any?) {
        delegate.error(marker, contextualise(format, context), *arguments)
    }

    override fun error(msg: String?, t: Throwable?) {
        delegate.error(contextualise(msg, context), t)
    }

    override fun debug(format: String?, arg: Any?) {
        delegate.debug(contextualise(format, context), arg)
    }

    override fun debug(marker: Marker?, format: String?, vararg arguments: Any?) {
        delegate.debug(marker, contextualise(format, context), *arguments)
    }

    override fun debug(format: String?, vararg arguments: Any?) {
        delegate.debug(contextualise(format, context), *arguments)
    }

    override fun debug(marker: Marker?, msg: String?) {
        delegate.debug(marker, contextualise(msg, context))
    }

    override fun debug(msg: String?) {
        delegate.debug(contextualise(msg, context))
    }

    override fun debug(format: String?, arg1: Any?, arg2: Any?) {
        delegate.debug(contextualise(format, context), arg1, arg2)
    }

    override fun debug(msg: String?, t: Throwable?) {
        delegate.debug(contextualise(msg, context), t)
    }

    override fun debug(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {
        delegate.debug(marker, contextualise(format, context), arg1, arg2)
    }

    override fun debug(marker: Marker?, format: String?, arg: Any?) {
        delegate.debug(marker, contextualise(format, context), arg)
    }

    override fun debug(marker: Marker?, msg: String?, t: Throwable?) {
        delegate.debug(marker, contextualise(msg, context), t)
    }

    override fun trace(marker: Marker?, msg: String?, t: Throwable?) {
        delegate.trace(marker, contextualise(msg, context), t)
    }

    override fun trace(format: String?, arg: Any?) {
        delegate.trace(contextualise(format, context), arg)
    }

    override fun trace(msg: String?) {
        delegate.trace(contextualise(msg, context))
    }

    override fun trace(format: String?, arg1: Any?, arg2: Any?) {
        delegate.trace(contextualise(format, context), arg1, arg2)
    }

    override fun trace(format: String?, vararg arguments: Any?) {
        delegate.trace(contextualise(format, context), *arguments)
    }

    override fun trace(msg: String?, t: Throwable?) {
        delegate.trace(contextualise(msg, context), t)
    }

    override fun trace(marker: Marker?, msg: String?) {
        delegate.trace(marker, contextualise(msg, context))
    }

    override fun trace(marker: Marker?, format: String?, arg: Any?) {
        delegate.trace(marker, contextualise(format, context), arg)
    }

    override fun trace(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {
        delegate.trace(marker, contextualise(format, context), arg1, arg2)
    }

    override fun trace(marker: Marker?, format: String?, vararg argArray: Any?) {
        delegate.trace(marker, contextualise(format, context), *argArray)
    }

    companion object {

        private fun contextualiseDefault(message: String?, context: InvocationContext): String {

            return "$message - ${context.serialised}"
        }

        private val InvocationContext.serialised: String
            get() {
                // TODO sollecitom consider a different default
                val keys = mutableMapOf<String, String>()
                keys += "actor" to "{id: ${actor.id.value}, store: ${actor.serviceId.value}, identity: $owningLegalIdentity}"
                keys += "trace" to "{invocation: ${trace.invocationId.value}, session: ${trace.sessionId.value}}"
                externalTrace?.let {
                    keys += "externalTrace" to "{invocation: ${it.invocationId.value}, session: ${it.sessionId.value}}"
                }
                return keys.entries.joinToString(separator = ", ", prefix = "context: {", postfix = "}") { "${it.key}: ${it.value}" }
            }
    }
}