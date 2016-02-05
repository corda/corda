/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.utilities

import org.slf4j.LoggerFactory
import java.io.PrintWriter
import java.io.StringWriter
import java.text.MessageFormat
import java.util.*
import java.util.logging.Formatter
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

// A couple of inlined utility functions: the first is just a syntax convenience, the second lets us use
// Kotlin's string interpolation efficiently: the message is never calculated/concatenated together unless
// logging at that level is enabled.
inline fun <reified T : Any> loggerFor(): org.slf4j.Logger = LoggerFactory.getLogger(T::class.java)
inline fun org.slf4j.Logger.trace(msg: () -> String) {
    if (isTraceEnabled) trace(msg())
}

/**
 * A Java logging formatter that writes more compact output than the default.
 */
class BriefLogFormatter : Formatter() {
    override fun format(logRecord: LogRecord): String {
        val arguments = arrayOfNulls<Any>(7)
        arguments[0] = logRecord.threadID
        arguments[1] = when (logRecord.level) {
            Level.SEVERE -> " **ERROR** "
            Level.WARNING -> " (warning) "
            else -> ""
        }
        val fullClassName = logRecord.sourceClassName
        val dollarIndex = fullClassName.indexOf('$')
        val className = fullClassName.substring(fullClassName.lastIndexOf('.') + 1, if (dollarIndex == -1) fullClassName.length else dollarIndex)
        arguments[2] = className
        arguments[3] = logRecord.sourceMethodName
        arguments[4] = Date(logRecord.millis)
        arguments[5] = if (logRecord.parameters != null) MessageFormat.format(logRecord.message, *logRecord.parameters) else logRecord.message
        if (logRecord.thrown != null) {
            val result = StringWriter()
            logRecord.thrown.printStackTrace(PrintWriter(result))
            arguments[6] = result.toString()
        } else {
            arguments[6] = ""
        }
        return messageFormat.format(arguments)
    }

    companion object {
        private val messageFormat = MessageFormat("{4,date,HH:mm:ss} {0} {1}{2}.{3}: {5}\n{6}")

        // OpenJDK made a questionable, backwards incompatible change to the Logger implementation. It internally uses
        // weak references now which means simply fetching the logger and changing its configuration won't work. We must
        // keep a reference to our custom logger around.
        private val loggerRefs = ArrayList<Logger>()

        /** Configures JDK logging to use this class for everything.  */
        fun init() {
            val logger = Logger.getLogger("")
            val handlers = logger.handlers
            handlers[0].formatter = BriefLogFormatter()
            loggerRefs.add(logger)
        }

        fun initVerbose(packageSpec: String = "") {
            init()
            loggerRefs[0].handlers[0].level = Level.ALL
            val logger = Logger.getLogger(packageSpec)
            logger.level = Level.ALL
            loggerRefs.add(logger)
        }
    }
}
