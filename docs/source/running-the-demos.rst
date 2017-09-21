Running the demos
=================

The `Corda repository <https://github.com/corda/corda>`_ contains a number of demo programs demonstrating
Corda's functionality:

1. The Trader Demo, which shows a delivery-vs-payment atomic swap of commercial paper for cash
2. The IRS Demo, which shows two nodes establishing an interest rate swap and performing fixings with a
   rates oracle
3. The Attachment Demo, which demonstrates uploading attachments to nodes
4. The Notary Demo, which shows three different types of notaries and a single node getting multiple transactions notarised.
5. The Bank of Corda Demo, which shows a node acting as an issuer of assets (the Bank of Corda) while remote client
   applications request issuance of some cash on behalf of a node called Big Corporation

If any of the demos don't work, please raise an issue on GitHub.

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

.. _irs-demo:

IRS demo
--------

This demo brings up three nodes: Bank A, Bank B and a node that simultaneously runs a notary, a network map and an interest rates
oracle. The two banks agree on an interest rate swap, and then do regular fixings of the deal as the time
on a simulated clock passes.

To run from the command line in Unix:

1. Run ``./gradlew samples:irs-demo:deployNodes`` to install configs and a command line tool under ``samples/irs-demo/build``
2. Run ``./gradlew samples:irs-demo:installDist``
3. Move to the ``samples/irs-demo/build`` directory
4. Run ``./nodes/runnodes`` to open up three new terminals with the three nodes (you may have to install xterm).

To run from the command line in Windows:

1. Run ``gradlew samples:irs-demo:deployNodes`` to install configs and a command line tool under ``samples\irs-demo\build``
2. Run ``gradlew samples:irs-demo:installDist``
3. Move to the ``samples\irs-demo\build`` directory
4. Run ``nodes\runnodes`` to open up three new terminals with the three nodes.

This demo also has a web app. To use this, run nodes and then navigate to
http://localhost:10007/web/irsdemo and http://localhost:10010/web/irsdemo to see each node's view of the ledger.

To use the web app, click the "Create Deal" button, fill in the form, then click the "Submit" button. You can then
use the time controls at the top left of the home page to run the fixings. Click any individual trade in the blotter to view it.

.. note:: The IRS web UI currently has a bug when changing the clock time where it may show no numbers or apply fixings inconsistently.
          The issues will be addressed in a future milestone release. Meanwhile, you can take a look at a simpler oracle example https://github.com/corda/oracle-example

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

.. _notary-demo:

Notary demo
-----------

This demo shows a party getting transactions notarised by either a single-node or a distributed notary service.
All versions of the demo start two counterparty nodes.
One of the counterparties will generate transactions that transfer a self-issued asset to the other party and submit them for notarisation.
The `Raft <https://raft.github.io/>`_ version of the demo will start three distributed notary nodes.
The `BFT SMaRt <https://bft-smart.github.io/library/>`_ version of the demo will start four distributed notary nodes.

The output will display a list of notarised transaction IDs and corresponding signer public keys. In the Raft distributed notary,
every node in the cluster can service client requests, and one signature is sufficient to satisfy the notary composite key requirement.
In the BFT SMaRt distributed notary, three signatures are required.
You will notice that successive transactions get signed by different members of the cluster (usually allocated in a random order).

To run the Raft version of the demo from the command line in Unix:

1. Run ``./gradlew samples:notary-demo:deployNodes``, which will create all three types of notaries' node directories
   with configs under ``samples/notary-demo/build/nodes/nodesRaft`` (``nodesBFT`` and ``nodesSingle`` for BFT and
   Single notaries).
2. Run ``./samples/notary-demo/build/nodes/nodesRaft/runnodes``, which will start the nodes in separate terminal windows/tabs.
   Wait until a "Node started up and registered in ..." message appears on each of the terminals
3. Run ``./gradlew samples:notary-demo:notarise`` to make a call to the "Party" node to initiate notarisation requests
   In a few seconds you will see a message "Notarised 10 transactions" with a list of transaction ids and the signer public keys

To run from the command line in Windows:

1. Run ``gradlew samples:notary-demo:deployNodes``, which will create all three types of notaries' node directories
   with configs under ``samples/notary-demo/build/nodes/nodesRaft`` (``nodesBFT`` and ``nodesSingle`` for BFT and
   Single notaries).
2. Run ``samples\notary-demo\build\nodes\nodesRaft\runnodes``, which will start the nodes in separate terminal windows/tabs.
   Wait until a "Node started up and registered in ..." message appears on each of the terminals
3. Run ``gradlew samples:notary-demo:notarise`` to make a call to the "Party" node to initiate notarisation requests
   In a few seconds you will see a message "Notarised 10 transactions" with a list of transaction ids and the signer public keys

To run the BFT SMaRt notary demo, use ``nodesBFT`` instead of ``nodesRaft`` in the path (you will see messages from notary nodes
trying to communicate each other sometime with connection errors, that's normal). For a single notary node, use ``nodesSingle``.

Notary nodes store consumed states in a replicated commit log, which is backed by a H2 database on each node.
You can ascertain that the commit log is synchronised across the cluster by accessing and comparing each of the nodes' backing stores
by using the H2 web console:

- Firstly, download `H2 web console <http://www.h2database.com/html/download.html>`_ (download the "platform-independent zip"),
  and start it using a script in the extracted folder: ``h2/bin/h2.sh`` (or ``h2\bin\h2`` for Windows)

- If you are uncertain as to which version of h2 to install or if you have connectivity issues, refer to ``build.gradle``
  located in the ``node`` directory and locate the compile step for ``com.h2database``. Use a client of the same
  major version - even if still in beta.

- The H2 web console should start up in a web browser tab. To connect we first need to obtain a JDBC connection string.
  Each node outputs its connection string in the terminal window as it starts up. In a terminal window where a node is running,
  look for the following string:

  ``Database connection url is              : jdbc:h2:tcp://10.18.0.150:56736/node``

  You can use the string on the right to connect to the h2 database: just paste it into the `JDBC URL` field and click *Connect*.
  You will be presented with a web application that enumerates all the available tables and provides an interface for you to query them using SQL

- The committed states are stored in the ``NOTARY_COMMITTED_STATES`` table. Note that the raw data is not human-readable,
  but we're only interested in the row count for this demo

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

