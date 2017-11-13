package net.corda.node.services.security

import net.corda.nodeapi.User
import org.apache.shiro.mgt.DefaultSecurityManager
import org.apache.shiro.subject.Subject
import org.apache.shiro.subject.SimplePrincipalCollection

/**
 * Build Subject instance with same username and permissions of a [User] instance
 */
fun buildSubject(user : User) : Subject {
    // There is no directly constructible implementation of Subject,
    // so we use the trick of creating a Realm containing just the
    // admin user and then apply Subject.Builder
    val realm = RPCRealmFactory.buildInMemory(listOf(user))
    return Subject.Builder(DefaultSecurityManager(realm))
            .authenticated(true)
            .principals(SimplePrincipalCollection(user.username, realm.name))
            .buildSubject()
}

/**
 * Helper function to create an authenticated subject with given [username]
 * and all permissions
 */
fun buildAdminSubject(username : String) : Subject {
    val user = User(username = username,
                    password = "",
                    permissions = setOf("ALL"))
    return buildSubject(user)
}
