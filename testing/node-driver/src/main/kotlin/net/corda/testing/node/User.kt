package net.corda.testing.node

data class User(
        val username: String,
        val password: String,
        val permissions: Set<String>)