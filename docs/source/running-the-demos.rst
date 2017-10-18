Samples
=======

.. contents::

A number of samples are provided with Corda, under the `samples/` folder, in order to provide worked examples of
how solutions to a number of common designs could be implemented in a CorDapp. New developers should start with the
:doc:`example CorDapp <tutorial-cordapp>`, rather than these samples. The primary samples are:

1. The :ref:`trader-demo`, which shows a delivery-vs-payment atomic swap of commercial paper for cash
2. The :ref:`attachment-demo`, which demonstrates uploading attachments to nodes
3. The :ref:`bank-of-corda-demo`, which shows a node acting as an issuer of assets (the Bank of Corda) while remote client
   applications request issuance of some cash on behalf of a node called Big Corporation
4. The :ref:`observer-demo`, which shows a pair of nodes issuing a state, with a third node acting as an observer who
   is required to witness all transactions for the contract, but is not a participant itself.

Documentation on running the samples can be found inside the sample directories themselves, in the `README.md` file.

.. note:: If you are running the demos from the command line in Linux (but not OS X), you may have to install `xterm`.

.. note:: If you would like to see flow activity on the nodes type in the node terminal ``flow watch``.

Please report any bugs with the samples on `GitHub <https://github.com/corda/corda/issues>`_. There are
also a number of more advanced demos which are intended for extended testing of Corda nodes, please refer to their
individual `README.md` files if required.
