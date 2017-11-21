package net.corda.node.services.messaging

import net.corda.client.rpc.PermissionException
import net.corda.core.context.InvocationContext
import net.corda.node.services.Permissions
import net.corda.nodeapi.internal.ArtemisMessagingComponent

data class RpcAuthContext(val invocation: InvocationContext, val grantedPermissions: RpcPermissions) {

    fun requirePermission(permission: String) = requireEitherPermission(setOf(permission))

    fun requireEitherPermission(permissions: Set<String>): RpcAuthContext {

        // TODO remove the NODE_USER condition once webserver and shell won't need it anymore
        if (invocation.principal().name != ArtemisMessagingComponent.NODE_USER && !grantedPermissions.coverAny(permissions)) {
            throw PermissionException("User not permissioned with any of $permissions, permissions are ${this.grantedPermissions}.")
        }
        return this
    }
}

data class RpcPermissions(private val values: Set<String> = emptySet()) {

    companion object {
        val NONE = RpcPermissions()
        val ALL = RpcPermissions(setOf("ALL"))
    }

    fun coverAny(permissions: Set<String>) = !values.intersect(permissions + Permissions.all()).isEmpty()
}