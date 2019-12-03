package net.corda.core.node.services.vault

import net.corda.core.DoNotImplement
import org.hibernate.Session

/**
 * Represents scope for the operation when JPA [Session] been created, i.e. transaction started.
 */
@DoNotImplement
interface SessionScope {
    val session: Session
}