/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package contracts.protocols

import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import contracts.Cash
import contracts.sumCashBy
import core.*
import core.messaging.MessagingSystem
import core.messaging.SingleMessageRecipient
import core.serialization.SerializeableWithKryo
import core.serialization.THREAD_LOCAL_KRYO
import core.serialization.deserialize
import core.serialization.registerDataClass
import core.utilities.continuations.*
import core.utilities.trace
import org.slf4j.LoggerFactory
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom

/**
 * This asset trading protocol has two parties (B and S for buyer and seller) and the following steps:
 *
 * 1. B sends the [StateAndRef] pointing to what they want to sell to S, along with info about the price.
 * 2. S sends to B a [SignedWireTransaction] that includes the state as input, S's cash as input, the state with the new
 *    owner key as output, and any change cash as output. It contains a single signature from S but isn't valid because
 *    it lacks a signature from B authorising movement of the asset.
 * 3. B signs it and hands the now finalised SignedWireTransaction back to S.
 *
 * They both end the protocol being in posession of a validly signed contract.
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
    abstract fun runSeller(
            net: MessagingSystem,
            otherSide: SingleMessageRecipient,
            assetToSell: StateAndRef<OwnableState>,
            price: Amount,
            myKey: KeyPair,
            partyKeyMap: Map<PublicKey, Party>,
            timestamper: TimestamperService
    ): ListenableFuture<Pair<TimestampedWireTransaction, LedgerTransaction>>

    abstract fun runBuyer(
            net: MessagingSystem,
            otherSide: SingleMessageRecipient,
            acceptablePrice: Amount,
            typeToSell: Class<out OwnableState>,
            wallet: List<StateAndRef<Cash.State>>,
            myKeys: Map<PublicKey, PrivateKey>,
            timestamper: TimestamperService,
            partyKeyMap: Map<PublicKey, Party>
    ): ListenableFuture<Pair<TimestampedWireTransaction, LedgerTransaction>>

    companion object {
        @JvmStatic fun create(): TwoPartyTradeProtocol {
            return TwoPartyTradeProtocolImpl()
        }
    }
}

private class TwoPartyTradeProtocolImpl : TwoPartyTradeProtocol() {
    companion object {
        val TRADE_TOPIC = "com.r3cev.protocols.trade"
        fun makeSessionID() = Math.abs(SecureRandom.getInstanceStrong().nextLong())
    }

    init {
        THREAD_LOCAL_KRYO.get().registerDataClass<TwoPartyTradeProtocolImpl.SellerTradeInfo>()
    }

    // This wraps some of the arguments passed to runSeller that are persistent across the lifetime of the trade and
    // can be serialised.
    class SellerInitialArgs(
            val assetToSell: StateAndRef<OwnableState>,
            val price: Amount,
            val myKeyPair: KeyPair
    ) : SerializeableWithKryo

    // This wraps the things which the seller needs, but which might change whilst the continuation is suspended,
    // e.g. due to a VM restart, networking issue, configuration file reload etc. It also contains the initial args
    // and the future that the code will fill out when done.
    class SellerContext(
            val timestamper: TimestamperService,
            val partyKeyMap: Map<PublicKey, Party>,
            val initialArgs: SellerInitialArgs?,
            val resultFuture: SettableFuture<Pair<TimestampedWireTransaction, LedgerTransaction>> = SettableFuture.create()
    )

    // This object is serialised to the network and is the first protocol message the seller sends to the buyer.
    class SellerTradeInfo(
            val assetForSale: StateAndRef<OwnableState>,
            val price: Amount,
            val primaryOwnerKey: PublicKey,
            val sessionID: Long
    ) : SerializeableWithKryo

    // The seller's side of the protocol. IMPORTANT: This class is loaded in a separate classloader and auto-mangled
    // by JavaFlow. Therefore, we cannot cast the object to Seller and poke it directly because the class we'd be
    // trying to poke at is different to the one we saw at compile time, so we'd get ClassCastExceptions. All
    // interaction with this class must be through either interfaces, or objects passed to and from the continuation
    // by the state machine framework. Please refer to the documentation website (docs/build/html) to learn more about
    // the protocol state machine framework.
    class Seller : ProtocolStateMachine<SellerContext> {
        override fun run() {
            val sessionID = makeSessionID()
            val args = context().initialArgs!!

            val hello = SellerTradeInfo(args.assetToSell, args.price, args.myKeyPair.public, sessionID)
            // Zero is a special session ID that is used to start a trade (i.e. before a session is started).
            var (ctx2, offerMsg) = sendAndReceive<SignedWireTransaction, SellerContext>(TRADE_TOPIC, 0, sessionID, hello)
            logger().trace { "Received partially signed transaction" }

            val partialTx = offerMsg
            partialTx.verifySignatures()
            val wtx = partialTx.txBits.deserialize<WireTransaction>()

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

            val ourSignature = args.myKeyPair.signWithECDSA(partialTx.txBits.bits)
            val fullySigned: SignedWireTransaction = partialTx.copy(sigs = partialTx.sigs + ourSignature)
            // We should run it through our full TransactionGroup of all transactions here.
            fullySigned.verify()
            val timestamped: TimestampedWireTransaction = fullySigned.toTimestampedTransaction(ctx2.timestamper)
            logger().trace { "Built finished transaction, sending back to secondary!" }
            send(TRADE_TOPIC, sessionID, timestamped)
            ctx2.resultFuture.set(Pair(timestamped, timestamped.verifyToLedgerTransaction(ctx2.timestamper, ctx2.partyKeyMap)))
        }
    }

    class UnacceptablePriceException(val givenPrice: Amount) : Exception()
    class AssetMismatchException(val expectedTypeName: String, val typeName: String) : Exception() {
        override fun toString() = "The submitted asset didn't match the expected type: $expectedTypeName vs $typeName"
    }


    class BuyerContext(
            val acceptablePrice: Amount,
            val typeToSell: Class<out OwnableState>,
            val wallet: List<StateAndRef<Cash.State>>,
            val myKeys: Map<PublicKey, PrivateKey>,
            val timestamper: TimestamperService,
            val partyKeyMap: Map<PublicKey, Party>,
            val resultFuture: SettableFuture<Pair<TimestampedWireTransaction, LedgerTransaction>> = SettableFuture.create()
    )

    // The buyer's side of the protocol. See note above Seller to learn about the caveats here.
    class Buyer : ProtocolStateMachine<BuyerContext> {
        override fun run() {
            // Start a new scope here so we can't accidentally reuse 'ctx' after doing the sendAndReceive below,
            // as the context object we're meant to use might change each time we suspend (e.g. due to VM restart).
            val (stx, theirSessionID) = run {
                // Wait for a trade request to come in.
                val (ctx, tradeRequest) = receive<SellerTradeInfo, BuyerContext>(TRADE_TOPIC, 0)
                val assetTypeName = tradeRequest.assetForSale.state.javaClass.name

                logger().trace { "Got trade request for a $assetTypeName" }

                // Check the start message for acceptability.
                check(tradeRequest.sessionID > 0)
                if (tradeRequest.price > ctx.acceptablePrice)
                    throw UnacceptablePriceException(tradeRequest.price)
                if (!ctx.typeToSell.isInstance(tradeRequest.assetForSale.state))
                    throw AssetMismatchException(ctx.typeToSell.name, assetTypeName)

                // TODO: Either look up the stateref here in our local db, or accept a long chain of states and
                // validate them to audit the other side and ensure it actually owns the state we are being offered!
                // For now, just assume validity!

                // Generate the shared transaction that both sides will sign, using the data we have.
                val ptx = PartialTransaction()
                // Add input and output states for the movement of cash.
                val cashSigningPubKeys = Cash().craftSpend(ptx, tradeRequest.price, tradeRequest.primaryOwnerKey, ctx.wallet)
                // Add inputs/outputs/a command for the movement of the asset.
                ptx.addInputState(tradeRequest.assetForSale.ref)
                // Just pick some arbitrary public key for now (this provides poor privacy).
                val (command, state) = tradeRequest.assetForSale.state.withNewOwner(ctx.myKeys.keys.first())
                ptx.addOutputState(state)
                ptx.addArg(WireCommand(command, tradeRequest.assetForSale.state.owner))

                for (k in cashSigningPubKeys) {
                    // TODO: This error case should be removed through the introduction of a Wallet class.
                    val priv = ctx.myKeys[k] ?: throw IllegalStateException("Coin in wallet with no known privkey")
                    ptx.signWith(KeyPair(k, priv))
                }

                val stx = ptx.toSignedTransaction(checkSufficientSignatures = false)
                stx.verifySignatures()  // Verifies that we generated a signed transaction correctly.
                Pair(stx, tradeRequest.sessionID)
            }

            // TODO: Could run verify() here to make sure the only signature missing is the primaries.
            logger().trace { "Sending partially signed transaction to primary" }
            // We'll just reuse the session ID the primary selected here for convenience.
            val (ctx, fullySigned) = sendAndReceive<TimestampedWireTransaction, BuyerContext>(TRADE_TOPIC, theirSessionID, theirSessionID, stx)
            logger().trace { "Got fully signed transaction, verifying ... "}
            val ltx = fullySigned.verifyToLedgerTransaction(ctx.timestamper, ctx.partyKeyMap)
            logger().trace { "Fully signed transaction was valid. Trade complete! :-)" }
            ctx.resultFuture.set(Pair(fullySigned, ltx))
        }
    }

    override fun runSeller(net: MessagingSystem, otherSide: SingleMessageRecipient, assetToSell: StateAndRef<OwnableState>,
                           price: Amount, myKey: KeyPair, partyKeyMap: Map<PublicKey, Party>,
                           timestamper: TimestamperService): ListenableFuture<Pair<TimestampedWireTransaction, LedgerTransaction>> {
        val args = SellerInitialArgs(assetToSell, price, myKey)
        val context = SellerContext(timestamper, partyKeyMap, args)
        val logger = LoggerFactory.getLogger("$TRADE_TOPIC.primary")
        loadContinuationClass<Seller>(javaClass.classLoader).iterateStateMachine(net, otherSide, context, context, logger)
        return context.resultFuture
    }

    override fun runBuyer(net: MessagingSystem, otherSide: SingleMessageRecipient, acceptablePrice: Amount,
                          typeToSell: Class<out OwnableState>, wallet: List<StateAndRef<Cash.State>>,
                          myKeys: Map<PublicKey, PrivateKey>, timestamper: TimestamperService,
                          partyKeyMap: Map<PublicKey, Party>): ListenableFuture<Pair<TimestampedWireTransaction, LedgerTransaction>> {
        val context = BuyerContext(acceptablePrice, typeToSell, wallet, myKeys, timestamper, partyKeyMap)
        val logger = LoggerFactory.getLogger("$TRADE_TOPIC.secondary")
        loadContinuationClass<Buyer>(javaClass.classLoader).iterateStateMachine(net, otherSide, context, context, logger)
        return context.resultFuture
    }
}