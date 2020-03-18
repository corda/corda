package net.corda.common.logging.errorReporting

import org.slf4j.Logger

class UnsetErrorReporter : ErrorReporter {

    override fun report(error: ErrorCode, logger: Logger) {
        logger.warn("Error reporter is not yet set. Localised error reporting is not available, but an attempt was made to log a code")
        logger.error("Code: ${error.namespace}:${error.code}")
    }
}