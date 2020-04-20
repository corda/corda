package net.corda.common.logging.errorReporting

abstract class ErrorReportingException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Occurs when reporting is requested before the error reporting code has been initialized
 */
class ReportingUninitializedException : ErrorReportingException("Error reporting is uninitialized")

/**
 * Occurs when no error context provider is supplied while initializing error reporting
 */
class NoContextProviderSuppliedException
    : ErrorReportingException("No error context provider was supplied when initializing error reporting")

/**
 * Occurs if the error reporting framework has been initialized twice
 */
class DoubleInitializationException : ErrorReportingException("Error reporting has previously been initialized")