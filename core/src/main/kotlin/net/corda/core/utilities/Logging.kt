package net.corda.core.utilities

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.config.Configurator
import org.apache.logging.log4j.core.config.LoggerConfig
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

// A couple of inlined utility functions: the first is just a syntax convenience, the second lets us use
// Kotlin's string interpolation efficiently: the message is never calculated/concatenated together unless
// logging at that level is enabled.
inline fun <reified T : Any> loggerFor(): org.slf4j.Logger = LoggerFactory.getLogger(T::class.java)

inline fun org.slf4j.Logger.trace(msg: () -> String) {
    if (isTraceEnabled) trace(msg())
}

inline fun org.slf4j.Logger.debug(msg: () -> String) {
    if (isDebugEnabled) debug(msg())
}

/** A configuration helper that allows modifying the log level for specific loggers */
object LogHelper {
    /**
     * Takes a set of strings identifying logger names for which the logging level should be configured.
     * If the logger name starts with a + or an ordinary character, the level is set to [Level.ALL]. If it starts
     * with a - then logging is switched off.
     */
    fun setLevel(vararg loggerNames: String) {
        for (spec in loggerNames) {
            val (name, level) = when (spec[0]) {
                '+' -> spec.substring(1) to Level.ALL
                '-' -> spec.substring(1) to Level.OFF
                else -> spec to Level.ALL
            }
            setLevel(name, level)
        }
    }

    fun setLevel(vararg classes: KClass<*>) = setLevel(*classes.map { "+" + it.java.`package`.name }.toTypedArray())

    /** Removes custom configuration for the specified logger names */
    fun reset(vararg names: String) {
        val loggerContext = LogManager.getContext(false) as LoggerContext
        val config = loggerContext.configuration
        names.forEach { config.removeLogger(it) }
        loggerContext.updateLoggers(config)
    }

    fun reset(vararg classes: KClass<*>) = reset(*classes.map { it.java.`package`.name }.toTypedArray())

    /** Updates logging level for the specified Log4j logger name */
    private fun setLevel(name: String, level: Level) {
        val loggerContext = LogManager.getContext(false) as LoggerContext
        val config = loggerContext.configuration
        val loggerConfig = LoggerConfig(name, level, false)
        loggerConfig.addAppender(config.appenders["Console-Appender"], null, null)
        config.removeLogger(name)
        config.addLogger(name, loggerConfig)
        loggerContext.updateLoggers(config)
    }

    /**
     * May fail to restore the original level due to unavoidable race if called by multiple threads.
     */
    inline fun <T> withLevel(logName: String, levelName: String, block: () -> T) = run {
        val level = Level.valueOf(levelName)
        val oldLevel = LogManager.getLogger(logName).level
        Configurator.setLevel(logName, level)
        try {
            block()
        } finally {
            Configurator.setLevel(logName, oldLevel)
        }
    }

}
