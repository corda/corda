.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Updating the contract
=====================

Remember that each state references a contract. The contract imposes constraints on transactions involving that state.
If the transaction does not obey the constraints of all the contracts of all its states, it cannot become a valid
ledger update.

We need to modify our contract so that the borrower's signature is required in any IOU creation transaction.

In ``IOUContract.java``/``IOUContract.kt``, change the imports block to the following:

.. container:: codeset

    .. literalinclude:: example-code/src/main/kotlin/net/corda/docs/tutorial/twoparty/contract.kt
        :language: kotlin
        :start-after: DOCSTART 01
        :end-before: DOCEND 01

    .. literalinclude:: example-code/src/main/java/net/corda/docs/java/tutorial/twoparty/IOUContract.java
        :language: java
        :start-after: DOCSTART 01
        :end-before: DOCEND 01

And update the final block of constraints in the ``requireThat`` block as follows:

.. container:: codeset

    .. literalinclude:: example-code/src/main/kotlin/net/corda/docs/tutorial/twoparty/contract.kt
        :language: kotlin
        :start-after: DOCSTART 02
        :end-before: DOCEND 02
        :dedent: 12

    .. literalinclude:: example-code/src/main/java/net/corda/docs/java/tutorial/twoparty/IOUContract.java
        :language: java
        :start-after: DOCSTART 02
        :end-before: DOCEND 02
        :dedent: 12

Progress so far
---------------
Our contract now imposes an additional constraint - the borrower must also sign an IOU creation transaction. Next, we
need to update ``IOUFlow`` so that it actually gathers the borrower's signature as part of the flow.