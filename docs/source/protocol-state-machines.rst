.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Protocol state machines
=======================

This article explains our experimental approach to modelling financial protocols in code. It explains how the
platform's state machine framework is used, and takes you through the code for a simple 2-party asset trading protocol
which is included in the source.

Introduction
------------

Shared distributed ledgers are interesting because they allow many different, mutually distrusting parties to
share a single source of truth about the ownership of assets. Digitally signed transactions are used to update that
shared ledger, and transactions may alter many states simultaneously and atomically.

Blockchain systems such as Bitcoin support the idea of building up a finished, signed transaction by passing around
partially signed invalid transactions outside of the main network, and by doing this you can implement
*delivery versus payment* such that there is no chance of settlement failure, because the movement of cash and the
traded asset are performed atomically by the same transaction. To perform such a trade involves a multi-step protocol
in which messages are passed back and forth privately between parties, checked, signed and so on.

Despite how useful these protocols are, platforms such as Bitcoin and Ethereum do not assist the developer with the rather
tricky task of actually building them. That is unfortunate. There are many awkward problems in their implementation
that a good platform would take care of for you, problems like:

* Avoiding "callback hell" in which code that should ideally be sequential is turned into an unreadable mess due to the
  desire to avoid using up a thread for every protocol instantiation.
* Surviving node shutdowns/restarts that may occur in the middle of the protocol without complicating things. This
  implies that the state of the protocol must be persisted to disk.
* Error handling.
* Message routing.
* Serialisation.
* Catching type errors, in which the developer gets temporarily confused and expects to receive/send one type of message
  when actually they need to receive/send another.
* Unit testing of the finished protocol.

Actor frameworks can solve some of the above but they are often tightly bound to a particular messaging layer, and
we would like to keep a clean separation. Additionally, they are typically not type safe, and don't make persistence or
writing sequential code much easier.

To put these problems in perspective, the *payment channel protocol* in the bitcoinj library, which allows bitcoins to
be temporarily moved off-chain and traded at high speed between two parties in private, consists of about 7000 lines of
Java and took over a month of full time work to develop. Most of that code is concerned with the details of persistence,
message passing, lifecycle management, error handling and callback management. Because the business logic is quite
spread out the code can be difficult to read and debug.

As small contract-specific trading protocols are a common occurence in finance, we provide a framework for the
construction of them that automatically handles many of the concerns outlined above.

Theory
------

A *continuation* is a suspended stack frame stored in a regular object that can be passed around, serialised,
unserialised and resumed from where it was suspended. This concept is sometimes referred to as "fibers". This may
sound abstract but don't worry, the examples below will make it clearer. The JVM does not natively support
continuations, so we implement them using a library called Quasar which works through behind-the-scenes
bytecode rewriting. You don't have to know how this works to benefit from it, however.

We use continuations for the following reasons:

* It allows us to write code that is free of callbacks, that looks like ordinary sequential code.
* A suspended continuation takes far less memory than a suspended thread. It can be as low as a few hundred bytes.
  In contrast a suspended Java stack can easily be 1mb in size.
* It frees the developer from thinking (much) about persistence and serialisation.

A *state machine* is a piece of code that moves through various *states*. These are not the same as states in the data
model (that represent facts about the world on the ledger), but rather indicate different stages in the progression
of a multi-stage protocol. Typically writing a state machine would require the use of a big switch statement and some
explicit variables to keep track of where you're up to. The use of continuations avoids this hassle.

A two party trading protocol
----------------------------

We would like to implement the "hello world" of shared transaction building protocols: a seller wishes to sell some
*asset* (e.g. some commercial paper) in return for *cash*. The buyer wishes to purchase the asset using his cash. They
want the trade to be atomic so neither side is exposed to the risk of settlement failure. We assume that the buyer
and seller have found each other and arranged the details on some exchange, or over the counter. The details of how
the trade is arranged isn't covered in this article.

Our protocol has two parties (B and S for buyer and seller) and will proceed as follows:

1. S sends a ``StateAndRef`` pointing to the state they want to sell to B, along with info about the price they require
   B to pay.
2. B sends to S a ``SignedTransaction`` that includes the state as input, B's cash as input, the state with the new
   owner key as output, and any change cash as output. It contains a single signature from B but isn't valid because
   it lacks a signature from S authorising movement of the asset.
3. S signs it and hands the now finalised ``SignedTransaction`` back to B.

You can find the implementation of this protocol in the file ``contracts/protocols/TwoPartyTradeProtocol.kt``.

