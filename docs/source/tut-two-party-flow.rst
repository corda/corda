.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Updating the flow
=================

To update the flow, we'll need to do two things:

* Update the lender's side of the flow to request the borrower's signature
* Create a flow for the borrower to run in response to a signature request from the lender

Updating the lender's flow
--------------------------
In the original CorDapp, we automated the process of notarising a transaction and recording it in every party's vault
by invoking a built-in flow called ``FinalityFlow`` as a subflow. We're going to use another pre-defined flow, called
``CollectSignaturesFlow``, to gather the borrower's signature.

We also need to add the borrower's public key to the transaction's command, making the borrower one of the required
signers on the transaction.

In ``IOUFlow.java``/``IOUFlow.kt``, change the imports block to the following:

.. container:: codeset

    .. literalinclude:: example-code/src/main/kotlin/net/corda/docs/tutorial/twoparty/flow.kt
        :language: kotlin
        :start-after: DOCSTART 01
        :end-before: DOCEND 01

    .. literalinclude:: example-code/src/main/java/net/corda/docs/java/tutorial/twoparty/IOUFlow.java
        :language: java
        :start-after: DOCSTART 01
        :end-before: DOCEND 01

And update ``IOUFlow.call`` by changing the code following the creation of the ``TransactionBuilder`` as follows:

.. container:: codeset

    .. literalinclude:: example-code/src/main/kotlin/net/corda/docs/tutorial/twoparty/flow.kt
        :language: kotlin
        :start-after: DOCSTART 02
        :end-before: DOCEND 02
        :dedent: 8

    .. literalinclude:: example-code/src/main/java/net/corda/docs/java/tutorial/twoparty/IOUFlow.java
        :language: java
        :start-after: DOCSTART 02
        :end-before: DOCEND 02
        :dedent: 8

To make the borrower a required signer, we simply add the borrower's public key to the list of signers on the command.

We now need to communicate with the borrower to request their signature. Whenever you want to communicate with another
party in the context of a flow, you first need to establish a flow session with them. If the counterparty has a
``FlowLogic`` registered to respond to the ``FlowLogic`` initiating the session, a session will be established. All
communication between the two ``FlowLogic`` instances will then place as part of this session.

Once we have a session with the borrower, we gather the borrower's signature using ``CollectSignaturesFlow``, which
takes:

* A transaction signed by the flow initiator
* A list of flow-sessions between the flow initiator and the required signers

And returns a transaction signed by all the required signers.

We then pass this fully-signed transaction into ``FinalityFlow``.

Creating the borrower's flow
----------------------------
We're now ready to write the lender's flow, which will respond to the borrower's attempt to gather our signature.
In a new ``IOUFlowResponder.java`` file in Java, or within the ``App.kt`` file in Kotlin, add the following class:

.. container:: codeset

    .. literalinclude:: example-code/src/main/kotlin/net/corda/docs/tutorial/twoparty/flowResponder.kt
        :language: kotlin
        :start-after: DOCSTART 01
        :end-before: DOCEND 01

    .. literalinclude:: example-code/src/main/java/net/corda/docs/java/tutorial/twoparty/IOUFlowResponder.java
        :language: java
        :start-after: DOCSTART 01
        :end-before: DOCEND 01

As with the ``IOUFlow``, our ``IOUFlowResponder`` flow is a ``FlowLogic`` subclass where we've overridden
``FlowLogic.call``.

The flow is annotated with ``InitiatedBy(IOUFlow.class)``, which means that your node will invoke
``IOUFlowResponder.call`` when it receives a message from a instance of ``Initiator`` running on another node. What
will this message from the ``IOUFlow`` be? If we look at the definition of ``CollectSignaturesFlow``, we can see that
we'll be sent a ``SignedTransaction``, and are expected to send back our signature over that transaction.

We could handle this manually. However, there is also a pre-defined flow called ``SignTransactionFlow`` that can handle
this process for us automatically. ``SignTransactionFlow`` is an abstract class, and we must subclass it and override
``SignTransactionFlow.checkTransaction``.

Once we've defined the subclass, we invoke it using ``FlowLogic.subFlow``, and the communication with the borrower's
and the lender's flow is conducted automatically.

CheckTransactions
^^^^^^^^^^^^^^^^^
``SignTransactionFlow`` will automatically verify the transaction and its signatures before signing it. However, just
because a transaction is valid doesn't mean we necessarily want to sign. What if we don't want to deal with the
counterparty in question, or the value is too high, or we're not happy with the transaction's structure?

Overriding ``SignTransactionFlow.checkTransaction`` allows us to define these additional checks. In our case, we are
checking that:

* The transaction involves an ``IOUState`` - this ensures that ``IOUContract`` will be run to verify the transaction
* The IOU's value is less than some amount (100 in this case)

If either of these conditions are not met, we will not sign the transaction - even if the transaction and its
signatures are valid.

Conclusion
----------
We have now updated our flow to gather the lender's signature as well, in line with the constraints in ``IOUContract``.
We can now run our updated CorDapp, using the instructions :doc:`here <hello-world-running>`.

Our CorDapp now requires agreement from both the lender and the borrower before an IOU can be created on the ledger.
This prevents either the lender or the borrower from unilaterally updating the ledger in a way that only benefits
themselves.