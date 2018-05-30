package net.corda.gradle.jarfilter

import org.gradle.api.logging.LogLevel.*
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.slf4j.Marker
import org.slf4j.helpers.MessageFormatter
import kotlin.reflect.KClass

class StdOutLogging(private val name: String, private val threshold: LogLevel) : Logger {
    constructor(clazz: KClass<*>, threshold: LogLevel = INFO) : this(clazz.java.simpleName, threshold)

    override fun getName(): String = name

    override fun isErrorEnabled(): Boolean = isEnabled(ERROR)
    override fun isErrorEnabled(marker: Marker): Boolean = isEnabled(ERROR)

    override fun isWarnEnabled(): Boolean = isEnabled(WARN)
    override fun isWarnEnabled(marker: Marker): Boolean = isEnabled(WARN)

    override fun isInfoEnabled(): Boolean = isEnabled(INFO)
    override fun isInfoEnabled(marker: Marker): Boolean = isEnabled(INFO)

    override fun isDebugEnabled(): Boolean = isEnabled(DEBUG)
    override fun isDebugEnabled(marker: Marker): Boolean = isEnabled(DEBUG)

    override fun isTraceEnabled(): Boolean = isEnabled(DEBUG)
    override fun isTraceEnabled(marker: Marker): Boolean = isEnabled(DEBUG)

    override fun isQuietEnabled(): Boolean = isEnabled(QUIET)

    override fun isLifecycleEnabled(): Boolean = isEnabled(LIFECYCLE)

    override fun isEnabled(level: LogLevel): Boolean = threshold <= level

    override fun warn(msg: String) = log(WARN, msg)
    override fun warn(msg: String, obj: Any?) = log(WARN, msg, obj)
    override fun warn(msg: String, vararg objects: Any?) = log(WARN, msg, *objects)
    override fun warn(msg: String, obj1: Any?, obj2: Any?) = log(WARN, msg, obj1, obj2)
    override fun warn(msg: String, ex: Throwable) = log(WARN, msg, ex)

    override fun warn(marker: Marker, msg: String) {
        if (isWarnEnabled(marker)) {
            print(WARN, msg)
        }
    }

    override fun warn(marker: Marker, msg: String, obj: Any?) {
        if (isWarnEnabled(marker)) {
            print(WARN, msg, obj)
        }
    }

    override fun warn(marker: Marker, msg: String, obj1: Any?, obj2: Any?) {
        if (isWarnEnabled(marker)) {
            print(WARN, msg, obj1, obj2)
        }
    }

    override fun warn(marker: Marker, msg: String, vararg objects: Any?) {
        if (isWarnEnabled(marker)) {
            printAny(WARN, msg, *objects)
        }
    }

    override fun warn(marker: Marker, msg: String, ex: Throwable) {
        if (isWarnEnabled(marker)) {
            print(WARN, msg, ex)
        }
    }

    override fun info(message: String, vararg objects: Any?) = log(INFO, message, *objects)
    override fun info(message: String) = log(INFO, message)
    override fun info(message: String, obj: Any?) = log(INFO, message, obj)
    override fun info(message: String, obj1: Any?, obj2: Any?) = log(INFO, message, obj1, obj2)
    override fun info(message: String, ex: Throwable) = log(INFO, message, ex)

    override fun info(marker: Marker, msg: String) {
        if (isInfoEnabled(marker)) {
            print(INFO, msg)
        }
    }

    override fun info(marker: Marker, msg: String, obj: Any?) {
        if (isInfoEnabled(marker)) {
            print(INFO, msg, obj)
        }
    }

    override fun info(marker: Marker, msg: String, obj1: Any?, obj2: Any?) {
        if (isInfoEnabled(marker)) {
            print(INFO, msg, obj1, obj2)
        }
    }

    override fun info(marker: Marker, msg: String, vararg objects: Any?) {
        if (isInfoEnabled(marker)) {
            printAny(INFO, msg, *objects)
        }
    }

    override fun info(marker: Marker, msg: String, ex: Throwable) {
        if (isInfoEnabled(marker)) {
            print(INFO, msg, ex)
        }
    }

    override fun error(message: String) = log(ERROR, message)
    override fun error(message: String, obj: Any?) = log(ERROR, message, obj)
    override fun error(message: String, obj1: Any?, obj2: Any?) = log(ERROR, message, obj1, obj2)
    override fun error(message: String, vararg objects: Any?) = log(ERROR, message, *objects)
    override fun error(message: String, ex: Throwable) = log(ERROR, message, ex)

    override fun error(marker: Marker, msg: String) {
        if (isErrorEnabled(marker)) {
            print(ERROR, msg)
        }
    }

    override fun error(marker: Marker, msg: String, obj: Any?) {
        if (isErrorEnabled(marker)) {
           print(ERROR, msg, obj)
        }
    }

