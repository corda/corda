package net.corda.common.logging.errorReporting

abstract class ErrorReportingException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Occurs when reporting is requested before the error reporting code has been initialized
 */
class ReportingUninitializedException : ErrorReportingException("Error reporting is uninitialized")