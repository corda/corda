Hello, World! Pt.2 - Contract constraints
=========================================

.. note:: This tutorial extends the CorDapp built during the :doc:`Hello, World tutorial <hello-world-introduction>`.

In the Hello, World tutorial, we built a CorDapp allowing us to model IOUs on ledger. Our CorDapp was made up of two
elements:

* An ``IOUState``, representing IOUs on the blockchain
* An ``IOUFlow`` and ``IOUFlowResponder`` flow pair, orchestrating the process of agreeing the creation of an IOU on-ledger

However, our CorDapp did not impose any constraints on the evolution of IOUs on the blockchain over time. Anyone was free
to create IOUs of any value, between any party.

In this tutorial, we'll write a contract to imposes rules on how an ``IOUState`` can change over time. In turn, this
will require some small changes to the flow we defined in the previous tutorial.

We'll start by writing the contract.

.. toctree::
   :maxdepth: 1

   tut-two-party-contract
   tut-two-party-flow