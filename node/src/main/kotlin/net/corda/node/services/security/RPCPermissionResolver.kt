package net.corda.node.services.security

import org.apache.shiro.authz.Permission
import org.apache.shiro.authz.permission.PermissionResolver

class RPCPermissionResolver : PermissionResolver {

    override fun resolvePermission(permissionString: String?): Permission {
        return RPCPermission(permissionString!!)
    }

}