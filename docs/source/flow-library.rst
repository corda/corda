Flow Library
============

There are a number of built-in flows supplied with Corda, which cover some core functionality.

FinalityFlow
------------

The ``FinalityFlow`` verifies the given transactions, then sends them to the specified notaries.

If the notary agrees that the transactions are acceptable then they are from that point onwards committed to the ledger,
and will be written through to the vault. Additionally they will be distributed to the parties reflected in the participants
list of the states.

The transactions will be topologically sorted before commitment to ensure that dependencies are committed before
dependers, so you don't need to do this yourself.

The transactions are expected to have already been resolved: if their dependencies are not available in local storage or
within the given set, verification will fail. They must have signatures from all necessary parties other than the notary.

If specified, the extra recipients are sent all the given transactions. The base set of parties to inform of each
transaction are calculated on a per transaction basis from the contract-given set of participants.

The flow returns the same transactions, in the same order, with the additional signatures.


CollectSignaturesFlow
---------------------

The ``CollectSignaturesFlow`` is used to automate the collection of signatures from the counter-parties to a transaction.
This is instead of manually writing a flow to send a transaction to each required party and wait for them to provide a
signature in response. This flow has been written because many of the custom flows created by CorDapp developers often
re-implemented this logic which can be the source of bugs but is easy to automate.

You use the ``CollectSignaturesFlow`` by passing it a ``SignedTransaction`` which has been signed by the node calling the
``CollectSignaturesFlow``. The flow will handle the resolution of the counter-party identities and request a signature from
each.

Finally, the flow will verify all the signatures and return a ``SignedTransaction`` with all the collected signatures.

When using this flow you will have to subclass the ``AbstractCollectSignaturesFlowResponder`` and provide your own
implementation of the ``checkTransaction`` method. This is to add additional verification logic on the responder side.

Typically after calling the ``CollectSignaturesFlow`` you then called the ``FinalityFlow``.

ResolveTransactionsFlow
-----------------------

This ``ResolveTransactionsFlow`` is used to verify the validity of a transaction by recursively checking the validity of
all the dependencies. Once a transaction is checked it's inserted into local storage so it can be relayed and won't be
checked again.

A couple of constructors are provided that accept a single transaction. When these are used, the dependencies of that
transaction are resolved and then the transaction itself is verified. Again, if successful, the results are inserted
into the database as long as a [SignedTransaction] was provided. If only the [WireTransaction] form was provided
then this isn't enough to put into the local database, so only the dependencies are checked and inserted. This way
to use the flow is helpful when resolving and verifying a finished but partially signed transaction.

The flow returns a list of verified ``LedgerTransaction`` objects, in a depth-first order.