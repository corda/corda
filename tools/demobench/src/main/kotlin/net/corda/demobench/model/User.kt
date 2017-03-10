package net.corda.demobench.model

data class User(val user: String, val password: String, val permissions: List<String>) {
    fun toMap() = mapOf(
        "user" to user,
        "password" to password,
        "permissions" to permissions
    )
}

@Suppress("UNCHECKED_CAST")
fun toUser(map: Map<String, Any>) = User(
    map.getOrElse("user", { "none" }) as String,
    map.getOrElse("password", { "none" }) as String,
    map.getOrElse("permissions", { emptyList<String>() }) as List<String>
)

fun user(name: String) = User(name, "letmein", listOf("ALL"))
