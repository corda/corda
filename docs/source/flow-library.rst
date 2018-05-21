Flow library
============

There are a number of built-in flows supplied with Corda, which cover some core functionality.

FinalityFlow
------------

The ``FinalityFlow`` verifies the given transactions, then sends them to the specified notary.

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

The ``CollectSignaturesFlow`` is used to automate the collection of signatures from the counterparties to a transaction.

You use the ``CollectSignaturesFlow`` by passing it a ``SignedTransaction`` which has at least been signed by yourself.
The flow will handle the resolution of the counterparty identities and request a signature from each counterparty.

Finally, the flow will verify all the signatures  and return a ``SignedTransaction`` with all the collected signatures.

When using this flow on the responding side you will have to subclass the ``AbstractCollectSignaturesFlowResponder`` and
provide your own implementation of the ``checkTransaction`` method. This is to add additional verification logic on the
responder side. Types of things you will need to check include:

* Ensuring that the transaction you are receiving is the transaction you *EXPECT* to receive. I.e. is has the expected
  type of inputs and outputs
* Checking that the properties of the outputs are as you would expect, this is in the absence of integrating reference
  data sources to facilitate this for us
* Checking that the transaction is not incorrectly spending (perhaps maliciously) one of your asset states, as potentially
  the transaction creator has access to some of your state references

Typically after calling the ``CollectSignaturesFlow`` you then called the ``FinalityFlow``.

SendTransactionFlow/ReceiveTransactionFlow
------------------------------------------

The ``SendTransactionFlow`` and ``ReceiveTransactionFlow`` are used to automate the verification of the transaction by
recursively checking the validity of all the dependencies. Once a transaction is received and checked it's inserted into
local storage so it can be relayed and won't be checked again.

The ``SendTransactionFlow`` sends the transaction to the counterparty and listen for data request as the counterparty
validating the transaction, extra checks can be implemented to restrict data access by overriding the ``verifyDataRequest``
method inside ``SendTransactionFlow``.

The ``ReceiveTransactionFlow`` returns a verified ``SignedTransaction``.