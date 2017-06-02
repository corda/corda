Oracles
=======

.. topic:: Summary

   * *A fact can be included in a transaction as part of a command*
   * *An oracle is a service that will only sign the transaction if the included fact is true*

In many cases, a transaction's contractual validity depends on some external piece of data, such as the current
exchange rate. However, if we were to let each participant evaluate the transaction's validity based on their own
view of the current exchange rate, the contract's execution would be non-deterministic: some signers would consider the
transaction valid, while others would consider it invalid. As a result, disagreements would arise over the true state
of the ledger.

Corda addresses this issue using *oracles*. Oracles are network services that, upon request, provide commands
that encapsulate a specific fact (e.g. the exchange rate at time x) and list the oracle as a required signer.

If a node wishes to use a given fact in a transaction, they request a command asserting this fact from the oracle. If
the oracle considers the fact to be true, they send back the required command. The node then includes the command in
their transaction, and the oracle will sign the transaction to assert that the fact is true.

If they wish to monetize their services, oracles can choose to only sign a transaction and attest to the validity of
the fact it contains for a fee.

Transaction tear-offs are used to prevent the oracle from seeing information about the transaction that is not
relevant to them. See :doc:`merkle-trees` for further details.