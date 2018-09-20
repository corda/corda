package net.corda.tools.error.codes.server.commons.logging

internal class Slf4jLoggerAdapter<CONTEXT : LoggingContext> private constructor(private val logger: org.slf4j.Logger) : Logger<CONTEXT> {

    companion object {

        @JvmStatic
        fun <CONTEXT : LoggingContext> wrap(logger: org.slf4j.Logger): Logger<CONTEXT> {

            return Slf4jLoggerAdapter(logger)
        }
    }

    override fun log(context: CONTEXT?, level: Logger.Level, message: String, vararg args: Any?) = level.enabled()?.let(::printFormatted)?.invoke(context?.applyTo(message) ?: message, args) ?: Unit

    override fun log(context: CONTEXT?, level: Logger.Level, message: String, throwable: Throwable) = level.enabled()?.let(::printWithError)?.invoke(context?.applyTo(message) ?: message, throwable) ?: Unit

    override fun log(context: CONTEXT?, level: Logger.Level, message: String, vararg argSuppliers: () -> Any?) = log(context, level, message, *argSuppliers.map { it.invoke() }.toTypedArray())

    override fun name(): String = logger.name

    override fun isEnabled(level: Logger.Level): Boolean {

        return level.enabled() != null
    }

    private fun Logger.Level.enabled(): Logger.Level? {

        val enabled = when (this) {
            Logger.Level.TRACE -> logger.isTraceEnabled
            Logger.Level.DEBUG -> logger.isDebugEnabled
            Logger.Level.INFO -> logger.isInfoEnabled
            Logger.Level.WARN -> logger.isWarnEnabled
            Logger.Level.ERROR -> logger.isErrorEnabled
            else -> throw IllegalStateException("Unknown level $this.")
        }
        return if (enabled) this else null
    }

    private fun printFormatted(level: Logger.Level): (String, Array<out Any?>) -> Unit {

        return when (level) {
            Logger.Level.TRACE -> { format, arguments -> logger.trace(format, *arguments) }
            Logger.Level.DEBUG -> { format, arguments -> logger.debug(format, *arguments) }
            Logger.Level.INFO -> { format, arguments -> logger.info(format, *arguments) }
            Logger.Level.WARN -> { format, arguments -> logger.warn(format, *arguments) }
            Logger.Level.ERROR -> { format, arguments -> logger.error(format, *arguments) }
            else -> throw IllegalStateException("Unknown level $level.")
        }
    }

    private fun printWithError(level: Logger.Level): (String, Throwable) -> Unit {

        return when (level) {
            Logger.Level.TRACE -> logger::trace
            Logger.Level.DEBUG -> logger::debug
            Logger.Level.INFO -> logger::info
            Logger.Level.WARN -> logger::warn
            Logger.Level.ERROR -> logger::error
            else -> throw IllegalStateException("Unknown level $level.")
        }
    }

    private fun CONTEXT.applyTo(message: String) = "$message $description"
}