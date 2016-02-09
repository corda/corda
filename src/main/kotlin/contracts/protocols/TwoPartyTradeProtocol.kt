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
import core.crypto.DigitalSignature
import core.crypto.SecureHash
import core.crypto.signWithECDSA
import core.messaging.*
import core.node.DataVendingService
import core.node.TimestamperClient
import core.utilities.trace
import java.security.KeyPair
import java.security.PublicKey
import java.time.Instant
import java.util.*

/**
 * This asset trading protocol implements a "delivery vs payment" type swap. It has two parties (B and S for buyer
 * and seller) and the following steps:
 *
 * 1. S sends the [StateAndRef] pointing to what they want to sell to B, along with info about the price they require
 *    B to pay. For example this has probably been agreed on an exchange.
 * 2. B sends to S a [SignedTransaction] that includes the state as input, B's cash as input, the state with the new
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
                  myKeyPair: KeyPair, buyerSessionID: Long): ListenableFuture<SignedTransaction> {
        val seller = Seller(otherSide, timestampingAuthority, assetToSell, price, myKeyPair, buyerSessionID)
        smm.add("$TRADE_TOPIC.seller", seller)
        return seller.resultFuture
    }

    fun runBuyer(smm: StateMachineManager, timestampingAuthority: LegallyIdentifiableNode,
                 otherSide: SingleMessageRecipient, acceptablePrice: Amount, typeToBuy: Class<out OwnableState>,
                 sessionID: Long): ListenableFuture<SignedTransaction> {
        val buyer = Buyer(otherSide, timestampingAuthority.identity, acceptablePrice, typeToBuy, sessionID)
        smm.add("$TRADE_TOPIC.buyer", buyer)
        return buyer.resultFuture
    }

    class UnacceptablePriceException(val givenPrice: Amount) : Exception()
    class AssetMismatchException(val expectedTypeName: String, val typeName: String) : Exception() {
        override fun toString() = "The submitted asset didn't match the expected type: $expectedTypeName vs $typeName"
    }
    class ExcessivelyLargeTransactionGraphException() : Exception()

    // This object is serialised to the network and is the first protocol message the seller sends to the buyer.
    class SellerTradeInfo(
            val assetForSale: StateAndRef<OwnableState>,
            val price: Amount,
            val sellerOwnerKey: PublicKey,
            val sessionID: Long
    )

    class SignaturesFromSeller(val timestampAuthoritySig: DigitalSignature.WithKey, val sellerSig: DigitalSignature.WithKey)

    open class Seller(val otherSide: SingleMessageRecipient,
                      val timestampingAuthority: LegallyIdentifiableNode,
                      val assetToSell: StateAndRef<OwnableState>,
                      val price: Amount,
                      val myKeyPair: KeyPair,
                      val buyerSessionID: Long) : ProtocolStateMachine<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val partialTX: SignedTransaction = receiveAndCheckProposedTransaction()

            // These two steps could be done in parallel, in theory. Our framework doesn't support that yet though.
            val ourSignature = signWithOurKey(partialTX)
            val tsaSig = timestamp(partialTX)

            val signedTransaction = sendSignatures(partialTX, ourSignature, tsaSig)

            return signedTransaction
        }

        @Suspendable
        open fun receiveAndCheckProposedTransaction(): SignedTransaction {
            val sessionID = random63BitValue()

            // Make the first message we'll send to kick off the protocol.
            val hello = SellerTradeInfo(assetToSell, price, myKeyPair.public, sessionID)

            val maybeSTX = sendAndReceive<SignedTransaction>(TRADE_TOPIC, otherSide, buyerSessionID, sessionID, hello)

            maybeSTX.validate {
                it.verifySignatures()
                val wtx: WireTransaction = it.tx
                logger.trace { "Received partially signed transaction: ${it.id}" }

                checkDependencies(it)

                requireThat {
                    "transaction sends us the right amount of cash" by (wtx.outputs.sumCashBy(myKeyPair.public) == price)
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
                return it
            }
        }

        @Suspendable
        open fun checkDependencies(txToCheck: SignedTransaction) {
            val toVerify = HashSet<LedgerTransaction>()
            val alreadyVerified = HashSet<LedgerTransaction>()
            val downloadedSignedTxns = ArrayList<SignedTransaction>()

            fetchDependenciesAndCheckSignatures(txToCheck.tx.inputs, toVerify, alreadyVerified, downloadedSignedTxns)

            TransactionGroup(toVerify, alreadyVerified).verify(serviceHub.storageService.contractPrograms)

            // Now write all the transactions we just validated back to the database for next time, including
            // signatures so we can serve up these transactions to other peers when we, in turn, send one that
            // depends on them onto another peer.
            //
            // It may seem tempting to write transactions to the database as we receive them, instead of all at once
            // here at the end. Doing it this way avoids cases where a transaction is in the database but its
            // dependencies aren't, or an unvalidated and possibly broken tx is there.
            serviceHub.storageService.validatedTransactions.putAll(downloadedSignedTxns.associateBy { it.id })
        }

        @Suspendable
        private fun fetchDependenciesAndCheckSignatures(depsToCheck: List<StateRef>,
                                                        toVerify: HashSet<LedgerTransaction>,
                                                        alreadyVerified: HashSet<LedgerTransaction>,
                                                        downloadedSignedTxns: ArrayList<SignedTransaction>) {
            // A1. Create a work queue of transaction hashes waiting for resolution. Create a TransactionGroup.
            //
            // B1. Pop a hash. Look it up in the database. If it's not there, put the hash into a list for sending to
            //     the other peer. If it is there, load it and put its outputs into the TransactionGroup as unverified
            //     roots, because it's already been validated before.
            // B2. If the queue is not empty, GOTO B1
            // B3. If the request list is empty, GOTO D1
            //
            // C1. Send the request for hashes to the peer and wait for the response. Clear the request list.
            // C2. For each transaction returned, verify that it does indeed hash to the requested transaction.
            // C3. Add each transaction to the TransactionGroup.
            // C4. Add each input state in each transaction to the work queue.
            //
            // D1. Verify the transaction group.
            // D2. Write all the transactions in the group to the database.
            // END
            //
            // Note: This protocol leaks private data. If you download a transaction and then do NOT request a
            // dependency, it means you already have it, which in turn means you must have been involved with it before
            // somehow, either in the tx itself or in any following spend of it. If there were no following spends, then
            // your peer knows for sure that you were involved ... this is bad! The only obvious ways to fix this are
            // something like onion routing of requests, secure hardware, or both.
            //
            // TODO: This needs to be factored out into a general subprotocol and subprotocol handling improved.

            val workQ = ArrayList<StateRef>()
            workQ.addAll(depsToCheck)

            val nextRequests = ArrayList<SecureHash>()

            val db = serviceHub.storageService.validatedTransactions

            var limitCounter = 0
            while (true) {
                for (ref in workQ) {
                    val stx: SignedTransaction? = db[ref.txhash]
                    if (stx == null) {
                        // Transaction wasn't found in our local database, so we have to ask for it.
                        nextRequests.add(ref.txhash)
                    } else {
                        alreadyVerified.add(stx.verifyToLedgerTransaction(serviceHub.identityService))
                    }
                }
                workQ.clear()

                if (nextRequests.isEmpty())
                    break

                val sid = random63BitValue()
                val fetchReq = DataVendingService.Request(nextRequests, serviceHub.networkService.myAddress, sid)
                logger.info("Requesting ${nextRequests.size} dependency(s) for verification")
                val maybeTxns: UntrustworthyData<ArrayList<SignedTransaction?>> =
                        sendAndReceive("platform.fetch.tx", otherSide, 0, sid, fetchReq)

                // Check for a buggy/malicious peer answering with something that we didn't ask for, and then
                // verify the signatures on the transactions and look up the identities to get LedgerTransactions.
                // Note that this doesn't run contracts: just checks the signatures match the data.
                val stxns: List<SignedTransaction> = validateTXFetchResponse(maybeTxns, nextRequests)
                nextRequests.clear()
                val ltxns = stxns.map { it.verifyToLedgerTransaction(serviceHub.identityService) }

                // Add to the TransactionGroup, pending verification.
                toVerify.addAll(ltxns)
                downloadedSignedTxns.addAll(stxns)

                // And now add all the input states to the work queue for database or remote resolution.
                workQ.addAll(ltxns.flatMap { it.inputs })

                // And loop around ...
                limitCounter += workQ.size
                if (limitCounter > 5000)
                    throw ExcessivelyLargeTransactionGraphException()
            }
        }

        private fun validateTXFetchResponse(maybeTxns: UntrustworthyData<ArrayList<SignedTransaction?>>,
                                            nextRequests: ArrayList<SecureHash>): List<SignedTransaction> {
            return maybeTxns.validate { response ->
                require(response.size == nextRequests.size)
                val answers = response.requireNoNulls()
                // Check transactions actually hash to what we requested, if this fails the remote node
                // is a malicious protocol violator or buggy.
                for ((index, stx) in answers.withIndex())
                    require(stx.id == nextRequests[index])
                answers
            }
        }

        open fun signWithOurKey(partialTX: SignedTransaction) = myKeyPair.signWithECDSA(partialTX.txBits)

        @Suspendable
        open fun timestamp(partialTX: SignedTransaction): DigitalSignature.LegallyIdentifiable {
            return TimestamperClient(this, timestampingAuthority).timestamp(partialTX.txBits)
        }

        @Suspendable
        open fun sendSignatures(partialTX: SignedTransaction, ourSignature: DigitalSignature.WithKey,
                                tsaSig: DigitalSignature.LegallyIdentifiable): SignedTransaction {
            val fullySigned = partialTX + tsaSig + ourSignature

            logger.trace { "Built finished transaction, sending back to secondary!" }

            send(TRADE_TOPIC, otherSide, buyerSessionID, SignaturesFromSeller(tsaSig, ourSignature))
            return fullySigned
        }
    }

    open class Buyer(val otherSide: SingleMessageRecipient,
                     val timestampingAuthority: Party,
                     val acceptablePrice: Amount,
                     val typeToBuy: Class<out OwnableState>,
                     val sessionID: Long) : ProtocolStateMachine<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val tradeRequest = receiveAndValidateTradeRequest()
            val (ptx, cashSigningPubKeys) = assembleSharedTX(tradeRequest)
            val stx = signWithOurKeys(cashSigningPubKeys, ptx)
            val signatures = swapSignaturesWithSeller(stx, tradeRequest.sessionID)

            logger.trace { "Got signatures from seller, verifying ... "}
            val fullySigned = stx + signatures.timestampAuthoritySig + signatures.sellerSig
            fullySigned.verify()

            logger.trace { "Signatures received are valid. Trade complete! :-)" }
            return fullySigned
        }

        @Suspendable
        open fun receiveAndValidateTradeRequest(): SellerTradeInfo {
            // Wait for a trade request to come in on our pre-provided session ID.
            val maybeTradeRequest = receive(TRADE_TOPIC, sessionID, SellerTradeInfo::class.java)

            val tradeRequest = maybeTradeRequest.validate {
                // What is the seller trying to sell us?
                val assetTypeName = it.assetForSale.state.javaClass.name
                logger.trace { "Got trade request for a $assetTypeName" }

                // Check the start message for acceptability.
                check(it.sessionID > 0)
                if (it.price > acceptablePrice)
                    throw UnacceptablePriceException(it.price)
                if (!typeToBuy.isInstance(it.assetForSale.state))
                    throw AssetMismatchException(typeToBuy.name, assetTypeName)

                return@validate it
            }

            // TODO: Either look up the stateref here in our local db, or accept a long chain of states and
            // validate them to audit the other side and ensure it actually owns the state we are being offered!
            // For now, just assume validity!
            return tradeRequest
        }

        @Suspendable
        open fun swapSignaturesWithSeller(stx: SignedTransaction, theirSessionID: Long): SignaturesFromSeller {
            logger.trace { "Sending partially signed transaction to seller" }

            // TODO: Protect against the seller terminating here and leaving us in the lurch without the final tx.

            return sendAndReceive(TRADE_TOPIC, otherSide, theirSessionID, sessionID, stx, SignaturesFromSeller::class.java).validate { it }
        }

        open fun signWithOurKeys(cashSigningPubKeys: List<PublicKey>, ptx: TransactionBuilder): SignedTransaction {
            // Now sign the transaction with whatever keys we need to move the cash.
            for (k in cashSigningPubKeys) {
                val priv = serviceHub.keyManagementService.toPrivate(k)
                ptx.signWith(KeyPair(k, priv))
            }

            val stx = ptx.toSignedTransaction(checkSufficientSignatures = false)
            stx.verifySignatures()  // Verifies that we generated a signed transaction correctly.

            // TODO: Could run verify() here to make sure the only signature missing is the sellers.

            return stx
        }

        open fun assembleSharedTX(tradeRequest: SellerTradeInfo): Pair<TransactionBuilder, List<PublicKey>> {
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
            return Pair(ptx, cashSigningPubKeys)
        }
    }
}