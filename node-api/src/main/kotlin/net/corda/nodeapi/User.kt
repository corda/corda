package net.corda.nodeapi

import net.corda.nodeapi.config.OldConfig
import net.corda.nodeapi.config.toConfig

data class User(
        @OldConfig("user")
        val username: String,
        val password: String,
        val permissions: Set<String>) {
    override fun toString(): String = "${javaClass.simpleName}($username, permissions=$permissions)"
    @Deprecated("Use toConfig().root().unwrapped() instead")
    fun toMap(): Map<String, Any> = toConfig().root().unwrapped()
}
