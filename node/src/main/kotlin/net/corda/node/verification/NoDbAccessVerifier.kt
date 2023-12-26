package net.corda.node.verification

import net.corda.core.internal.verification.Verifier
import net.corda.nodeapi.internal.persistence.withoutDatabaseAccess

class NoDbAccessVerifier(private val delegate: Verifier) : Verifier {
    override fun verify() {
        withoutDatabaseAccess {
            delegate.verify()
        }
    }
}
