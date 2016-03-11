.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Writing oracle services
=======================

This article covers *oracles*: network services that link the ledger to the outside world by providing facts that
affect the validity of transactions.

The current prototype includes two oracles:

1. A timestamping service
2. An interest rate fixing service

We will examine the similarities and differences in their design, whilst covering how the oracle concept works.

Introduction
------------

Oracles are a key concept in the block chain/decentralised ledger space. They can be essential for many kinds of
application, because we often wish to condition a transaction on some fact being true or false, but the ledger itself
has a design that is essentially functional: all transactions are *pure* and *immutable*. Phrased another way, a
smart contract cannot perform any input/output or depend on any state outside of the transaction itself. There is no
way to download a web page or interact with the user, in a smart contract. It must be this way because everyone must
be able to independently check a transaction and arrive at an identical conclusion for the ledger to maintan its
integrity: if a transaction could evaluate to "valid" on one computer and then "invalid" a few minutes later on a
different computer, the entire shared ledger concept wouldn't work.

But it is often essential that transactions do depend on data from the outside world, for example, verifying that an
interest rate swap is paying out correctly may require data on interest rates, verifying that a loan has reached
maturity requires knowledge about the current time, knowing which side of a bet receives the payment may require
arbitrary facts about the real world (e.g. the bankruptcy or solvency of a company or country) ... and so on.

We can solve this problem by introducing services that create digitally signed data structures which assert facts.
These structures can then be used as an input to a transaction and distributed with the transaction data itself. Because
the statements are themselves immutable and signed, it is impossible for an oracle to change its mind later and
invalidate transactions that were previously found to be valid. In contrast, consider what would happen if a contract
could do an HTTP request: it's possible that an answer would change after being downloaded, resulting in loss of
consensus (breaks).

The two basic approaches
------------------------

The architecture provides two ways of implementing oracles with different tradeoffs:

1. Using commands
2. Using attachments

When a fact is encoded in a command, it is embedded in the transaction itself. The oracle then acts as a co-signer to
the entire transaction. The oracle's signature is valid only for that transaction, and thus even if a fact (like a
stock price) does not change, every transaction that incorporates that fact must go back to the oracle for signing.

When a fact is encoded as an attachment, it is a separate object to the transaction which is referred to by hash.
Nodes download attachments from peers at the same time as they download transactions, unless of course the node has
already seen that attachment, in which case it won't fetch it again. Contracts have access to the contents of
attachments and attachments can be digitally signed (in future).

As you can see, both approaches share a few things: they both allow arbitrary binary data to be provided to transactions
(and thus contracts). The primary difference is whether the data is a freely reusable, standalone object or whether it's
integrated with a transaction.

Here's a quick way to decide which approach makes more sense for your data source:

* Is your data *continuously changing*, like a stock price, the current time, etc? If yes, use a command.
* Is your data *commercially valuable*, like a feed which you are not allowed to resell unless it's incorporated into
  a business deal? If yes, use a command, so you can charge money for signing the same fact in each unique business
  context.
* Is your data *very small*, like a single number? If yes, use a command.
* Is your data *large*, *static* and *commercially worthless*, for instance, a holiday calendar? If yes, use an
  attachment.
* Is your data *intended for human consumption*, like a PDF of legal prose, or an Excel spreadsheet? If yes, use an
  attachment.

Asserting continuously varying data that is publicly known
----------------------------------------------------------

Let's look at the timestamping oracle that can be found in the ``TimestamperService`` class. This is an example of
an oracle that uses a command because the current time is a constantly changing fact that everybody knows.

The most obvious way to implement such a service would be:

1. The creator of the transaction that depends on the time reads their local clock
2. They insert a command with that time into the transaction
3. They then send it to the oracle for signing.

But this approach has a problem. There will never be exact clock synchronisation between the party creating the
transaction and the oracle. This is not only due to physics, network latencies etc but because between inserting the
command and getting the oracle to sign there may be many other steps, like sending the transaction to other parties
involved in the trade as well, or even requesting human signoff. Thus the time observed by the oracle may be quite
different to the time observed in step 1. This problem can occur any time an oracle attests to a constantly changing
value.

.. note:: It is assumed that "true time" for a timestamping oracle means GPS/NaviStar time as defined by the atomic
   clocks at the US Naval Observatory. This time feed is extremely accurate and available globally for free.

