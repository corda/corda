package net.corda.nodeapi.internal

import net.corda.nodeapi.config.OldConfig

data class User(
        @OldConfig("user")
        val username: String,
        val password: String,
        val permissions: Set<String>) {
    fun toMap() = mapOf(
            "username" to username,
            "password" to password,
            "permissions" to permissions
    )
    override fun toString(): String = "${javaClass.simpleName}($username, permissions=$permissions)"
}