    override fun error(marker: Marker, msg: String, obj1: Any?, obj2: Any?) {
        if (isErrorEnabled(marker)) {
            print(ERROR, msg, obj1, obj2)
        }
    }

    override fun error(marker: Marker, msg: String, vararg objects: Any?) {
        if (isErrorEnabled(marker)) {
            printAny(ERROR, msg, *objects)
        }
    }

    override fun error(marker: Marker, msg: String, ex: Throwable) {
        if (isErrorEnabled(marker)) {
            print(ERROR, msg, ex)
        }
    }

    override fun log(level: LogLevel, message: String) {
        if (isEnabled(level)) {
            print(level, message)
        }
    }

    override fun log(level: LogLevel, message: String, vararg objects: Any?) {
        if (isEnabled(level)) {
            printAny(level, message, *objects)
        }
    }

    override fun log(level: LogLevel, message: String, ex: Throwable) {
        if (isEnabled(level)) {
            print(level, message, ex)
        }
    }

    override fun debug(message: String, vararg objects: Any?) = log(DEBUG, message, *objects)
    override fun debug(message: String) = log(DEBUG, message)
    override fun debug(message: String, obj: Any?) = log(DEBUG, message, obj)
    override fun debug(message: String, obj1: Any?, obj2: Any?) = log(DEBUG, message, obj1, obj2)
    override fun debug(message: String, ex: Throwable) = log(DEBUG, message, ex)

    override fun debug(marker: Marker, msg: String) {
        if (isDebugEnabled(marker)) {
            print(DEBUG, msg)
        }
    }

    override fun debug(marker: Marker, msg: String, obj: Any?) {
        if (isDebugEnabled(marker)) {
            print(DEBUG, msg, obj)
        }
    }

    override fun debug(marker: Marker, msg: String, obj1: Any?, obj2: Any?) {
        if (isDebugEnabled(marker)) {
            print(DEBUG, msg, obj1, obj2)
        }
    }

    override fun debug(marker: Marker, msg: String, vararg objects: Any?) {
        if (isDebugEnabled(marker)) {
            printAny(DEBUG, msg, *objects)
        }
    }

    override fun debug(marker: Marker, msg: String, ex: Throwable) {
        if (isDebugEnabled(marker)) {
            print(DEBUG, msg, ex)
        }
    }

    override fun lifecycle(message: String) = log(LIFECYCLE, message)
    override fun lifecycle(message: String, vararg objects: Any?) = log(LIFECYCLE, message, *objects)
    override fun lifecycle(message: String, ex: Throwable) = log(LIFECYCLE, message, ex)

    override fun quiet(message: String) = log(QUIET, message)
    override fun quiet(message: String, vararg objects: Any?) = log(QUIET, message, *objects)
    override fun quiet(message: String, ex: Throwable) = log(QUIET, message, ex)

    override fun trace(message: String) = debug(message)
    override fun trace(message: String, obj: Any?) = debug(message, obj)
    override fun trace(message: String, obj1: Any?, obj2: Any?) = debug(message, obj1, obj2)
    override fun trace(message: String, vararg objects: Any?) = debug(message, *objects)
    override fun trace(message: String, ex: Throwable) = debug(message, ex)

    override fun trace(marker: Marker, msg: String) {
        if (isTraceEnabled(marker)) {
            print(DEBUG, msg)
        }
    }

    override fun trace(marker: Marker, msg: String, obj: Any?) {
        if (isTraceEnabled(marker)) {
            print(DEBUG, msg, obj)
        }
    }

    override fun trace(marker: Marker, msg: String, obj1: Any?, obj2: Any?) {
        if (isTraceEnabled(marker)) {
            print(DEBUG, msg, obj1, obj2)
        }
    }

    override fun trace(marker: Marker, msg: String, vararg objects: Any?) {
        if (isTraceEnabled(marker)) {
            printAny(DEBUG, msg, *objects)
        }
    }

    override fun trace(marker: Marker, msg: String, ex: Throwable) {
        if (isTraceEnabled) {
            print(DEBUG, msg, ex)
        }
    }

    private fun print(level: LogLevel, message: String) {
        println("$name - $level: $message")
    }

    private fun print(level: LogLevel, message: String, ex: Throwable) {
        print(level, message)
        ex.printStackTrace(System.out)
    }

    private fun print(level: LogLevel, message: String, obj: Any?) {
        print(level, MessageFormatter.format(message, obj).message)
    }

    private fun print(level: LogLevel, message: String, obj1: Any?, obj2: Any?) {
        print(level, MessageFormatter.format(message, obj1, obj2).message)
    }

    private fun printAny(level: LogLevel, message: String, vararg objects: Any?) {
        print(level, MessageFormatter.arrayFormat(message, objects).message)
    }
}