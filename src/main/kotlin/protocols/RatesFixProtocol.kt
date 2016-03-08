/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package protocols

import co.paralleluniverse.fibers.Suspendable
import core.*
import core.crypto.DigitalSignature
import core.messaging.LegallyIdentifiableNode
import core.messaging.SingleMessageRecipient
import core.protocols.ProtocolLogic
import java.math.BigDecimal
import java.util.*

// This code is unit tested in NodeInterestRates.kt

/**
 * This protocol queries the given oracle for an interest rate fix, and if it is within the given tolerance embeds the
 * fix in the transaction and then proceeds to get the oracle to sign it. Although the [call] method combines the query
 * and signing step, you can run the steps individually by constructing this object and then using the public methods
 * for each step.
 *
 * @throws FixOutOfRange if the returned fix was further away from the expected rate by the given amount.
 */
class RatesFixProtocol(private val tx: TransactionBuilder,
                       private val oracle: LegallyIdentifiableNode,
                       private val fixOf: FixOf,
                       private val expectedRate: BigDecimal,
                       private val rateTolerance: BigDecimal) : ProtocolLogic<Unit>() {
    companion object {
        val TOPIC = "platform.rates.interest.fix"
    }

    class FixOutOfRange(val byAmount: BigDecimal) : Exception()

    data class QueryRequest(val queries: List<FixOf>, val replyTo: SingleMessageRecipient, val sessionID: Long)
    data class SignRequest(val tx: WireTransaction, val replyTo: SingleMessageRecipient, val sessionID: Long)

    @Suspendable
    override fun call() {
        val fix = query()
        checkFixIsNearExpected(fix)
        tx.addCommand(fix, oracle.identity.owningKey)
        tx.addSignatureUnchecked(sign())
    }

    private fun checkFixIsNearExpected(fix: Fix) {
        val delta = (fix.value - expectedRate).abs()
        if (delta > rateTolerance) {
            // TODO: Kick to a user confirmation / ui flow if it's out of bounds instead of raising an exception.
            throw FixOutOfRange(delta)
        }
    }

    @Suspendable
    fun sign(): DigitalSignature.LegallyIdentifiable {
        val sessionID = random63BitValue()
        val wtx = tx.toWireTransaction()
        val req = SignRequest(wtx, serviceHub.networkService.myAddress, sessionID)
        val resp = sendAndReceive<DigitalSignature.LegallyIdentifiable>(TOPIC + ".sign", oracle.address, 0, sessionID, req)

        return resp.validate { sig ->
            check(sig.signer == oracle.identity)
            tx.checkSignature(sig)
            sig
        }
    }

    @Suspendable
    fun query(): Fix {
        val sessionID = random63BitValue()
        val req = QueryRequest(listOf(fixOf), serviceHub.networkService.myAddress, sessionID)
        val resp = sendAndReceive<ArrayList<Fix>>(TOPIC + ".query", oracle.address, 0, sessionID, req)

        return resp.validate {
            val fix = it.first()
            // Check the returned fix is for what we asked for.
            check(fix.of == fixOf)
            fix
        }
    }
}
