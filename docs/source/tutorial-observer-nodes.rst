.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Observer nodes
==============

Posting transactions to an observer node is a common requirement in finance, where regulators often want
to receive comprehensive reporting on all actions taken. By running their own node, regulators can receive a stream
of digitally signed, de-duplicated reports useful for later processing.

Adding support for observer nodes to your application is easy. The IRS (interest rate swap) demo shows to do it.

Just define a new flow that wraps the SendTransactionFlow/ReceiveTransactionFlow, as follows:

.. container:: codeset

    .. literalinclude:: ../../samples/irs-demo/cordapp/workflows-irs/src/main/kotlin/net.corda.irs/flows/AutoOfferFlow.kt
        :language: kotlin
        :start-after: DOCSTART 1
        :end-before: DOCEND 1

In this example, the ``AutoOfferFlow`` is the business logic, and we define two very short and simple flows to send
the transaction to the regulator. There are two important aspects to note here:

1. The ``ReportToRegulatorFlow`` is marked as an ``@InitiatingFlow`` because it will start a new conversation, context
   free, with the regulator.
2. The ``ReceiveRegulatoryReportFlow`` uses ``ReceiveTransactionFlow`` in a special way - it tells it to send the
   transaction to the vault for processing, including all states even if not involving our public keys. This is required
   because otherwise the vault will ignore states that don't list any of the node's public keys, but in this case,
   we do want to passively observe states we can't change. So overriding this behaviour is required.

If the states define a relational mapping (see :doc:`api-persistence`) then the regulator will be able to query the
reports from their database and observe new transactions coming in via RPC.

Caveats
-------

* By default, vault queries do not differentiate between states you recorded as a participant/owner, and states you 
  recorded as an observer. You will have to write custom vault queries that only return states for which you are a 
  participant/owner. See https://docs.corda.net/api-vault-query.html#example-usage for information on how to do this. 
  This also means that ``Cash.generateSpend`` should not be used when recording ``Cash.State`` states as an observer

* When an observer node is sent a transaction with the ALL_VISIBLE flag set, any transactions in the transaction history
  that have not already been received will also have ALL_VISIBLE states recorded. This mean a node that is both an observer
  and a participant may have some transactions with all states recorded and some with only relevant states recorded, even
  if those transactions are part of the same chain. As a result, there may be more states present in the vault than would be
  expected if just those transactions sent with the ALL_VISIBLE recording flag were processed in this way.

* Nodes may re-record transaction if they have previously recorded them as a participant and wish to record them as an observer. However,
  the node cannot resolve a forward chain of transactions if this is done. This means that if you wish to re-record a chain of transactions
  and get the new output states to be correctly marked as consumed, the full chain must be sent to the node *in order*.
