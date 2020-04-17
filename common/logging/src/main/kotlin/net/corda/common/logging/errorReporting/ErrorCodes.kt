package net.corda.common.logging.errorReporting

/**
 * A collection of error codes.
 *
 * Types implementing this are required to be enum classes to be used in an error.
 */
interface ErrorCodes {
    /**
     * The namespace of this collection of errors.
     *
     * These are used to partition errors into categories, e.g. "database" or "cordapp". Namespaces should be unique, which can be enforced
     * by using enum elements.
     */
    val namespace: String
}