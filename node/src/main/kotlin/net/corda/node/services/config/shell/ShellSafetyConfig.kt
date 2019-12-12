package net.corda.node.services.config.shell

import net.corda.core.crypto.SecureHash
import net.corda.node.services.config.NodeConfiguration
import net.corda.nodeapi.internal.config.User

const val SAFE_INTERNAL_SHELL_PERMISSION = ""
const val UNSAFE_INTERNAL_SHELL_PERMISSION = "ALL"

const val INTERNAL_SHELL_USER = "internalShell"
val internalShellPassword: String by lazy { SecureHash.randomSHA256().toString() }

fun internalShellPermissions(safe: Boolean): Set<String> {
    return setOf(if (safe) { SAFE_INTERNAL_SHELL_PERMISSION } else { UNSAFE_INTERNAL_SHELL_PERMISSION })
}

fun determineUnsafeUsers(config: NodeConfiguration): Set<String> {
    var unsafeUsers = HashSet<String>()
    for (user in config.rpcUsers) {
        for (perm in user.permissions) {
            if (perm == UNSAFE_INTERNAL_SHELL_PERMISSION) {
                unsafeUsers.add(user.username)
                break
            }
        }
    }

    checkSecurityUsers(config, unsafeUsers)
    return unsafeUsers
}

private fun checkSecurityPermListForUser(user: User, unsafeUsers: MutableSet<String>) {
    for (perm in user.permissions) {
        if (perm == UNSAFE_INTERNAL_SHELL_PERMISSION) {
            unsafeUsers.add(user.username)
            return
        }
    }
}

private fun checkSecurityForUserList(users: List<User>, unsafeUsers: MutableSet<String>) {
    for (user in users) {
        checkSecurityPermListForUser(user, unsafeUsers)
    }
}

private fun checkSecurityUsers(config: NodeConfiguration, unsafeUsers: MutableSet<String>) {
    val users = config.security?.authService?.dataSource?.users
    if (users != null) {
        checkSecurityForUserList(users, unsafeUsers)
    }
}

