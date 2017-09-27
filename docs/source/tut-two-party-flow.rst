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

In ``IOUFlow.java``/``IOUFlow.kt``, update ``IOUFlow.call`` as follows:

.. container:: codeset

    .. code-block:: kotlin

        ...

        // We add the items to the builder.
        val state = IOUState(iouValue, me, otherParty)
        val cmd = Command(IOUContract.Create(), listOf(me.owningKey, otherParty.owningKey))
        txBuilder.withItems(state, cmd)

        // Verifying the transaction.
        txBuilder.verify(serviceHub)

        // Signing the transaction.
        val signedTx = serviceHub.signInitialTransaction(txBuilder)

        // Obtaining the counterparty's signature
        val otherSession = initiateFlow(otherParty)
        val fullySignedTx = subFlow(CollectSignaturesFlow(signedTx, setOf(otherSession), CollectSignaturesFlow.tracker()))

        // Finalising the transaction.
        subFlow(FinalityFlow(fullySignedTx))

    .. code-block:: java

        ...

        import com.google.common.collect.ImmutableList;
        import java.security.PublicKey;
        import java.util.Collections;
        import java.util.List;

        ...

        // We add the items to the builder.
        IOUState state = new IOUState(iouValue, me, otherParty);
        List<PublicKey> requiredSigners = ImmutableList.of(me.getOwningKey(), otherParty.getOwningKey());
        Command cmd = new Command(new IOUContract.Create(), requiredSigners);
        txBuilder.withItems(state, cmd);

        // Verifying the transaction.
        txBuilder.verify(getServiceHub());

        // Signing the transaction.
        final SignedTransaction signedTx = getServiceHub().signInitialTransaction(txBuilder);

        // Obtaining the counterparty's signature
        final FlowSession otherSession = initiateFlow(otherParty)
        final SignedTransaction fullySignedTx = subFlow(new CollectSignaturesFlow(signedTx, Collections.singleton(otherSession), CollectSignaturesFlow.Companion.tracker()));

        // Finalising the transaction.
        subFlow(new FinalityFlow(fullySignedTx));

        return null;

To make the borrower a required signer, we simply add the borrower's public key to the list of signers on the command.

``CollectSignaturesFlow``, meanwhile, takes a transaction signed by the flow initiator, and returns a transaction
signed by all the transaction's other required signers. We then pass this fully-signed transaction into
``FinalityFlow``.

Creating the borrower's flow
----------------------------
We're now ready to write the lender's flow, which will respond to the borrower's attempt to gather our signature.
In a new ``IOUFlowResponder.java`` file in Java, or within the ``App.kt`` file in Kotlin, add the following class:

.. container:: codeset

    .. code-block:: kotlin

        ...

        import net.corda.core.transactions.SignedTransaction

        ...

        @InitiatedBy(IOUFlow::class)
        class IOUFlowResponder(val otherPartySession: FlowSession) : FlowLogic<Unit>() {
            @Suspendable
            override fun call() {
                val signTransactionFlow = object : SignTransactionFlow(otherPartySession, SignTransactionFlow.tracker()) {
                    override fun checkTransaction(stx: SignedTransaction) = requireThat {
                        val output = stx.tx.outputs.single().data
                        "This must be an IOU transaction." using (output is IOUState)
                        val iou = output as IOUState
                        "The IOU's value can't be too high." using (iou.value < 100)
                    }
                }

                subFlow(signTransactionFlow)
            }
        }

    .. code-block:: java

        package com.template.flow;

        import co.paralleluniverse.fibers.Suspendable;
        import com.template.state.IOUState;
        import net.corda.core.contracts.ContractState;
        import net.corda.core.flows.FlowException;
        import net.corda.core.flows.FlowLogic;
        import net.corda.core.flows.FlowSession;
        import net.corda.core.flows.InitiatedBy;
        import net.corda.core.flows.SignTransactionFlow;
        import net.corda.core.transactions.SignedTransaction;
        import net.corda.core.utilities.ProgressTracker;

        import static net.corda.core.contracts.ContractsDSL.requireThat;

        @InitiatedBy(IOUFlow.class)
        public class IOUFlowResponder extends FlowLogic<Void> {
            private final FlowSession otherPartySession;

            public IOUFlowResponder(FlowSession otherPartySession) {
                this.otherPartySession = otherPartySession;
            }

            @Suspendable
            @Override
            public Void call() throws FlowException {
                class SignTxFlow extends SignTransactionFlow {
                    private signTxFlow(FlowSession otherPartySession, ProgressTracker progressTracker) {
                        super(otherPartySession, progressTracker);
                    }

                    @Override
                    protected void checkTransaction(SignedTransaction stx) {
                        requireThat(require -> {
                            ContractState output = stx.getTx().getOutputs().get(0).getData();
                            require.using("This must be an IOU transaction.", output instanceof IOUState);
                            IOUState iou = (IOUState) output;
                            require.using("The IOU's value can't be too high.", iou.getValue() < 100);
                            return null;
                        });
                    }
                }

                subFlow(new SignTxFlow(otherPartySession, SignTransactionFlow.Companion.tracker()));

                return null;
            }
        }

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