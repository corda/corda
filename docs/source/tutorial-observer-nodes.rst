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

    .. code-block:: kotlin

            @InitiatedBy(Requester::class)
            class AutoOfferAcceptor(otherSideSession: FlowSession) : Acceptor(otherSideSession) {
                @Suspendable
                override fun call(): SignedTransaction {
                    val finalTx = super.call()
                    // Our transaction is now committed to the ledger, so report it to our regulator. We use a custom flow
                    // that wraps SendTransactionFlow to allow the receiver to customise how ReceiveTransactionFlow is run,
                    // and because in a real life app you'd probably have more complex logic here e.g. describing why the report
                    // was filed, checking that the reportee is a regulated entity and not some random node from the wrong
                    // country and so on.
                    val regulator = serviceHub.identityService.partiesFromName("Regulator", true).single()
                    subFlow(ReportToRegulatorFlow(regulator, finalTx))
                    return finalTx
                }
            }

            @InitiatingFlow
            class ReportToRegulatorFlow(private val regulator: Party, private val finalTx: SignedTransaction) : FlowLogic<Unit>() {
                @Suspendable
                override fun call() {
                    val session = initiateFlow(regulator)
                    subFlow(SendTransactionFlow(session, finalTx))
                }
            }

            @InitiatedBy(ReportToRegulatorFlow::class)
            class ReceiveRegulatoryReportFlow(private val otherSideSession: FlowSession) : FlowLogic<Unit>() {
                @Suspendable
                override fun call() {
                    // Start the matching side of SendTransactionFlow above, but tell it to record all visible states even
                    // though they (as far as the node can tell) are nothing to do with us.
                    subFlow(ReceiveTransactionFlow(otherSideSession, true, StatesToRecord.ALL_VISIBLE))
                }
            }

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

* Nodes only record each transaction once. If a node has already recorded a transaction in non-observer mode, it cannot
  later re-record the same transaction as an observer. This issue is tracked here:
  https://r3-cev.atlassian.net/browse/CORDA-883
