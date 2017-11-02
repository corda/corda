@file:JvmName("RPCServerStructures")

package net.corda.node.services.messaging

import net.corda.client.rpc.PermissionException
import net.corda.node.services.Permissions.Companion.all
import net.corda.nodeapi.ArtemisMessagingComponent

/** Helper method which checks that the current RPC user is entitled for the given permission. Throws a [PermissionException] otherwise. */
fun RpcContext.requirePermission(permission: String): RpcContext = requireEitherPermission(setOf(permission))

/** Helper method which checks that the current RPC user is entitled with any of the given permissions. Throws a [PermissionException] otherwise. */
fun RpcContext.requireEitherPermission(permissions: Set<String>): RpcContext {
    // TODO remove the NODE_USER condition once webserver doesn't need it
    val currentUserPermissions = currentUser.permissions
    if (currentUser.username != ArtemisMessagingComponent.NODE_USER && currentUserPermissions.intersect(permissions + all()).isEmpty()) {
        throw PermissionException("User not permissioned with any of $permissions, permissions are $currentUserPermissions")
    }
    return this
}