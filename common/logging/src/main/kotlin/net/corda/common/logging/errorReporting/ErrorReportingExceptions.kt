package net.corda.common.logging.errorReporting

abstract class ErrorReportingException(message: String, cause: Throwable? = null) : Exception(message, cause)

class ReportingUninitializedException : ErrorReportingException("Error reporting is uninitialized")