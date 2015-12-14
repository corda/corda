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

To put these problems in perspective the *payment channel protocol* in the bitcoinj library, which allows bitcoins to
be temporarily moved off-chain and traded at high speed between two parties in private, consists of about 7000 lines of
Java and took over a month of full time work to develop. Most of that code is concerned with the details of persistence,
message passing, lifecycle management, error handling and callback management. Because the business logic is quite
spread out the code can be difficult to read and debug.

As small contract-specific trading protocols are a common occurence in finance, we provide a framework for the
construction of them that automatically handles many of the concerns outlined above.

Theory
------

A *continuation* is a suspended stack frame stored in a regular object that can be passed around, serialised,
unserialised and resumed from where it was suspended. This may sound abstract but don't worry, the examples below
will make it clearer. The JVM does not natively support continuations, so we implement them using a a library called
JavaFlow which works through behind-the-scenes bytecode rewriting. You don't have to know how this works to benefit
from it, however.

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
2. B sends to S a ``SignedWireTransaction`` that includes the state as input, B's cash as input, the state with the new
   owner key as output, and any change cash as output. It contains a single signature from B but isn't valid because
   it lacks a signature from S authorising movement of the asset.
3. S signs it and hands the now finalised ``SignedWireTransaction`` back to B.

You can find the implementation of this protocol in the file ``contracts/protocols/TwoPartyTradeProtocol.kt``.

Assuming no malicious termination, they both end the protocol being in posession of a valid, signed transaction that
represents an atomic asset swap.

Note that it's the *seller* who initiates contact with the buyer, not vice-versa as you might imagine.

We start by defining an abstract base class to encapsulate the protocol. This is what code that invokes the protocol
will see:

.. container:: codeset

   .. sourcecode:: kotlin

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

Let's unpack what this code does:

- It defines a several classes nested inside the main ``TwoPartyTradeProtocol`` class, and a couple of methods, one to
  run the buyer side of the protocol and one to run the seller side.
- Two of the classes are simply wrappers for parameters to the trade; things like what is being sold, what the price
  of the asset is, how much the buyer is willing to pay and so on. The ``myKeyPair`` field is simply the public key
  that the seller wishes the buyer to send the cash to. The session ID field is sent from buyer to seller when the
  trade is being set up and is just a big random number. It's used to keep messages separated on the network, and stop
  malicious entities trying to interfere with the message stream.
- The other two classes define empty abstract classes called ``Buyer`` and ``Seller``. These inherit from a class
  called ``ProtocolStateMachine`` and provide two type parameters: the arguments class we just defined for each side
  and the type of the object that the protocol finally produces (this doesn't have to be identical for each side, even
  though in this case it is).
- Finally it simply defines a static method that creates an instance of an object that inherits from this base class
  and returns it, with a ``StateMachineManager`` as an instance. The Impl class will be defined below.

Alright, so using this protocol shouldn't be too hard: in the simplest case we can just pass in the details of the trade
to either runBuyer or runSeller, depending on who we are, and then call ``.get()`` on the resulting future to block the
calling thread until the protocol has finished. Or we could register a callback on the returned future that will be
invoked when it's done, where we could e.g. update a user interface.

The only tricky part is how to get one of these things. We need a ``StateMachineManager``. Where does that come from
and why do we need one?

The state machine manager
-------------------------

The SMM is a class responsible for taking care of all running protocols in a node. It knows how to register handlers
with a ``MessagingService`` and iterate the right state machine when the time comes. It provides the
send/receive/sendAndReceive calls that let the code request network interaction and it will store a serialised copy of
each state machine before it's suspended to wait for the network.

To get a ``StateMachineManager``, you currently have to build one by passing in a ``ServiceHub`` and a thread or thread
pool which it can use. This will change in future so don't worry about the details of this too much: just check the
unit tests to see how it's done.

Implementing the seller
-----------------------

.. container:: codeset

   .. sourcecode:: kotlin

      private class TwoPartyTradeProtocolImpl(private val smm: StateMachineManager) : TwoPartyTradeProtocol() {
          companion object {
              val TRADE_TOPIC = "com.r3cev.protocols.trade"
              fun makeSessionID() = Math.abs(SecureRandom.getInstanceStrong().nextLong())
          }

          class SellerImpl : Seller() {
              override fun call(args: SellerInitialArgs): Pair<TimestampedWireTransaction, LedgerTransaction> {
                  TODO()
              }
          }


          class BuyerImpl : Buyer() {
              override fun call(args: BuyerInitialArgs): Pair<TimestampedWireTransaction, LedgerTransaction> {
                  TODO()
              }
          }

          override fun runSeller(otherSide: SingleMessageRecipient, args: SellerInitialArgs): Seller {
              return smm.add(otherSide, args, "$TRADE_TOPIC.seller", SellerImpl::class.java)
          }

          override fun runBuyer(otherSide: SingleMessageRecipient, args: BuyerInitialArgs): Buyer {
              return smm.add(otherSide, args, "$TRADE_TOPIC.buyer", BuyerImpl::class.java)
          }
      }

We start with a skeleton on which we will build the protocol. Putting things in a *companion object* in Kotlin is like
declaring them as static members in Java. Here, we define a "topic" that will identify trade related messages that
arrive at a node (see :doc:`messaging` for details), and a convenience function to pick a large random session ID.

.. note:: Session IDs keep different traffic streams separated, so for security they must be large and random enough
   to be unguessable. 63 bits is good enough.

