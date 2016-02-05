/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package contracts.protocols

import co.paralleluniverse.fibers.Suspendable
import com.google.common.util.concurrent.ListenableFuture
import contracts.Cash
import contracts.sumCashBy
import core.*
import core.messaging.LegallyIdentifiableNode
import core.messaging.ProtocolStateMachine
import core.messaging.SingleMessageRecipient
import core.messaging.StateMachineManager
import core.node.TimestamperClient
import core.serialization.deserialize
import core.utilities.trace
import java.security.KeyPair
import java.security.PublicKey
import java.time.Instant

/**
 * This asset trading protocol implements a "delivery vs payment" type swap. It has two parties (B and S for buyer
 * and seller) and the following steps:
 *
 * 1. S sends the [StateAndRef] pointing to what they want to sell to B, along with info about the price they require
 *    B to pay. For example this has probably been agreed on an exchange.
 * 2. B sends to S a [SignedWireTransaction] that includes the state as input, B's cash as input, the state with the new
 *    owner key as output, and any change cash as output. It contains a single signature from B but isn't valid because
 *    it lacks a signature from S authorising movement of the asset.
 * 3. S signs it and hands the now finalised SignedWireTransaction back to B.
 *
 * Assuming no malicious termination, they both end the protocol being in posession of a valid, signed transaction
 * that represents an atomic asset swap.
 *
 * Note that it's the *seller* who initiates contact with the buyer, not vice-versa as you might imagine.
 *
 * To initiate the protocol, use either the [runBuyer] or [runSeller] methods, depending on which side of the trade
 * your node is taking. These methods return a future which will complete once the trade is over and a fully signed
 * transaction is available: you can either block your thread waiting for the protocol to complete by using
 * [ListenableFuture.get] or more usefully, register a callback that will be invoked when the time comes.
 *
 * To see an example of how to use this class, look at the unit tests.
 */
object TwoPartyTradeProtocol {
    val TRADE_TOPIC = "platform.trade"

    fun runSeller(smm: StateMachineManager, timestampingAuthority: LegallyIdentifiableNode,
                  otherSide: SingleMessageRecipient, assetToSell: StateAndRef<OwnableState>, price: Amount,
                  myKeyPair: KeyPair, buyerSessionID: Long): ListenableFuture<Pair<WireTransaction, LedgerTransaction>> {
        val seller = Seller(otherSide, timestampingAuthority, assetToSell, price, myKeyPair, buyerSessionID)
        smm.add("$TRADE_TOPIC.seller", seller)
        return seller.resultFuture
    }

    fun runBuyer(smm: StateMachineManager, timestampingAuthority: LegallyIdentifiableNode,
                 otherSide: SingleMessageRecipient, acceptablePrice: Amount, typeToBuy: Class<out OwnableState>,
                 sessionID: Long): ListenableFuture<Pair<WireTransaction, LedgerTransaction>> {
        val buyer = Buyer(otherSide, timestampingAuthority.identity, acceptablePrice, typeToBuy, sessionID)
        smm.add("$TRADE_TOPIC.buyer", buyer)
        return buyer.resultFuture
    }

    class Seller(val otherSide: SingleMessageRecipient,
                 val timestampingAuthority: LegallyIdentifiableNode,
                 val assetToSell: StateAndRef<OwnableState>,
                 val price: Amount,
                 val myKeyPair: KeyPair,
                 val buyerSessionID: Long) : ProtocolStateMachine<Pair<WireTransaction, LedgerTransaction>>() {
        @Suspendable
        override fun call(): Pair<WireTransaction, LedgerTransaction> {
            val sessionID = random63BitValue()

            // Make the first message we'll send to kick off the protocol.
            val hello = SellerTradeInfo(assetToSell, price, myKeyPair.public, sessionID)

            val partialTX = sendAndReceive(TRADE_TOPIC, otherSide, buyerSessionID, sessionID, hello, SignedWireTransaction::class.java)
            logger.trace { "Received partially signed transaction" }

            partialTX.verifySignatures()
            val wtx: WireTransaction = partialTX.txBits.deserialize()

            requireThat {
                "transaction sends us the right amount of cash" by (wtx.outputStates.sumCashBy(myKeyPair.public) == price)
                // There are all sorts of funny games a malicious secondary might play here, we should fix them:
                //
                // - This tx may attempt to send some assets we aren't intending to sell to the secondary, if
                //   we're reusing keys! So don't reuse keys!
                // - This tx may not be valid according to the contracts of the input states, so we must resolve
                //   and fully audit the transaction chains to convince ourselves that it is actually valid.
                // - This tx may include output states that impose odd conditions on the movement of the cash,
                //   once we implement state pairing.
                //
                // but the goal of this code is not to be fully secure, but rather, just to find good ways to
                // express protocol state machines on top of the messaging layer.
            }

            // Sign with our key and get the timestamping authorities key as well.
            // These two steps could be done in parallel, in theory.
            val ourSignature = myKeyPair.signWithECDSA(partialTX.txBits)
            val tsaSig = TimestamperClient(this, timestampingAuthority).timestamp(partialTX.txBits)
            val fullySigned = partialTX.withAdditionalSignature(tsaSig).withAdditionalSignature(ourSignature)
            val ltx = fullySigned.verifyToLedgerTransaction(serviceHub.identityService)

            // We should run it through our full TransactionGroup of all transactions here.

            logger.trace { "Built finished transaction, sending back to secondary!" }

            send(TRADE_TOPIC, otherSide, buyerSessionID, fullySigned)

            return Pair(wtx, ltx)
        }
    }

