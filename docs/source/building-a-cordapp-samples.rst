CorDapp samples
===============

There are two distinct sets of samples provided with Corda, one introducing new developers to how to write CorDapps, and
more complex worked examples of how solutions to a number of common designs could be implemented in a CorDapp.
The former can be found on `the Corda website <https://www.corda.net/samples/>`_. In particular, new developers
should start with the :doc:`example CorDapp <tutorial-cordapp>`.

The advanced samples are contained within the `samples/` folder of the Corda repository. The most generally useful of
these samples are:

1. The `trader-demo`, which shows a delivery-vs-payment atomic swap of commercial paper for cash
2. The `attachment-demo`, which demonstrates uploading attachments to nodes
3. The `bank-of-corda-demo`, which shows a node acting as an issuer of assets (the Bank of Corda) while remote client
   applications request issuance of some cash on behalf of a node called Big Corporation

Documentation on running the samples can be found inside the sample directories themselves, in the `README.md` file.

.. note:: If you would like to see flow activity on the nodes type in the node terminal ``flow watch``.

Please report any bugs with the samples on `GitHub <https://github.com/corda/corda/issues>`_.
