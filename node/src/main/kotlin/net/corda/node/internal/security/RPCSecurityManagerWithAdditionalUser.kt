package net.corda.node.internal.security

import net.corda.nodeapi.internal.config.User
import org.apache.shiro.mgt.DefaultSecurityManager
import org.apache.shiro.subject.SimplePrincipalCollection
import javax.security.auth.login.FailedLoginException

/**
 * Wrapper for [RPCSecurityManager] which creates in-memory [AuthorizingSubject] for [User].
 * Can be used to add on a specific [User] on top of the principals provided by the [RPCSecurityManager] realm.
 */
class RPCSecurityManagerWithAdditionalUser(private val delegate: RPCSecurityManager, private val user: User) : RPCSecurityManager by delegate {

    private val realmId = user.username + "Realm"
    private val shellAuthorizingSubject = ShiroAuthorizingSubject(subjectId = SimplePrincipalCollection(user.username, id.value),
            manager = DefaultSecurityManager(InMemoryRealm(listOf(user), realmId)))

    @Throws(FailedLoginException::class)
    override fun authenticate(principal: String, password: Password): AuthorizingSubject =
            if (user.username == principal && user.password == password.valueAsString) {
                shellAuthorizingSubject
            } else {
                delegate.authenticate(principal, password)
            }

    override fun buildSubject(principal: String): AuthorizingSubject =
            if (user.username == principal) {
                shellAuthorizingSubject
            } else {
                delegate.buildSubject(principal)
            }
}
