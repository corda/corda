package net.corda.core.logging

import org.slf4j.spi.LocationAwareLogger

internal class SLF4JLoggerAdapter(private val delegate: org.slf4j.Logger) : Logger {

    companion object {
        val fqcn = SLF4JLoggerAdapter::class.qualifiedName
    }

    override fun log(level: Logger.Level, loggerName: String, message: String, error: Throwable?) = level.doLog(message, error)

    override val name: String = delegate.name

    override fun isEnabled(level: Logger.Level) = level.enabled

    private val Logger.Level.enabled: Boolean
        get() {
            return when (this) {
                is Logger.Level.TRACE -> delegate.isTraceEnabled
                is Logger.Level.DEBUG -> delegate.isDebugEnabled
                is Logger.Level.INFO -> delegate.isInfoEnabled
                is Logger.Level.WARN -> delegate.isWarnEnabled
                is Logger.Level.ERROR -> delegate.isErrorEnabled
            }
        }

    private fun Logger.Level.doLog(message: String, error: Throwable?) {

        when (delegate) {
            is LocationAwareLogger -> delegate.log(null, fqcn, intValue, message, null, error)
            else -> when (this) {
                is Logger.Level.TRACE -> error?.let { delegate.trace(message, it) } ?: delegate.trace(message)
                is Logger.Level.DEBUG -> error?.let { delegate.debug(message, it) } ?: delegate.debug(message)
                is Logger.Level.INFO -> error?.let { delegate.info(message, it) } ?: delegate.info(message)
                is Logger.Level.WARN -> error?.let { delegate.warn(message, it) } ?: delegate.warn(message)
                is Logger.Level.ERROR -> error?.let { delegate.error(message, it) } ?: delegate.error(message)
            }
        }
    }

    private val Logger.Level.intValue: Int
        get() {
            return when (this) {
                is Logger.Level.TRACE -> LocationAwareLogger.TRACE_INT
                is Logger.Level.DEBUG -> LocationAwareLogger.DEBUG_INT
                is Logger.Level.INFO -> LocationAwareLogger.INFO_INT
                is Logger.Level.WARN -> LocationAwareLogger.WARN_INT
                is Logger.Level.ERROR -> LocationAwareLogger.ERROR_INT
            }
        }
}