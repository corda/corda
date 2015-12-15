/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package contracts.protocols

import contracts.Cash
import contracts.sumCashBy
import core.*
import core.messaging.*
import core.serialization.deserialize
import core.utilities.trace
import java.security.KeyPair
import java.security.PublicKey

/**
 * This asset trading protocol has two parties (B and S for buyer and seller) and the following steps:
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
 * To get an implementation of this class, use the static [TwoPartyTradeProtocol.create] method. Then use either
 * the [runBuyer] or [runSeller] methods, depending on which side of the trade your node is taking. These methods
 * return a future which will complete once the trade is over and a fully signed transaction is available: you can
 * either block your thread waiting for the protocol to complete by using [ListenableFuture.get] or more usefully,
 * register a callback that will be invoked when the time comes.
 *
 * To see an example of how to use this class, look at the unit tests.
 */
abstract class TwoPartyTradeProtocol {
    class SellerInitialArgs(
            val assetToSell: StateAndRef<OwnableState>,
            val price: Amount,
            val myKeyPair: KeyPair,
            val buyerSessionID: Long
    )

    abstract fun runSeller(otherSide: SingleMessageRecipient, args: SellerInitialArgs): Seller

    class BuyerInitialArgs(
            val acceptablePrice: Amount,
            val typeToBuy: Class<out OwnableState>,
            val sessionID: Long
    )

    abstract fun runBuyer(otherSide: SingleMessageRecipient, args: BuyerInitialArgs): Buyer

    abstract class Buyer : ProtocolStateMachine<BuyerInitialArgs, Pair<TimestampedWireTransaction, LedgerTransaction>>()
    abstract class Seller : ProtocolStateMachine<SellerInitialArgs, Pair<TimestampedWireTransaction, LedgerTransaction>>()

    companion object {
        @JvmStatic fun create(smm: StateMachineManager): TwoPartyTradeProtocol {
            return TwoPartyTradeProtocolImpl(smm)
        }
    }
}

/** The implementation of the [TwoPartyTradeProtocol] base class. */
private class TwoPartyTradeProtocolImpl(private val smm: StateMachineManager) : TwoPartyTradeProtocol() {
    companion object {
        val TRADE_TOPIC = "com.r3cev.protocols.trade"
    }

    // This object is serialised to the network and is the first protocol message the seller sends to the buyer.
    class SellerTradeInfo(
            val assetForSale: StateAndRef<OwnableState>,
            val price: Amount,
            val sellerOwnerKey: PublicKey,
            val sessionID: Long
    )

    // The seller's side of the protocol. IMPORTANT: This class is loaded in a separate classloader and auto-mangled
    // by JavaFlow. Therefore, we cannot cast the object to Seller and poke it directly because the class we'd be
    // trying to poke at is different to the one we saw at compile time, so we'd get ClassCastExceptions. All
    // interaction with this class must be through either interfaces, the supertype, or objects passed to and from
    // the continuation by the state machine framework. Please refer to the documentation website (docs/build/html) to
    // learn more about the protocol state machine framework.
    class SellerImpl : Seller() {
        override fun call(args: SellerInitialArgs): Pair<TimestampedWireTransaction, LedgerTransaction> {
            val sessionID = random63BitValue()

            // Make the first message we'll send to kick off the protocol.
            val hello = SellerTradeInfo(args.assetToSell, args.price, args.myKeyPair.public, sessionID)

            val partialTX = sendAndReceive<SignedWireTransaction>(TRADE_TOPIC, args.buyerSessionID, sessionID, hello)
            logger().trace { "Received partially signed transaction" }

            partialTX.verifySignatures()
            val wtx = partialTX.txBits.deserialize<WireTransaction>()

            requireThat {
                "transaction sends us the right amount of cash" by (wtx.outputStates.sumCashBy(args.myKeyPair.public) == args.price)
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

            val ourSignature = args.myKeyPair.signWithECDSA(partialTX.txBits.bits)
            val fullySigned: SignedWireTransaction = partialTX.copy(sigs = partialTX.sigs + ourSignature)
            // We should run it through our full TransactionGroup of all transactions here.
            fullySigned.verify()
            val timestamped: TimestampedWireTransaction = fullySigned.toTimestampedTransaction(serviceHub.timestampingService)
            logger().trace { "Built finished transaction, sending back to secondary!" }

            send(TRADE_TOPIC, args.buyerSessionID, timestamped)

            return Pair(timestamped, timestamped.verifyToLedgerTransaction(serviceHub.timestampingService, serviceHub.identityService))
        }
    }

