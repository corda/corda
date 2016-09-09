package com.r3corda.core.contracts

import com.r3corda.core.crypto.Party
import com.r3corda.core.serialization.OpaqueBytes
import java.security.PublicKey
import java.util.*

/**
 * A command from the monitoring client, to the node.
 *
 * @param id ID used to tag event(s) resulting from a command.
 */
sealed class ClientToServiceCommand(val id: UUID) {
    /**
     * Issue cash state objects.
     *
     * @param amount the amount of currency to issue on to the ledger.
     * @param issueRef the reference to specify on the issuance, used to differentiate pools of cash. Convention is
     * to use the single byte "0x01" as a default.
     * @param recipient the party to issue the cash to.
     * @param notary the notary to use for this transaction.
     * @param id the ID to be provided in events resulting from this request.
     */
    class IssueCash(val amount: Amount<Currency>,
                    val issueRef: OpaqueBytes,
                    val recipient: Party,
                    val notary: Party,
                    id: UUID = UUID.randomUUID()) : ClientToServiceCommand(id)

    /**
     * Pay cash to someone else.
     *
     * @param amount the amount of currency to issue on to the ledger.
     * @param recipient the party to issue the cash to.
     * @param id the ID to be provided in events resulting from this request.
     */
    class PayCash(val amount: Amount<Issued<Currency>>, val recipient: Party,
                  id: UUID = UUID.randomUUID()) : ClientToServiceCommand(id)

    /**
     * Exit cash from the ledger.
     *
     * @param amount the amount of currency to exit from the ledger.
     * @param issueRef the reference previously specified on the issuance.
     * @param id the ID to be provided in events resulting from this request.
     */
    class ExitCash(val amount: Amount<Currency>, val issueRef: OpaqueBytes,
                   id: UUID = UUID.randomUUID()) : ClientToServiceCommand(id)
}
