package net.corda.core.node.services.vault

import net.corda.core.DoNotImplement
import org.hibernate.Session

/**
 * Represents transaction performed on the Vault storage.
 */
@DoNotImplement
interface VaultTransaction {
    val session: Session
}