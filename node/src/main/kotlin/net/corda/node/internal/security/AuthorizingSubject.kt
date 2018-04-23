/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.internal.security

/**
 * Provides permission checking for the subject identified by the given [principal].
 */
interface AuthorizingSubject {

    /**
     * Identity of underlying subject
     */
    val principal: String

    /**
     * Determines if the underlying subject is entitled to perform a certain action,
     * (e.g. an RPC invocation) represented by an [action] string followed by an
     * optional list of arguments.
     */
    fun isPermitted(action : String, vararg arguments : String) : Boolean
}

/**
 * An implementation of [AuthorizingSubject] permitting all actions
 */
class AdminSubject(override val principal : String) : AuthorizingSubject {

    override fun isPermitted(action: String, vararg arguments: String) = true

}