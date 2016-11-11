.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Writing oracle services
=======================

This article covers *oracles*: network services that link the ledger to the outside world by providing facts that
affect the validity of transactions.

The current prototype includes an example oracle that provides an interest rate fixing service. It is used by the
IRS trading demo app.

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

When a fact is encoded as an attachment, it is a separate object to the transaction and is referred to by hash.
Nodes download attachments from peers at the same time as they download transactions, unless of course the node has
already seen that attachment, in which case it won't fetch it again. Contracts have access to the contents of
attachments when they run.

.. note:: Currently attachments do not support digital signing, but this is a planned feature.

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

Asserting continuously varying data
-----------------------------------

.. note:: A future version of the platform will include a complete tutorial on implementing this type of oracle.

Let's look at the interest rates oracle that can be found in the ``NodeInterestRates`` file. This is an example of
an oracle that uses a command because the current interest rate fix is a constantly changing fact.

The obvious way to implement such a service is like this:

1. The creator of the transaction that depends on the interest rate sends it to the oracle.
2. The oracle inserts a command with the rate and signs the transaction.
3. The oracle sends it back.

But this has a problem - it would mean that the oracle has to be the first entity to sign the transaction, which might impose
ordering constraints we don't want to deal with (being able to get all parties to sign in parallel is a very nice thing).
So the way we actually implement it is like this:

1. The creator of the transaction that depends on the interest rate asks for the current rate. They can abort at this point
   if they want to.
2. They insert a command with that rate and the time it was obtained into the transaction.
3. They then send it to the oracle for signing, along with everyone else in parallel. The oracle checks that the command
   has correct data for the asserted time, and signs if so.

This same technique can be adapted to other types of oracle.

The oracle consists of a core class that implements the query/sign operations (for easy unit testing), and then a separate
class that binds it to the network layer.

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

Pay-per-play oracles
--------------------

Because the signature covers the transaction, and transactions may end up being forwarded anywhere, the fact itself
is independently checkable. However, this approach can still be useful when the data itself costs money, because the act
of issuing the signature in the first place can be charged for (e.g. by requiring the submission of a fresh
``Cash.State`` that has been re-assigned to a key owned by the oracle service). Because the signature covers the
*transaction* and not only the *fact*, this allows for a kind of weak pseudo-DRM over data feeds. Whilst a smart
contract could in theory include a transaction parsing and signature checking library, writing a contract in this way
would be conclusive evidence of intent to disobey the rules of the service (*res ipsa loquitur*). In an environment
where parties are legally identifiable, usage of such a contract would by itself be sufficient to trigger some sort of
punishment.
