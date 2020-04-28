package net.corda.common.logging.errorReporting

/**
 * Namespaces for errors within the node.
 */
enum class NodeNamespaces {
    DATABASE,
    CORDAPP
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

/**
 * Errors related to loading of Cordapps
 */
enum class CordappErrors : ErrorCodes {
    DUPLICATE_CORDAPPS_INSTALLED,
    MULTIPLE_CORDAPPS_FOR_FLOW,
    MISSING_VERSION_ATTRIBUTE,
    INVALID_VERSION_IDENTIFIER;

    override val namespace = NodeNamespaces.CORDAPP.toString()
}