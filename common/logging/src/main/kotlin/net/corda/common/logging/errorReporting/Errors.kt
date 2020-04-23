package net.corda.common.logging.errorReporting

/**
 * Namespaces for errors within the node.
 */
enum class NodeNamespaces {
    DATABASE
}

/**
 * Errors related to database connectivity
 */
enum class NodeDatabaseErrors : ErrorCodes {
    COULD_NOT_CONNECT,
    MISSING_DRIVER,
    FAILED_STARTUP,
    PASSWORD_REQUIRED_FOR_H2;

    override val namespace = NodeNamespaces.DATABASE.toString()
}