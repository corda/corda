Running the demos
=================

The repository contains a small number of demo programs that run two-node networks, demonstrating functionality developed
so far. We have:

1. The trader demo, which shows a delivery-vs-payment atomic swap of commercial paper for cash. You can learn more about
   how this works in :doc:`flow-state-machines`.
2. The IRS demo, which shows two nodes establishing an interest rate swap between them and performing fixings with a
   rates oracle, all driven via the HTTP API.
3. The IRS demo web interface - a web interface to the IRS demo.
4. The attachment demo, which demonstrates uploading attachments to nodes.
5. The SIMM valuation demo, a large demo which shows two nodes agreeing on a portfolio and valuing the initial margin
   using the Standard Initial Margin Model.
6. The distributed notary demo, which demonstrates a single node getting multiple transactions notarised by a distributed (Raft-based) notary.

.. note:: If any demos don't work please jump on our mailing list and let us know.

The demos can be run either from the command line, or from inside IntelliJ. Running from the command line is
recommended if you are just wanting to see them run, using IntelliJ can be helpful if you want to debug or
develop the demos themselves.

Trader demo
-----------

This demo brings up three nodes: Bank A, Bank B and a notary/network map node that they both use. Bank A will
be the buyer, and self-issues some cash in order to acquire the commercial paper from Bank B, the seller.

To run from the command line:

1. Run ``./gradlew samples:trader-demo:deployNodes`` to create a set of configs and installs under ``samples/trader-demo/build/nodes``
2. Run ``./samples/trader-demo/build/nodes/runnodes`` (or ``runnodes.bat`` on Windows) to open up three new terminals with the three nodes.
3. Run ``./gradlew samples:trader-demo:runBuyer`` to set up the buyer node with some self-issued cash. This step
   is not expected to print much.
4. Run ``./gradlew samples:trader-demo:runSeller`` to trigger the transaction. You can see both sides of the
   trade print their progress and final transaction state in the bank node tabs/windows.

To run from IntelliJ:

1. Open the Corda project in IntelliJ and run the "Install" configuration
2. Open the Corda samples project in IntelliJ and run the "Trader Demo: Run Nodes" configuration
3. Run "Trader Demo: Run Buyer"
4. Run "Trader Demo: Run Seller"

In the "Trader Demo: Run Nodes" windows you should see some log lines scroll past, and within a few seconds the messages
"Purchase complete - we are a happy customer!" and "Sale completed - we have a happy customer!" should be printed.

IRS demo
--------

This demo brings up three nodes: Bank A, Bank B and a node that runs a notary, a network map and an interest rates
oracle together. The two banks agree on an interest rate swap, and then do regular fixings of the deal as the time
on a simulated clock passes.

To run from the command line:

1. Run ``./gradlew samples:irs-demo:deployNodes samples:irs-demo:installDist`` to install configs and a command line tool under ``samples/irs-demo/build``.
2. Change to the ``samples/irs-demo/build`` directory.
3. Run ``./nodes/runnodes`` (or ``runnodes.bat`` on Windows) to open up three new terminals with the three nodes.
4. Run ``./install/irs-demo/bin/irs-demo --role UploadRates`` (or use ``irs-demo.bat`` on Windows). You should see a
   message be printed to the first node (the notary/oracle/network map node) saying that it has accepted some new
   interest rates.
5. Now run ``irs-demo`` as in step 4, but this time with ``--role Trade 1``. The number is a trade ID. You should
   see lots of activity as the nodes set up the deal, notarise it, get it signed by the oracle and so on.
6. Now run ``irs-demo --role Date 2016-12-12`` to roll the simulated clock forward and see some fixings take place.

To run from IntelliJ:

1. Open the Corda project in IntelliJ and run the "Install" configuration
2. Open the Corda samples project in IntelliJ and run the "IRS Demo: Run Nodes" configuration
3. Run "IRS Demo: Run Upload Rates" to upload rates to the oracle.
4. Run "IRS Demo: Run Trade" to have nodes agree on a trade.
5. Run "IRS Demo: Run Date Change" to run the fixings.

In the "IRS Demo: Run Nodes" window you'll see a lot of activity when you run the trade and when you run the date change.
The date change rolls the clock forwards and causes the nodes to agree on the fixings over a period.

There is also an web app as part of this demo. To use this set up the trades and then navigate to
http://localhost:10005/web/irsdemo and http://localhost:10007/web/irsdemo to see both node's view of the ledger.

To use the demos click the "Create Deal" button, fill in the form, then click the "Submit" button. Now you will be
able to use the time controls at the top left of the home page to run the fixings. Click any individual trade in the
blotter to view it.

