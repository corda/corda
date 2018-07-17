Oracles
=======

.. topic:: Summary

   * *A fact can be included in a transaction as part of a command*
   * *An oracle is a service that will only sign the transaction if the included fact is true*

.. only:: htmlmode

    Video
    -----
    .. raw:: html
    
        <iframe src="https://player.vimeo.com/video/214157956" width="640" height="360" frameborder="0" webkitallowfullscreen mozallowfullscreen allowfullscreen></iframe>
        <p></p>


Overview
--------
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

For privacy purposes, the oracle does not require to have access on every part of the transaction and the only
information it needs to see is their embedded, related to this oracle, command(s). We should also provide
guarantees that all of the commands requiring a signature from this oracle should be visible to
the oracle entity, but not the rest. To achieve that we use filtered transactions, in which the transaction proposer(s)
uses a nested Merkle tree approach to "tear off" the unrelated parts of the transaction. See :doc:`key-concepts-tearoffs`
for more information on how transaction tear-offs work.

If they wish to monetize their services, oracles can choose to only sign a transaction and attest to the validity of
the fact it contains for a fee.