package net.corda.node.internal.context

import net.corda.core.context.Trace
import net.corda.core.identity.CordaX500Name

class InvocationContext(private val user: User, private val trace: Trace = Trace(), private val externalTrace: Trace? = null)

data class User(val id: Id, val legalIdentity: LegalIdentity) {

    data class Id(val value: String, val store: String)

    data class LegalIdentity(val value: CordaX500Name)
    // in case we need different user types in corda (to provide polymorphic behaviour) we can an extra field here
}