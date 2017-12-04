package net.corda.node.internal.security

import net.corda.core.context.AuthServiceId
import net.corda.node.internal.security.AuthorizingSubject
import javax.security.auth.login.FailedLoginException

/**
 * Manage security of RPC users, providing logic for user
 * authentication and authorization.
 */
interface RPCSecurityManager : AutoCloseable {

    /**
     * Perform user authentication. If the authentication is successful
     * the returns values is an [AuthorizingSubject] containing the permissions
     * of the authenticated user. If the authentication fails an exception
     * is thrown.
     */
    fun authenticate(principal : String, password : CharArray) =
        tryAuthenticate(principal, password) ?: throw FailedLoginException("Authentication fail for ${principal}")

    /**
     * Non-throwing version of authenticate, returning null instead of throwing
     * in case of authentication failure
     */
    fun tryAuthenticate(principal : String, password: CharArray) : AuthorizingSubject?

    /**
     * Construct an AuthorizingSubject instance allowing to perform permission checks
     * on the given principal.
     */
    fun subjectInSession(principal : String) : AuthorizingSubject

    /**
     *  An identifier associated to this security service
     */
    val id: AuthServiceId
}