    class UnacceptablePriceException(val givenPrice: Amount) : Exception()
    class AssetMismatchException(val expectedTypeName: String, val typeName: String) : Exception() {
        override fun toString() = "The submitted asset didn't match the expected type: $expectedTypeName vs $typeName"
    }

    // The buyer's side of the protocol. See note above Seller to learn about the caveats here.
    class BuyerImpl : Buyer() {
        override fun call(args: BuyerInitialArgs): Pair<TimestampedWireTransaction, LedgerTransaction> {
            // Wait for a trade request to come in on our pre-provided session ID.
            val tradeRequest = receive<SellerTradeInfo>(TRADE_TOPIC, args.sessionID)

            // What is the seller trying to sell us?
            val assetTypeName = tradeRequest.assetForSale.state.javaClass.name
            logger().trace { "Got trade request for a $assetTypeName" }

            // Check the start message for acceptability.
            check(tradeRequest.sessionID > 0)
            if (tradeRequest.price > args.acceptablePrice)
                throw UnacceptablePriceException(tradeRequest.price)
            if (!args.typeToBuy.isInstance(tradeRequest.assetForSale.state))
                throw AssetMismatchException(args.typeToBuy.name, assetTypeName)

            // TODO: Either look up the stateref here in our local db, or accept a long chain of states and
            // validate them to audit the other side and ensure it actually owns the state we are being offered!
            // For now, just assume validity!

            // Generate the shared transaction that both sides will sign, using the data we have.
            val ptx = PartialTransaction()
            // Add input and output states for the movement of cash, by using the Cash contract to generate the states.
            val wallet = serviceHub.walletService.currentWallet
            val cashStates = wallet.statesOfType<Cash.State>()
            val cashSigningPubKeys = Cash().craftSpend(ptx, tradeRequest.price, tradeRequest.sellerOwnerKey, cashStates)
            // Add inputs/outputs/a command for the movement of the asset.
            ptx.addInputState(tradeRequest.assetForSale.ref)
            // Just pick some new public key for now.
            val freshKey = serviceHub.keyManagementService.freshKey()
            val (command, state) = tradeRequest.assetForSale.state.withNewOwner(freshKey.public)
            ptx.addOutputState(state)
            ptx.addArg(WireCommand(command, tradeRequest.assetForSale.state.owner))

            // Now sign the transaction with whatever keys we need to move the cash.
            for (k in cashSigningPubKeys) {
                val priv = serviceHub.keyManagementService.toPrivate(k)
                ptx.signWith(KeyPair(k, priv))
            }

            val stx = ptx.toSignedTransaction(checkSufficientSignatures = false)
            stx.verifySignatures()  // Verifies that we generated a signed transaction correctly.

            // TODO: Could run verify() here to make sure the only signature missing is the sellers.

            logger().trace { "Sending partially signed transaction to seller" }

            // TODO: Protect against the buyer terminating here and leaving us in the lurch without the final tx.
            // TODO: Protect against a malicious buyer sending us back a different transaction to the one we built.
            val fullySigned = sendAndReceive<TimestampedWireTransaction>(TRADE_TOPIC,
                    tradeRequest.sessionID, args.sessionID, stx)

            logger().trace { "Got fully signed transaction, verifying ... "}

            val ltx = fullySigned.verifyToLedgerTransaction(serviceHub.timestampingService, serviceHub.identityService)

            logger().trace { "Fully signed transaction was valid. Trade complete! :-)" }

            return Pair(fullySigned, ltx)
        }
    }

    override fun runSeller(otherSide: SingleMessageRecipient, args: SellerInitialArgs): Seller {
        return smm.add(otherSide, args, "$TRADE_TOPIC.seller", SellerImpl::class.java)
    }

    override fun runBuyer(otherSide: SingleMessageRecipient, args: BuyerInitialArgs): Buyer {
        return smm.add(otherSide, args, "$TRADE_TOPIC.buyer", BuyerImpl::class.java)
    }
}