    // This object is serialised to the network and is the first protocol message the seller sends to the buyer.
    private class SellerTradeInfo(
            val assetForSale: StateAndRef<OwnableState>,
            val price: Amount,
            val sellerOwnerKey: PublicKey,
            val sessionID: Long
    )


    class UnacceptablePriceException(val givenPrice: Amount) : Exception()
    class AssetMismatchException(val expectedTypeName: String, val typeName: String) : Exception() {
        override fun toString() = "The submitted asset didn't match the expected type: $expectedTypeName vs $typeName"
    }

    // The buyer's side of the protocol. See note above Seller to learn about the caveats here.
    class Buyer(val otherSide: SingleMessageRecipient,
                val timestampingAuthority: Party,
                val acceptablePrice: Amount,
                val typeToBuy: Class<out OwnableState>,
                val sessionID: Long) : ProtocolStateMachine<Pair<WireTransaction, LedgerTransaction>>() {
        @Suspendable
        override fun call(): Pair<WireTransaction, LedgerTransaction> {
            // Wait for a trade request to come in on our pre-provided session ID.
            val tradeRequest = receive(TRADE_TOPIC, sessionID, SellerTradeInfo::class.java)

            // What is the seller trying to sell us?
            val assetTypeName = tradeRequest.assetForSale.state.javaClass.name
            logger.trace { "Got trade request for a $assetTypeName" }

            // Check the start message for acceptability.
            check(tradeRequest.sessionID > 0)
            if (tradeRequest.price > acceptablePrice)
                throw UnacceptablePriceException(tradeRequest.price)
            if (!typeToBuy.isInstance(tradeRequest.assetForSale.state))
                throw AssetMismatchException(typeToBuy.name, assetTypeName)

            // TODO: Either look up the stateref here in our local db, or accept a long chain of states and
            // validate them to audit the other side and ensure it actually owns the state we are being offered!
            // For now, just assume validity!

            // Generate the shared transaction that both sides will sign, using the data we have.
            val ptx = TransactionBuilder()
            // Add input and output states for the movement of cash, by using the Cash contract to generate the states.
            val wallet = serviceHub.walletService.currentWallet
            val cashStates = wallet.statesOfType<Cash.State>()
            val cashSigningPubKeys = Cash().generateSpend(ptx, tradeRequest.price, tradeRequest.sellerOwnerKey, cashStates)
            // Add inputs/outputs/a command for the movement of the asset.
            ptx.addInputState(tradeRequest.assetForSale.ref)
            // Just pick some new public key for now. This won't be linked with our identity in any way, which is what
            // we want for privacy reasons: the key is here ONLY to manage and control ownership, it is not intended to
            // reveal who the owner actually is. The key management service is expected to derive a unique key from some
            // initial seed in order to provide privacy protection.
            val freshKey = serviceHub.keyManagementService.freshKey()
            val (command, state) = tradeRequest.assetForSale.state.withNewOwner(freshKey.public)
            ptx.addOutputState(state)
            ptx.addCommand(command, tradeRequest.assetForSale.state.owner)

            // And add a request for timestamping: it may be that none of the contracts need this! But it can't hurt
            // to have one.
            ptx.setTime(Instant.now(), timestampingAuthority, 30.seconds)

            // Now sign the transaction with whatever keys we need to move the cash.
            for (k in cashSigningPubKeys) {
                val priv = serviceHub.keyManagementService.toPrivate(k)
                ptx.signWith(KeyPair(k, priv))
            }

            val stx = ptx.toSignedTransaction(checkSufficientSignatures = false)
            stx.verifySignatures()  // Verifies that we generated a signed transaction correctly.

            // TODO: Could run verify() here to make sure the only signature missing is the sellers.

            logger.trace { "Sending partially signed transaction to seller" }

            // TODO: Protect against the buyer terminating here and leaving us in the lurch without the final tx.
            // TODO: Protect against a malicious buyer sending us back a different transaction to the one we built.

            val fullySigned = sendAndReceive(TRADE_TOPIC, otherSide, tradeRequest.sessionID,
                    sessionID, stx, SignedWireTransaction::class.java)

            logger.trace { "Got fully signed transaction, verifying ... "}

            val ltx = fullySigned.verifyToLedgerTransaction(serviceHub.identityService)

            logger.trace { "Fully signed transaction was valid. Trade complete! :-)" }

            return Pair(fullySigned.tx, ltx)
        }
    }
}