Attachment demo
---------------

This demo brings up three nodes, and sends a transaction containing an attachment from one to the other. To run
it from the command line (recommended for Mac/UNIX users!):

1. Run ``./gradlew samples:attachment-demo:deployNodes`` to create a set of configs and installs under ``samples/attachment-demo/build/nodes``
2. Run ``./samples/attachment-demo/build/nodes/runnodes`` to open up three new terminal tabs/windows with the three nodes.
3. Run ``./gradlew samples:attachment-demo:runRecipient``, which will block waiting for something to happen.
4. Run ``./gradlew samples:attachment-demo:runSender`` in another terminal window to trigger the transaction.
   Now look at the other windows to see the output of the demo.

Or you can run them from inside IntelliJ, but when done this way, all the node output is printed to a single console.

1. Open the Corda project in IntelliJ and run the "Install" configuration
2. Open the Corda samples project in IntelliJ and run the "Attachment Demo: Run Nodes" configuration
3. Run "Attachment Demo: Run Recipient" - this waits for a trade to start
4. Run "Attachment Demo: Run Sender" - sends the attachment

In the "Attachment Demo: Run Nodes" window you should see some log lines scroll past, and within a few seconds the
message "File received - we're happy!" should be printed.

SIMM and Portfolio demo
-----------------------

.. note:: Read more about this demo at :doc:`initial-margin-agreement`.

To run the demo run:

1. Open the Corda project in IntelliJ and run the "Install" configuration
2. Open the Corda samples project in IntelliJ and run the "Simm Valuation Demo" configuration

Now open http://localhost:10005/web/simmvaluationdemo and http://localhost:10007/web/simmvaluationdemo to view the two nodes that this
will have started respectively. You can now use the demo by creating trades and agreeing the valuations.

.. _notary-demo:

Distributed Notary demo
-----------------------

This is a simple demonstration showing a party getting transactions notarised by a distributed `Raft <https://raft.github.io/>`_-based notary service.
The demo will start three distributed notary nodes, and two counterparty nodes. One of the parties will generate transactions
that move a self-issued asset to the other party, and submit them for notarisation.

The output will display a list of notarised transaction ids and corresponding signer public keys. In the Raft distributed notary
every node in the cluster services client requests, and one signature is sufficient to satisfy the notary composite key requirement.
You will notice that subsequent transactions get signed by different members of the cluster (usually allocated in a random order).

To run from IntelliJ:

1. Open the Corda samples project in IntelliJ and run the ``Notary Demo: Run Nodes`` configuration to start the nodes.
   Once all nodes are started you will see several "Node started up and registered in ..." messages.
2. Run ``Notary Demo: Run Notarisation`` to make a call to the "Party" node to initiate notarisation requests.
   In a few seconds you will see a message "Notarised 10 transactions" with a list of transaction ids and the signer public keys.

To run from the command line:

1. Run ``./gradlew samples:raft-notary-demo:deployNodes``, which will create node directories with configs under ``samples/raft-notary-demo/build/nodes``.
2. Run ``./samples/raft-notary-demo/build/nodes/runnodes``, which will start the nodes in separate terminal windows/tabs.
   Wait until a "Node started up and registered in ..." appears on each of the terminals.
3. Run ``./gradlew samples:raft-notary-demo:notarise`` to make a call to the "Party" node to initiate notarisation requests.
   In a few seconds you will see a message "Notarised 10 transactions" with a list of transaction ids and the signer public keys.

Notary nodes store consumed states in a replicated commit log, which is backed by a H2 database on each node.
To ascertain that the commit log is synchronised across the cluster you access and compare each of the nodes' backing stores
by using the H2 web console:

- Firstly, download `H2 web console <http://www.h2database.com/html/download.html>`_ (download the "platform-independent zip"),
  and start it using a script in the extracted folder: ``h2/bin/h2.sh`` (or ``h2.bat`` for Windows)

- The H2 web console should start up in a web browser tab. To connect we first need to obtain a JDBC connection string.
  Each node outputs its connection string in the terminal window as it starts up. In a terminal window where a node is running,
  look for the following string:

  ``Database connection url is              : jdbc:h2:tcp://10.18.0.150:56736/node``

  You can use the string on the right to connect to the h2 database: just paste it in to the `JDBC URL` field and click *Connect*.
  You will be presented with a web application that enumerates all the available tables and provides an interface for you to query them using SQL.
- The committed states are stored in the ``NOTARY_COMMITTED_STATES`` table. Note that the raw data is not human-readable,
  but we're only interested in the row count for this demo.