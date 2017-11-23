package net.corda.node.internal.security

import net.corda.core.context.AuthServiceId
import net.corda.nodeapi.User
import org.apache.shiro.authc.UsernamePasswordToken
import org.apache.shiro.mgt.DefaultSecurityManager
import org.apache.shiro.mgt.SecurityManager
import org.apache.shiro.subject.SimplePrincipalCollection
import org.apache.shiro.subject.Subject

/**
 * Implement [RPCSecurityManager] adapting [org.apache.shiro.mgt.SecurityManager]
 */
open class ShiroRPCSecurityManager(override val id : AuthServiceId,
                                   private val manager : SecurityManager) : RPCSecurityManager {

    override fun authenticate(principal : String, password : CharArray) : AuthorizingSubject {
        val authToken = UsernamePasswordToken(principal, password)
        val authSubject = Subject.Builder(manager).buildSubject()
        authSubject.login(authToken)
        return ShiroAuthorizingSubject(authSubject)
    }

    override fun tryAuthenticate(principal : String, password: CharArray) : AuthorizingSubject? {
        val authToken = UsernamePasswordToken(principal, password)
        var authSubject = Subject.Builder(manager).buildSubject()
        try {
            authSubject.login(authToken)
        }
        catch (e : Exception) {
            authSubject = null
        }
        return ShiroAuthorizingSubject(authSubject)
    }

    override fun resolveSubject(principal : String) : AuthorizingSubject {
        val subject = Subject.Builder(manager)
                .authenticated(true)
                .principals(SimplePrincipalCollection(principal, id.value))
                .buildSubject()
        return ShiroAuthorizingSubject(subject)
    }
}

/**
 * An RPCSecurityManager implementation serving data from a given list of [User]
 */
class RPCSecurityManagerInMemory(override val id : AuthServiceId,
                                 private val users : List<User>)
    : ShiroRPCSecurityManager(
        manager = DefaultSecurityManager(InMemoryRealm(users, id.value)),
        id = id)