Assuming no malicious termination, they both end the protocol being in posession of a valid, signed transaction that
represents an atomic asset swap.

Note that it's the *seller* who initiates contact with the buyer, not vice-versa as you might imagine.

We start by defining a wrapper that namespaces the protocol code, two functions to start either the buy or sell side
of the protocol, and two classes that will contain the protocol definition. We also pick what data will be used by
each side.

.. container:: codeset

   .. sourcecode:: kotlin

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

          // This object is serialised to the network and is the first protocol message the seller sends to the buyer.
          class SellerTradeInfo(
                  val assetForSale: StateAndRef<OwnableState>,
                  val price: Amount,
                  val sellerOwnerKey: PublicKey,
                  val sessionID: Long
          )

          class SignaturesFromSeller(val timestampAuthoritySig: DigitalSignature.WithKey, val sellerSig: DigitalSignature.WithKey)

          class Seller(val otherSide: SingleMessageRecipient,
                       val timestampingAuthority: LegallyIdentifiableNode,
                       val assetToSell: StateAndRef<OwnableState>,
                       val price: Amount,
                       val myKeyPair: KeyPair,
                       val buyerSessionID: Long) : ProtocolStateMachine<SignedTransaction>() {
              @Suspendable
              override fun call(): SignedTransaction {
                  TODO()
              }
          }

          class UnacceptablePriceException(val givenPrice: Amount) : Exception()
          class AssetMismatchException(val expectedTypeName: String, val typeName: String) : Exception() {
              override fun toString() = "The submitted asset didn't match the expected type: $expectedTypeName vs $typeName"
          }

          class Buyer(val otherSide: SingleMessageRecipient,
                      val timestampingAuthority: Party,
                      val acceptablePrice: Amount,
                      val typeToBuy: Class<out OwnableState>,
                      val sessionID: Long) : ProtocolStateMachine<SignedTransaction>() {
              @Suspendable
              override fun call(): SignedTransaction {
                  TODO()
              }
          }
      }

Let's unpack what this code does:

- It defines a several classes nested inside the main ``TwoPartyTradeProtocol`` singleton, and a couple of methods, one
  to run the buyer side of the protocol and one to run the seller side. Some of the classes are simply protocol messages.
- It defines the "trade topic", which is just a string that namespaces this protocol. The prefix "platform." is reserved
  by the DLG, but you can define your own protocols using standard Java-style reverse DNS notation.
- The ``runBuyer`` and ``runSeller`` methods take a number of parameters that specialise the protocol for this run,
  use them to construct a ``Buyer`` or ``Seller`` object respectively, and then add the new instances to the
  ``StateMachineManager``. The purpose of this class is described below. The ``smm.add`` method takes a logger name as
  the first parameter, this is just a standard JDK logging identifier string, and the instance to add.

Going through the data needed to become a seller, we have:

- ``timestampingAuthority: LegallyIdentifiableNode`` - a reference to a node on the P2P network that acts as a trusted
  timestamper. The use of timestamping is described in :doc:`data-model`.
- ``otherSide: SingleMessageRecipient`` - the network address of the node with which you are trading.
- ``assetToSell: StateAndRef<OwnableState>`` - a pointer to the ledger entry that represents the thing being sold.
- ``price: Amount`` - the agreed on price that the asset is being sold for.
- ``myKeyPair: KeyPair`` - the key pair that controls the asset being sold. It will be used to sign the transaction.
- ``buyerSessionID: Long`` - a unique number that identifies this trade to the buyer. It is expected that the buyer
  knows that the trade is going to take place and has sent you such a number already. (This field may go away in a future
  iteration of the framework)

.. note:: Session IDs keep different traffic streams separated, so for security they must be large and random enough
   to be unguessable. 63 bits is good enough.

And for the buyer:

- ``acceptablePrice: Amount`` - the price that was agreed upon out of band. If the seller specifies a price less than
  or equal to this, then the trade will go ahead.
- ``typeToBuy: Class<out OwnableState>`` - the type of state that is being purchased. This is used to check that the
  sell side of the protocol isn't trying to sell us the wrong thing, whether by accident or on purpose.
- ``sessionID: Long`` - the session ID that was handed to the seller in order to start the protocol.

The run methods return a ``ListenableFuture`` that will complete when the protocol has finished.

Alright, so using this protocol shouldn't be too hard: in the simplest case we can just pass in the details of the trade
to either runBuyer or runSeller, depending on who we are, and then call ``.get()`` on resulting object to
block the calling thread until the protocol has finished. Or we could register a callback on the returned future that
will be invoked when it's done, where we could e.g. update a user interface.