The runSeller and runBuyer methods simply start the state machines, passing in a reference to the classes and the topics
each side will use.

Now let's try implementing the seller side. Firstly, we're going to need a message to send to the buyer describing what
we want to trade. Remember: this data comes from whatever system was used to find the trading partner to begin with.
It could be as simple as a chat room or as complex as a 24/7 exchange.

.. container:: codeset

   .. sourcecode:: kotlin

      // This object is serialised to the network and is the first protocol message the seller sends to the buyer.
      class SellerTradeInfo(
            val assetForSale: StateAndRef<OwnableState>,
            val price: Amount,
            val sellerOwnerKey: PublicKey,
            val buyerSessionID: Long
      )

That's simple enough: our opening protocol message will be serialised before being sent over the wire, and it contains
the details that were agreed so we can double check them. It also contains a session ID so we can identify this
trade's messages, and a pointer to where the asset that is being sold can be found on the ledger.

Next we add some code to the ``SellerImpl.call`` method:

.. container:: codeset

   .. sourcecode:: kotlin

      val sessionID = makeSessionID()

      // Make the first message we'll send to kick off the protocol.
      val hello = SellerTradeInfo(args.assetToSell, args.price, args.myKeyPair.public, sessionID)

      // Zero is a special session ID that is being listened to by the buyer (i.e. before a session is started).
      val partialTX = sendAndReceive<SignedWireTransaction>(TRADE_TOPIC, args.buyerSessionID, sessionID, hello)
      logger().trace { "Received partially signed transaction" }

That's pretty straight forward. We generate a session ID to identify what's happening on the seller side, fill out
the initial protocol message, and then call ``sendAndReceive``. This function takes a few arguments:

- A type argument, which is the object we're expecting to receive from the other side.
- The topic string that ensures the message is routed to the right bit of code in the other side's node.
- The session IDs that ensure the messages don't get mixed up with other simultaneous trades.
- And finally, the thing to send. It'll be serialised and sent automatically.

Once sendAndReceive is called, the call method will be suspended into a continuation. When it gets back we'll do a log
message. The buyer is supposed to send us a transaction with all the right inputs/outputs/commands in return, with their
cash put into the transaction and their signature on it authorising the movement of the cash.

.. note:: There are a few rules you need to bear in mind when writing a class that will be used as a continuation.
   The first is that anything on the stack when the function is suspended will be stored into the heap and kept alive by
   the garbage collector. So try to avoid keeping enormous data structures alive unless you really have to.

   The second is that as well as being kept on the heap, objects reachable from the stack will be serialised. The state
   of the function call may be resurrected much later! Kryo doesn't require objects be marked as serialisable, but even so,
   doing things like creating threads from inside these calls would be a bad idea. They should only contain business
   logic.

   The third rule to bear in mind is that you can't declare variables or methods in these classes and access
   them from outside of the class, due to the bytecode rewriting and classloader tricks that are used to make this all
   work. If you want access to something inside the BuyerImpl or SellerImpl classes, you must define a super-interface
   or super-class (like ``Buyer``/``Seller``) and put what you want to access there.

OK, let's keep going:

.. container:: codeset

   .. sourcecode:: kotlin

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

      send(TRADE_TOPIC, sessionID, timestamped)

      return Pair(timestamped, timestamped.verifyToLedgerTransaction(serviceHub.timestampingService, serviceHub.identityService))

Here, we see some assertions and signature checking to satisfy ourselves that we're not about to sign something
incorrect. Once we're happy, we calculate a signature over the transaction to authorise the movement of the asset
we are selling, and then we verify things to make sure it's all OK. Finally, we request timestamping of the
transaction, and send the now finalised and validated transaction back to the buyer.

.. warning:: This code is **not secure**. Other than not checking for all possible invalid constructions, if the
   seller stops before sending the finalised transaction to the buyer, the seller is left with a valid transaction
   but the buyer isn't, so they can't spend the asset they just purchased! This sort of thing will be fixed in a
   future version of the code.

Finally, the call function returns with the result of the protocol: in our case, the final transaction in two different
forms.

Implementing the buyer
----------------------

OK, let's do the same for the buyer side:

.. container:: codeset

   .. sourcecode:: kotlin

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

This code is fairly straightforward. Here are some things to pay attention to:

1. We do some sanity checking on the received message to ensure we're being offered what we expected to be offered.
2. We create a cash spend in the normal way, by using ``Cash().craftSpend``.
3. We access the *service hub* when we need it to access things that are transient and may change or be recreated
   whilst a protocol is suspended, things like the wallet or the timestamping service. Remember that a protocol may
   be suspended when it waits to receive a message across node or computer restarts, so objects representing a service
   or data which may frequently change should be accessed 'just in time'.
4. Finally, we send the unfinsished, invalid transaction to the seller so they can sign it. They are expected to send
   back to us a ``TimestampedWireTransaction``, which once we verify it, should be the final outcome of the trade.

As you can see, the protocol logic is straightforward and does not contain any callbacks or network glue code, despite
the fact that it takes minimal resources and can survive node restarts.

.. warning:: When accessing things via the ``serviceHub`` field, avoid the temptation to stuff a reference into a local variable.
   If you do this then next time your protocol waits to receive an object, the system will try and serialise all your
   local variables and end up trying to serialise, e.g. the timestamping service, which doesn't make any conceptual
   sense. The ``serviceHub`` field is defined by the ``ProtocolStateMachine`` superclass and is marked transient so
   this problem doesn't occur. It's also restored for you after a protocol state machine is restored after a node
   restart.

