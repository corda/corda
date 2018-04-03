package net.corda.node.internal.security

import net.corda.node.services.config.shell.localShellUser
import net.corda.nodeapi.internal.config.User
import org.apache.shiro.mgt.DefaultSecurityManager
import org.apache.shiro.subject.SimplePrincipalCollection
import javax.security.auth.login.FailedLoginException

/**
 * Wrapper for [RPCSecurityManager] which gives [User] unlimited access.
 * By the default grants permissions to "shell/shell" user/password.
 */
class DevModeRPCSecurityManagerImpl(private val delegate: RPCSecurityManager, private val user: User = localShellUser()) : RPCSecurityManager by delegate {

    private val REALM_ID =  "devModeRealm"
    private val shellRealm = InMemoryRealm(listOf(user), REALM_ID)
    private val shellManager = DefaultSecurityManager(shellRealm)
    private val shellAuthorizingSubject = ShiroAuthorizingSubject(subjectId = SimplePrincipalCollection(user.username, id.value), manager = shellManager)

    @Throws(FailedLoginException::class)
    override fun authenticate(principal: String, password: Password): AuthorizingSubject {
        return if(user.username.equals(principal, ignoreCase = true) && user.password.equals(password.valueAsString, ignoreCase = true)) {
            shellAuthorizingSubject
        } else {
            delegate.authenticate(principal, password)
        }
    }

    override fun buildSubject(principal: String): AuthorizingSubject =
            if(user.username.equals(principal, ignoreCase = true)) {
                shellAuthorizingSubject
            } else {
                delegate.buildSubject(principal)
            }
}
