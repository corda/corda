.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Updating the contract
=====================

Remember that each state references a contract. The contract imposes constraints on transactions involving that state.
If the transaction does not obey the constraints of all the contracts of all its states, it cannot become a valid
ledger update.

We need to modify our contract so that the lender's signature is required in any IOU creation transaction. This will
only require changing a single line of code. In ``IOUContract.java``/``IOUContract.kt``, update the final line of the
``requireThat`` block as follows:

.. container:: codeset

    .. code-block:: kotlin

        // Constraints on the signers.
        "The borrower and lender must be signers." using (command.signers.containsAll(listOf(
            out.borrower.owningKey, out.lender.owningKey)))

    .. code-block:: java

        // Constraints on the signers.
        check.using("The borrower and lender must be signers.", command.getSigners().containsAll(
            ImmutableList.of(borrower.getOwningKey(), lender.getOwningKey())));

Progress so far
---------------
Our contract now imposes an additional constraint - the lender must also sign an IOU creation transaction. Next, we
need to update ``IOUFlow`` so that it actually gathers the counterparty's signature as part of the flow.