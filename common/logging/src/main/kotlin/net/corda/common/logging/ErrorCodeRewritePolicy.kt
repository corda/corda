package net.corda.common.logging

import org.apache.logging.log4j.core.Core
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.appender.rewrite.RewritePolicy
import org.apache.logging.log4j.core.config.plugins.Plugin
import org.apache.logging.log4j.core.config.plugins.PluginFactory
import org.apache.logging.log4j.core.impl.Log4jLogEvent

@Plugin(name = "ErrorCodeRewritePolicy", category = Core.CATEGORY_NAME, elementType = "rewritePolicy", printObject = false)
class ErrorCodeRewritePolicy : RewritePolicy {
    override fun rewrite(source: LogEvent): LogEvent? {
        val newMessage = source.message?.withErrorCodeFor(source.thrown, source.level)
        return if (newMessage == source.message) {
            source
        } else {
            Log4jLogEvent.Builder(source).setMessage(newMessage).build()
        }
    }

    companion object {
        @JvmStatic
        @PluginFactory
        fun createPolicy(): ErrorCodeRewritePolicy {
            return ErrorCodeRewritePolicy()
        }
    }
}