We fix it by including explicit tolerances in the command, which is defined like this:

.. sourcecode:: kotlin

   data class TimestampCommand(val after: Instant?, val before: Instant?) : CommandData
       init {
           if (after == null && before == null)
               throw IllegalArgumentException("At least one of before/after must be specified")
           if (after != null && before != null)
               check(after <= before)
       }
   }

This defines a class that has two optional fields: before and after, along with a constructor that imposes a couple
more constraints that cannot be expressed in the type system, namely, that "after" actually is temporally after
"before", and that at least one bound must be present. A timestamp command that doesn't contain anything is illegal.

Thus we express that the *true value* of the fact "the current time" is actually unknowable. Even when both before and
after times are included, the transaction could have occurred at any point between those two timestamps. In this case
"occurrence" could mean the execution date, the value date, the trade date etc ... the oracle doesn't care what precise
meaning the timestamp has to the contract.

By creating a range that can be either closed or open at one end, we allow all of the following facts to be modelled:

* This transaction occurred at some point after the given time (e.g. after a maturity event)
* This transaction occurred at any time before the given time (e.g. before a bankruptcy event)
* This transaction occurred at some point roughly around the given time (e.g. on a specific day)

This same technique can be adapted to other types of oracle.

Asserting occasionally varying data that is not publicly known
--------------------------------------------------------------

Sometimes you may want a fact that changes, but is not entirely continuous. Additionally the exact value may not be
public, or may only be semi-public (e.g. easily available to some entities on the network but not all). An example of
this would be a LIBOR interest rate fix.

In this case, the following design can be used. The oracle service provides a query API which returns the current value,
and a signing service that signs a transaction if the data in the command matches the answer being returned by the
query API. Probably the query response contains some sort of timestamp as well, so the service can recognise values
that were true in the past but no longer are (this is arguably a part of the fact being asserted).

Because the signature covers the transaction, and transactions may end up being forwarded anywhere, the fact itself
is independently checkable. However, this approach can be useful when the data itself costs money, because the act
of issuing the signature in the first place can be charged for (e.g. by requiring the submission of a fresh
``Cash.State`` that has been re-assigned to a key owned by the oracle service). Because the signature covers the
*transaction* and not only the *fact*, this allows for a kind of weak pseudo-DRM over data feeds. Whilst a smart
contract could in theory include a transaction parsing and signature checking library, writing a contract in this way
would be conclusive evidence of intent to disobey the rules of the service (*res ipsa loquitur*). In an environment
where parties are legally identifiable, usage of such a contract would by itself be sufficient to trigger some sort of
punishment.

Here is an extract from the ``NodeService.Oracle`` class and supporting types:

.. sourcecode:: kotlin

   /** A [FixOf] identifies the question side of a fix: what day, tenor and type of fix ("LIBOR", "EURIBOR" etc) */
   data class FixOf(val name: String, val forDay: LocalDate, val ofTenor: Duration)

   /** A [Fix] represents a named interest rate, on a given day, for a given duration. It can be embedded in a tx. */
   data class Fix(val of: FixOf, val value: BigDecimal) : CommandData

   class Oracle {
       fun query(queries: List<FixOf>): List<Fix>

       fun sign(wtx: WireTransaction): DigitalSignature.LegallyIdentifiable
   }

Because the fix contains a timestamp (the ``forDay`` field), there can be an arbitrary delay between a fix being
requested via ``query`` and the signature being requested via ``sign``.

Implementing oracles in the framework
-------------------------------------

Implementation involves the following steps:

1. Defining a high level oracle class, that exposes the basic API operations.
2. Defining a lower level service class, that binds network messages to the API.
3. Defining a protocol using the :doc:`protocol-state-machines` framework to make it easy for a client to interact
   with the oracle.

An example of how to do this can be found in the ``NodeInterestRates.Oracle``, ``NodeInterestRates.Service`` and
``RateFixProtocol`` classes. The exact details of how this code works will change in future, so for now consulting
the protocols tutorial and the code for the server-side oracles implementation will have to suffice. There will be more
detail added once the platform APIs have settled down.

Currently, there's no network map service, so the location and identity keys of an oracle must be distributed out of
band.