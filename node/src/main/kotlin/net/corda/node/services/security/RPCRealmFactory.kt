package net.corda.node.services.security

import net.corda.node.services.config.NodeConfiguration
import net.corda.nodeapi.User
import org.apache.shiro.authc.AuthenticationInfo
import org.apache.shiro.authc.AuthenticationToken
import org.apache.shiro.authc.SimpleAuthenticationInfo
import org.apache.shiro.authc.credential.SimpleCredentialsMatcher
import org.apache.shiro.authz.AuthorizationInfo
import org.apache.shiro.authz.SimpleAuthorizationInfo
import org.apache.shiro.realm.AuthorizingRealm
import org.apache.shiro.subject.PrincipalCollection
import org.apache.shiro.subject.SimplePrincipalCollection

/**
 * A factory constructing implementations of AuthorizingRealm
 * bound to a Node configuration
 */
object RPCRealmFactory {

    /**
     * Construct Realm object configured in the given [config]
     */
    fun build(config : NodeConfiguration) : AuthorizingRealm {
        /*
         * Run-time switch to actual AuthorizingRealm implementation used in Node.
         *
         * Currently we only support authorization/authentication data
         * explicitly reported in the node configuration.
         */
        return buildInMemory(config.rpcUsers)
    }

    /**
     * Get an implementation of AuthorizingRealm taking data
     * from input list of [users]
     */
    fun buildInMemory(users : List<User>) : AuthorizingRealm =
        InMemoryRealm(users, DOMAIN)

    val DOMAIN = "RPC"
}

/*
 * An implementation of AuthorizingRealm serving data
 * from an input list of User
 */
internal class InMemoryRealm : AuthorizingRealm {

    constructor(users: List<User>, realmId : String) {
        authorizationInfoByUser = users.associate {
            it.username to SimpleAuthorizationInfo().apply {
                objectPermissions = it.permissions.map { RPCPermission(it) }.toSet()
                roles = emptySet<String>()
                stringPermissions = emptySet<String>()
            }
        }
        authenticationInfoByUser = users.associate {
            it.username to SimpleAuthenticationInfo().apply {
                credentials = it.password
                principals = SimplePrincipalCollection(it.username, realmId)
            }
        }
        // Plain credential matcher
        credentialsMatcher = SimpleCredentialsMatcher()
    }

    /*
     * Methods from AuthorizingRealm interface used by Shiro to query
     * for authentication/authorization data for a given user
     */
    override fun doGetAuthenticationInfo(token: AuthenticationToken) =
            authenticationInfoByUser.getValue(token.credentials as String)


    override fun doGetAuthorizationInfo(principals: PrincipalCollection) =
            authorizationInfoByUser.getValue(principals.primaryPrincipal as String)

    private val authorizationInfoByUser: Map<String, AuthorizationInfo>
    private val authenticationInfoByUser: Map<String, AuthenticationInfo>
}