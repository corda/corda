package net.corda.testing.node

/** Object encapsulating a node rpc user and their associated permissions for use when testing */
data class User(
        val username: String,
        val password: String,
        val permissions: Set<String>)