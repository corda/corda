package net.corda.node.internal.serialization.testutils

import net.corda.core.context.AuthServiceId
import net.corda.node.internal.security.AuthorizingSubject
import net.corda.node.internal.security.Password
import net.corda.node.internal.security.RPCSecurityManager

class TestRPCSecurityManager : RPCSecurityManager{
    override val id: AuthServiceId
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

    override fun authenticate(principal: String, password: Password): AuthorizingSubject {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun buildSubject(principal: String): AuthorizingSubject {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun close() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}