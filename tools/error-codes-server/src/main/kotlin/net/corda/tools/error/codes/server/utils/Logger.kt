package net.corda.tools.error.codes.server.utils

import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

interface Logger<CONTEXT : LoggingContext> {

    companion object {

        fun <TYPE : Any, CONTEXT : LoggingContext> forType(type: KClass<TYPE>): Logger<CONTEXT> = Slf4jLoggerAdapter.wrap(LoggerFactory.getLogger(type.javaObjectType))
    }

    sealed class Level(private val severity: Int) : Comparable<Level> {

        companion object {

            private val comparator = Comparator.comparing(Level::severity)

            val TRACE: Level = Trace()
            val DEBUG: Level = Debug()
            val INFO: Level = Info()
            val WARN: Level = Warn()
            val ERROR: Level = Error()
        }

        private class Trace : Level(1)

        private class Debug : Level(2)

        private class Info : Level(3)

        private class Warn : Level(4)

        private class Error : Level(5)

        override operator fun compareTo(other: Level) = comparator.compare(this, other)
    }

    fun isEnabled(level: Level): Boolean = true

    fun name(): String = javaClass.name

    fun log(context: CONTEXT?, level: Level, message: String, vararg argSuppliers: () -> Any?)

    fun log(context: CONTEXT?, level: Level, message: String, vararg args: Any?) = log(context, level, message, argSuppliers = *args.map { argument -> { argument } }.toTypedArray())

    fun log(context: CONTEXT?, level: Level, message: String, throwable: Throwable) = log(context, level, message, arrayOf(throwable))

    fun log(level: Level, message: String, vararg argSuppliers: () -> Any?) = log(null, level, message, *argSuppliers)

    fun log(level: Level, message: String, vararg args: Any?) = log(level, message, *args.map { argument -> { argument } }.toTypedArray())

    fun log(level: Level, message: String, throwable: Throwable) = log(level, message, arrayOf(throwable))

    fun trace(context: CONTEXT?, message: String, vararg argSuppliers: () -> Any?) = log(context, Level.TRACE, message, argSuppliers = *argSuppliers)

    fun trace(context: CONTEXT?, message: String, vararg args: Any?) = log(context, Level.TRACE, message, *args)

    fun trace(context: CONTEXT?, message: String, throwable: Throwable) = log(context, Level.TRACE, message, throwable)

    fun trace(message: String, vararg argSuppliers: () -> Any?) = log(Level.TRACE, message, argSuppliers = *argSuppliers)

    fun trace(message: String, vararg args: Any?) = log(Level.TRACE, message, *args)

    fun trace(message: String, throwable: Throwable) = log(Level.TRACE, message, throwable)

    fun debug(context: CONTEXT?, message: String, vararg argSuppliers: () -> Any?) = log(context, Level.DEBUG, message, argSuppliers = *argSuppliers)

    fun debug(context: CONTEXT?, message: String, vararg args: Any?) = log(context, Level.DEBUG, message, *args)

    fun debug(context: CONTEXT?, message: String, throwable: Throwable) = log(context, Level.DEBUG, message, throwable)

    fun debug(message: String, vararg argSuppliers: () -> Any?) = log(Level.DEBUG, message, argSuppliers = *argSuppliers)

    fun debug(message: String, vararg args: Any?) = log(Level.DEBUG, message, *args)

    fun debug(message: String, throwable: Throwable) = log(Level.DEBUG, message, throwable)

    fun info(context: CONTEXT?, message: String, throwable: Throwable) = log(context, Level.INFO, message, throwable)

    fun info(context: CONTEXT?, message: String, vararg argSuppliers: () -> Any?) = log(context, Level.INFO, message, argSuppliers = *argSuppliers)

    fun info(context: CONTEXT?, message: String, vararg args: Any?) = log(context, Level.INFO, message, *args)

    fun info(message: String, throwable: Throwable) = log(Level.INFO, message, throwable)

    fun info(message: String, vararg argSuppliers: () -> Any?) = log(Level.INFO, message, argSuppliers = *argSuppliers)

    fun info(message: String, vararg args: Any?) = log(Level.INFO, message, *args)

    fun warn(context: CONTEXT?, message: String, vararg argSuppliers: () -> Any?) = log(context, Level.WARN, message, argSuppliers = *argSuppliers)

    fun warn(context: CONTEXT?, message: String, vararg args: Any?) = log(context, Level.WARN, message, *args)

    fun warn(context: CONTEXT?, message: String, throwable: Throwable) = log(context, Level.WARN, message, throwable)

    fun warn(message: String, vararg argSuppliers: () -> Any?) = log(Level.WARN, message, argSuppliers = *argSuppliers)

    fun warn(message: String, vararg args: Any?) = log(Level.WARN, message, *args)

    fun warn(message: String, throwable: Throwable) = log(Level.WARN, message, throwable)

    fun error(context: CONTEXT?, message: String, vararg argSuppliers: () -> Any?) = log(context, Level.ERROR, message, argSuppliers = *argSuppliers)

    fun error(context: CONTEXT?, message: String, vararg args: Any?) = log(context, Level.ERROR, message, *args)

    fun error(context: CONTEXT?, message: String, throwable: Throwable) = log(context, Level.ERROR, message, throwable)

    fun error(message: String, vararg argSuppliers: () -> Any?) = log(Level.ERROR, message, argSuppliers = *argSuppliers)

    fun error(message: String, vararg args: Any?) = log(Level.ERROR, message, *args)

    fun error(message: String, throwable: Throwable) = log(Level.ERROR, message, throwable)

    fun isTraceEnabled() = isEnabled(Level.TRACE)

    fun isDebugEnabled() = isEnabled(Level.DEBUG)

    fun isInfoEnabled() = isEnabled(Level.INFO)

    fun isWarnEnabled() = isEnabled(Level.WARN)

    fun isErrorEnabled() = isEnabled(Level.ERROR)
}

// TODO sollecitom create such a function in a context-aware place outside this class.
//inline fun <reified T : Any> loggerFor(): Logger<ContextImpl> = Logger.forType<ContextImpl>(T::class)