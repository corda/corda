@file:JvmName("RPCServerStructures")

package net.corda.node.services.messaging

import net.corda.nodeapi.ArtemisMessagingComponent
import net.corda.nodeapi.PermissionException

/** Helper method which checks that the current RPC user is entitled for the given permission. Throws a [PermissionException] otherwise. */
fun RpcContext.requirePermission(permission: String) {
    // TODO remove the NODE_USER condition once webserver doesn't need it
    val currentUserPermissions = currentUser.permissions
    if (currentUser.username != ArtemisMessagingComponent.NODE_USER && currentUserPermissions.intersect(listOf(permission, "ALL")).isEmpty()) {
        throw PermissionException("User not permissioned for $permission, permissions are $currentUserPermissions")
    }
}
