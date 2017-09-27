@file:JvmName("User")

package net.corda.demobench.model

import net.corda.core.internal.uncheckedCast
import net.corda.nodeapi.User
import java.util.*

fun toUser(map: Map<String, Any>) = User(
        map.getOrElse("username", { "none" }) as String,
        map.getOrElse("password", { "none" }) as String,
        LinkedHashSet(uncheckedCast<Any, Collection<String>>(map.getOrElse("permissions", { emptyList<String>() })))
)

fun user(name: String) = User(name, "letmein", setOf("ALL"))
