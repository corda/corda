package net.corda.nodeapi.internal.config

data class User(
        @OldConfig("user")
        val username: String,
        val password: String,
        val permissions: Set<String>) {
    override fun toString(): String = "${javaClass.simpleName}($username, permissions=$permissions)"
    @Deprecated("Use toConfig().root().unwrapped() instead", ReplaceWith("toConfig().root().unwrapped()"))
    fun toMap(): Map<String, Any> = toConfig().root().unwrapped()
}
