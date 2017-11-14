package net.corda.node.services.security

import net.corda.node.services.config.NodeConfiguration
import org.apache.shiro.config.IniSecurityManagerFactory
import org.apache.shiro.mgt.DefaultSecurityManager
import org.apache.shiro.mgt.SecurityManager

object RPCSecurityManagerFactory {

    fun build(config : NodeConfiguration) : SecurityManager {
        if (config.shiroPluginIni != null) {
            return IniSecurityManagerFactory(config.shiroPluginIni).instance
        }
        else {
            return DefaultSecurityManager(RPCRealmFactory.buildInMemory(config.rpcUsers))
        }
    }
}