Finally, we define a couple of exceptions, and two classes that will be used as a protocol message called
``SellerTradeInfo`` and ``SignaturesFromSeller``.

Suspendable methods
-------------------

The ``call`` method of the buyer/seller classes is marked with the ``@Suspendable`` annotation. What does this mean?

As mentioned above, our protocol framework will at points suspend the code and serialise it to disk. For this to work,
any methods on the call stack must have been pre-marked as ``@Suspendable`` so the bytecode rewriter knows to modify
the underlying code to support this new feature. A protocol is suspended when calling either ``receive``, ``send`` or
``sendAndReceive`` which we will learn more about below. For now, just be aware that when one of these methods is
invoked, all methods on the stack must have been marked. If you forget, then in the unit test environment you will
get a useful error message telling you which methods you didn't mark. The fix is simple enough: just add the annotation
and try again.

.. note:: A future version of Java is likely to remove this pre-marking requirement completely.

The state machine manager
-------------------------

The SMM is a class responsible for taking care of all running protocols in a node. It knows how to register handlers
with a ``MessagingService`` and iterate the right state machine when messages arrive. It provides the
send/receive/sendAndReceive calls that let the code request network interaction and it will store a serialised copy of
each state machine before it's suspended to wait for the network.

To get a ``StateMachineManager``, you currently have to build one by passing in a ``ServiceHub`` and a thread or thread
pool which it can use. This will change in future so don't worry about the details of this too much: just check the
unit tests to see how it's done.

Implementing the seller
-----------------------

Let's implement the ``Seller.call`` method. This will be invoked by the platform when the protocol is started by the
``StateMachineManager``.

.. container:: codeset

   .. sourcecode:: kotlin

      val partialTX: SignedTransaction = receiveAndCheckProposedTransaction()

      // These two steps could be done in parallel, in theory. Our framework doesn't support that yet though.
      val ourSignature = signWithOurKey(partialTX)
      val tsaSig = timestamp(partialTX)

      val stx: SignedTransaction = sendSignatures(partialTX, ourSignature, tsaSig)

      return stx

Here we see the outline of the procedure. We receive a proposed trade transaction from the buyer and check that it's
valid. Then we sign with our own key, request a timestamping authority to assert with another signature that the
timestamp in the transaction (if any) is valid, and finally we send back both our signature and the TSA's signature.
Finally, we hand back to the code that invoked the protocol the finished transaction in a couple of different forms.

Let's fill out the ``receiveAndCheckProposedTransaction()`` method.

.. container:: codeset

   .. sourcecode:: kotlin

      @Suspendable
      open fun receiveAndCheckProposedTransaction(): SignedTransaction {
          val sessionID = random63BitValue()

          // Make the first message we'll send to kick off the protocol.
          val hello = SellerTradeInfo(assetToSell, price, myKeyPair.public, sessionID)

          val maybePartialTX = sendAndReceive(TRADE_TOPIC, buyerSessionID, sessionID, hello, SignedTransaction::class.java)
          val partialTX = maybePartialTX.validate {
                it.verifySignatures()
                logger.trace { "Received partially signed transaction" }
                val wtx: WireTransaction = it.tx

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
          }
          return partialTX
      }

That's pretty straightforward. We generate a session ID to identify what's happening on the seller side, fill out
the initial protocol message, and then call ``sendAndReceive``. This function takes a few arguments:

- The topic string that ensures the message is routed to the right bit of code in the other side's node.
- The session IDs that ensure the messages don't get mixed up with other simultaneous trades.
- The thing to send. It'll be serialised and sent automatically.
- Finally a type argument, which is the kind of object we're expecting to receive from the other side.

It returns a simple wrapper class, ``UntrustworthyData<SignedTransaction>``, which is just a marker class that reminds
us that the data came from a potentially malicious external source and may have been tampered with or be unexpected in
other ways. It doesn't add any functionality, but acts as a reminder to "scrub" the data before use. Here, our scrubbing
simply involves checking the signatures on it. Then we could go ahead and do some more involved checks.

Once sendAndReceive is called, the call method will be suspended into a continuation. When it gets back we'll do a log
message. The buyer is supposed to send us a transaction with all the right inputs/outputs/commands in return, with their
cash put into the transaction and their signature on it authorising the movement of the cash.

