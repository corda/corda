package net.corda.node.utilities.logging

import net.corda.nodeapi.internal.addShutdownHook
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.async.AsyncLoggerContext
import org.apache.logging.log4j.core.selector.ClassLoaderContextSelector
import org.apache.logging.log4j.core.util.Constants
import org.apache.logging.log4j.util.PropertiesUtil
import java.net.URI

class AsyncLoggerContextSelectorNoThreadLocal : ClassLoaderContextSelector() {
    companion object {
        /**
         * Returns `true` if the user specified this selector as the Log4jContextSelector, to make all loggers
         * asynchronous.
         *
         * @return `true` if all loggers are asynchronous, `false` otherwise.
         */
        @JvmStatic
        fun isSelected(): Boolean {
            return AsyncLoggerContextSelectorNoThreadLocal::class.java.name == PropertiesUtil.getProperties().getStringProperty(Constants.LOG4J_CONTEXT_SELECTOR)
        }
    }

    init {
        // if we use async log4j logging, we need to shut down the logging to flush the loggers on exit
        addShutdownHook { LogManager.shutdown() }
    }

    override fun createContext(name: String?, configLocation: URI?): LoggerContext {
        return AsyncLoggerContext(name, null, configLocation).also { it.setUseThreadLocals(false) }
    }

    override fun toContextMapKey(loader: ClassLoader?): String {
        // LOG4J2-666 ensure unique name across separate instances created by webapp classloaders
        return "AsyncContextNoThreadLocal@" + Integer.toHexString(System.identityHashCode(loader))
    }

    override fun defaultContextName(): String {
        return "DefaultAsyncContextNoThreadLocal@" + Thread.currentThread().name
    }
}
