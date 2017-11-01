Running the demos
=================

.. contents::

The `Corda repository <https://github.com/corda/corda>`_ contains a number of demo programs demonstrating
Corda's functionality. These examples are intended to demonstrate how specific functionality can be implemented in a
CorDapp. New developers should start with the :doc:`example CorDapp <tutorial-cordapp>`.

1. The :ref:`trader-demo`, which shows a delivery-vs-payment atomic swap of commercial paper for cash
2. The :ref:`attachment-demo`, which demonstrates uploading attachments to nodes
3. The :ref:`bank-of-corda-demo`, which shows a node acting as an issuer of assets (the Bank of Corda) while remote client
   applications request issuance of some cash on behalf of a node called Big Corporation

If any of the demos don't work, please raise an issue on `GitHub <https://github.com/corda/corda/issues>`_. There are
also a number of advanced demos which are intended for extended testing of Corda nodes, and are documented in
:doc:`advanced demos <advanced-running-the-demos>`.

.. note:: If you are running the demos from the command line in Linux (but not macOS), you may have to install xterm.

.. note:: If you would like to see flow activity on the nodes type in the node terminal ``flow watch``.

.. _trader-demo:

Trader demo
-----------

This demo brings up four nodes: Bank A, Bank B, Bank Of Corda, and a notary/network map node that they all use. Bank A will
be the buyer, and requests some cash from the Bank of Corda in order to acquire commercial paper from Bank B, the seller.

To run from the command line in Unix:

1. Run ``./gradlew samples:trader-demo:deployNodes`` to create a set of configs and installs under ``samples/trader-demo/build/nodes``
2. Run ``./samples/trader-demo/build/nodes/runnodes`` to open up four new terminals with the four nodes
3. Run ``./gradlew samples:trader-demo:runBank`` to instruct the bank node to issue cash and commercial paper to the buyer and seller nodes respectively.
4. Run ``./gradlew samples:trader-demo:runSeller`` to trigger the transaction. If you entered ``flow watch``

you can see flows running on both sides of transaction. Additionally you should see final trade information displayed
to your terminal.

To run from the command line in Windows:

1. Run ``gradlew samples:trader-demo:deployNodes`` to create a set of configs and installs under ``samples\trader-demo\build\nodes``
2. Run ``samples\trader-demo\build\nodes\runnodes`` to open up four new terminals with the four nodes
3. Run ``gradlew samples:trader-demo:runBank`` to instruct the buyer node to request issuance of some cash from the Bank of Corda node
4. Run ``gradlew samples:trader-demo:runSeller`` to trigger the transaction. If you entered ``flow watch``

you can see flows running on both sides of transaction. Additionally you should see final trade information displayed
to your terminal.

.. _attachment-demo:

Attachment demo
---------------

This demo brings up three nodes, and sends a transaction containing an attachment from one to the other.

To run from the command line in Unix:

1. Run ``./gradlew samples:attachment-demo:deployNodes`` to create a set of configs and installs under ``samples/attachment-demo/build/nodes``
2. Run ``./samples/attachment-demo/build/nodes/runnodes`` to open up three new terminal tabs/windows with the three nodes and webserver for BankB
3. Run ``./gradlew samples:attachment-demo:runRecipient``, which will block waiting for a trade to start
4. Run ``./gradlew samples:attachment-demo:runSender`` in another terminal window to send the attachment. Now look at the other windows to
   see the output of the demo

To run from the command line in Windows:

1. Run ``gradlew samples:attachment-demo:deployNodes`` to create a set of configs and installs under ``samples\attachment-demo\build\nodes``
2. Run ``samples\attachment-demo\build\nodes\runnodes`` to open up three new terminal tabs/windows with the three nodes and webserver for BankB
3. Run ``gradlew samples:attachment-demo:runRecipient``, which will block waiting for a trade to start
4. Run ``gradlew samples:attachment-demo:runSender`` in another terminal window to send the attachment. Now look at the other windows to
   see the output of the demo

.. _bank-of-corda-demo:

Bank Of Corda demo
------------------

This demo brings up three nodes: a notary, a node acting as the Bank of Corda that accepts requests for issuance of some asset
and a node acting as Big Corporation which requests issuance of an asset (cash in this example).

Upon receipt of a request the Bank of Corda node self-issues the asset and then transfers ownership to the requester
after successful notarisation and recording of the issue transaction on the ledger.

.. note:: The Bank of Corda is somewhat like a "Bitcoin faucet" that dispenses free bitcoins to developers for
          testing and experimentation purposes.

To run from the command line in Unix:

1. Run ``./gradlew samples:bank-of-corda-demo:deployNodes`` to create a set of configs and installs under ``samples/bank-of-corda-demo/build/nodes``
2. Run ``./samples/bank-of-corda-demo/build/nodes/runnodes`` to open up three new terminal tabs/windows with the three nodes
3. Run ``./gradlew samples:bank-of-corda-demo:runRPCCashIssue`` to trigger a cash issuance request
4. Run ``./gradlew samples:bank-of-corda-demo:runWebCashIssue`` to trigger another cash issuance request.
   Now look at your terminal tab/window to see the output of the demo

To run from the command line in Windows:

1. Run ``gradlew samples:bank-of-corda-demo:deployNodes`` to create a set of configs and installs under ``samples\bank-of-corda-demo\build\nodes``
2. Run ``samples\bank-of-corda-demo\build\nodes\runnodes`` to open up three new terminal tabs/windows with the three nodes
3. Run ``gradlew samples:bank-of-corda-demo:runRPCCashIssue`` to trigger a cash issuance request
4. Run ``gradlew samples:bank-of-corda-demo:runWebCashIssue`` to trigger another cash issuance request.
   Now look at the your terminal tab/window to see the output of the demo

.. note:: To verify that the Bank of Corda node is alive and running, navigate to the following URL:
          http://localhost:10007/api/bank/date

In the window you run the command you should see (in case of Web, RPC is simmilar):

- Requesting Cash via Web ...
- Successfully processed Cash Issue request

If you want to see flow activity enter in node's shell ``flow watch``. It will display all state machines
running currently on the node.

Launch the Explorer application to visualize the issuance and transfer of cash for each node:

    ``./gradlew tools:explorer:run`` (on Unix) or ``gradlew tools:explorer:run`` (on Windows)

Using the following login details:

- For the Bank of Corda node: localhost / port 10006 / username bankUser / password test
- For the Big Corporation node: localhost / port 10009 / username bigCorpUser / password test

See https://docs.corda.net/node-explorer.html for further details on usage.