.. note:: There are a couple of rules you need to bear in mind when writing a class that will be used as a continuation.
   The first is that anything on the stack when the function is suspended will be stored into the heap and kept alive by
   the garbage collector. So try to avoid keeping enormous data structures alive unless you really have to.

   The second is that as well as being kept on the heap, objects reachable from the stack will be serialised. The state
   of the function call may be resurrected much later! Kryo doesn't require objects be marked as serialisable, but even so,
   doing things like creating threads from inside these calls would be a bad idea. They should only contain business
   logic.

Here's the rest of the code:

.. container:: codeset

   .. sourcecode:: kotlin

      open fun signWithOurKey(partialTX: SignedTransaction) = myKeyPair.signWithECDSA(partialTX.txBits)

      @Suspendable
      open fun timestamp(partialTX: SignedTransaction): DigitalSignature.LegallyIdentifiable {
          return TimestamperClient(this, timestampingAuthority).timestamp(partialTX.txBits)
      }

      @Suspendable
      open fun sendSignatures(partialTX: SignedTransaction, ourSignature: DigitalSignature.WithKey,
                              tsaSig: DigitalSignature.LegallyIdentifiable): SignedTransaction {
          val fullySigned = partialTX + tsaSig + ourSignature
          fullySigned.verify()

          // TODO: We should run it through our full TransactionGroup of all transactions here.

          logger.trace { "Built finished transaction, sending back to secondary!" }

          send(TRADE_TOPIC, otherSide, buyerSessionID, SignaturesFromSeller(tsaSig, ourSignature))
          return fullySigned
      }

It's should be all pretty straightforward: here, ``txBits`` is the raw byte array representing the transaction.

In ``sendSignatures``, we take the two signatures we calculated, then add them to the partial transaction we were sent
and verify that the signatures all make sense. This should never fail: it's just a sanity check. Finally, we wrap the
two signatures in a simple wrapper message class and send it back. The send won't block waiting for an acknowledgement,
but the underlying message queue software will retry delivery if the other side has gone away temporarily.

.. warning:: This code is **not secure**. Other than not checking for all possible invalid constructions, if the
   seller stops before sending the finalised transaction to the buyer, the seller is left with a valid transaction
   but the buyer isn't, so they can't spend the asset they just purchased! This sort of thing will be fixed in a
   future version of the code.

Implementing the buyer
----------------------

OK, let's do the same for the buyer side:

.. container:: codeset

   .. sourcecode:: kotlin

      @Suspendable
      override fun call(): SignedTransaction {
          val tradeRequest = receiveAndValidateTradeRequest()
          val (ptx, cashSigningPubKeys) = assembleSharedTX(tradeRequest)
          val stx = signWithOurKeys(cashSigningPubKeys, ptx)
          val signatures = swapSignaturesWithSeller(stx, tradeRequest.sessionID)

          logger.trace { "Got signatures from seller, verifying ... "}
          val fullySigned = stx + signatures.timestampAuthoritySig + signatures.sellerSig
          fullySigned.verify()

          logger.trace { "Fully signed transaction was valid. Trade complete! :-)" }
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

          return sendAndReceive(TRADE_TOPIC, otherSide, theirSessionID, sessionID, stx, SignaturesFromSeller::class.java).validate {}
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

This code is longer but still fairly straightforward. Here are some things to pay attention to:

1. We do some sanity checking on the received message to ensure we're being offered what we expected to be offered.
2. We create a cash spend in the normal way, by using ``Cash().generateSpend``. See the contracts tutorial if this isn't
   clear.
3. We access the *service hub* when we need it to access things that are transient and may change or be recreated
   whilst a protocol is suspended, things like the wallet or the timestamping service. Remember that a protocol may
   be suspended when it waits to receive a message across node or computer restarts, so objects representing a service
   or data which may frequently change should be accessed 'just in time'.
4. Finally, we send the unfinished, invalid transaction to the seller so they can sign it. They are expected to send
   back to us a ``SignaturesFromSeller``, which once we verify it, should be the final outcome of the trade.

As you can see, the protocol logic is straightforward and does not contain any callbacks or network glue code, despite
the fact that it takes minimal resources and can survive node restarts.

.. warning:: When accessing things via the ``serviceHub`` field, avoid the temptation to stuff a reference into a local variable.
   If you do this then next time your protocol waits to receive an object, the system will try and serialise all your
   local variables and end up trying to serialise, e.g. the timestamping service, which doesn't make any conceptual
   sense. The ``serviceHub`` field is defined by the ``ProtocolStateMachine`` superclass and is marked transient so
   this problem doesn't occur. It's also restored for you when a protocol state machine is restored after a node
   restart.

