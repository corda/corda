package net.corda.testing.node

/**
 * Object encapsulating a node rpc user and their associated permissions for use when testing using the [driver]
 *
 * @property username The rpc user's username
 * @property password The rpc user's password
 * @property permissions A [List] of [String] detailing the [User]'s permissions
 * */
data class User(
        val username: String,
        val password: String,
        val permissions: Set<String>)