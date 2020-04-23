package net.corda.common.logging.errorReporting

/**
 * A type representing an error condition.
 *
 * Error codes should be used in situations where an error is expected and information can be provided back to the user about what they've
 * done wrong. Each error code should have a resource bundle defined for it, which contains set of properties that define the error string
 * in different languages. See the resource bundles in common/logging/src/main/resources/errorReporting for more details.
 */
interface ErrorCode<CODES> where CODES: ErrorCodes, CODES: Enum<CODES> {

    /**
     * The error code.
     *
     * Error codes are used to indicate what sort of error occurred. A unique code should be returned for each possible
     * error condition that could be reported within the defined namespace. The code should very briefly describe what has gone wrong, e.g.
     * "failed-to-store" or "connection-unavailable".
     */
    val code: CODES

    /**
     * Parameters to pass to the string template when reporting this error. The corresponding template that defines the error string in the
     * resource bundle must be expecting this list of parameters. Parameters should be in the order required by the message template - for
     * example, if the message template is "This error has argument {0} and argument {1}", the first element of this list will be placed
     * into {0}.
     */
    val parameters: List<Any>
}