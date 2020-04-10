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
enum class NodeDatabaseErrors {
    COULD_NOT_CONNECT,
    MISSING_DRIVERS,
    FAILED_STARTUP,
    PASSWORD_REQUIRED_FOR_H2
}

/**
 * Errors related to loading of Cordapps
 */
enum class CordappErrors {
    DUPLICATE_CORDAPPS_INSTALLED
}