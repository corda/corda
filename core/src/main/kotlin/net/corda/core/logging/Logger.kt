package net.corda.core.logging

// Cannot use default parameters for trace(), debug(), etc. nor `@JvmOverloads`.
interface Logger {

    fun log(level: Level, loggerName: String, message: String, error: Throwable? = null)

    val name: String

    fun log(level: Level, message: String, error: Throwable? = null) = log(level, name, message, error)

    fun log(level: Level, error: Throwable? = null, message: () -> String) {

        if (isEnabled(level)) {
            log(level, message(), error)
        }
    }

    fun trace(message: String) = log(Level.TRACE, message, null)

    fun trace(message: String, error: Throwable) = log(Level.TRACE, message, error)

    fun trace(message: () -> String) = log(Level.TRACE, null, message)

    fun trace(error: Throwable, message: () -> String) = log(Level.TRACE, error, message)

    fun debug(message: String) = log(Level.DEBUG, message, null)

    fun debug(message: String, error: Throwable) = log(Level.DEBUG, message, error)

    fun debug(message: () -> String) = log(Level.DEBUG, null, message)

    fun debug(error: Throwable, message: () -> String) = log(Level.DEBUG, error, message)

    fun info(message: String) = log(Level.INFO, message, null)

    fun info(message: String, error: Throwable) = log(Level.INFO, message, error)

    fun info(message: () -> String) = log(Level.INFO, null, message)

    fun info(error: Throwable, message: () -> String) = log(Level.INFO, error, message)

    fun warn(message: String) = log(Level.WARN, message, null)

    fun warn(message: String, error: Throwable) = log(Level.WARN, message, error)

    fun warn(message: () -> String) = log(Level.WARN, null, message)

    fun warn(error: Throwable, message: () -> String) = log(Level.WARN, error, message)

    fun error(message: String) = log(Level.ERROR, message, null)

    fun error(message: String, error: Throwable) = log(Level.ERROR, message, error)

    fun error(message: () -> String) = log(Level.ERROR, null, message)

    fun error(error: Throwable, message: () -> String) = log(Level.ERROR, error, message)

    fun isEnabled(level: Level): Boolean

    sealed class Level {

        object TRACE : Level()
        object DEBUG : Level()
        object INFO : Level()
        object WARN : Level()
        object ERROR : Level()
    }
}