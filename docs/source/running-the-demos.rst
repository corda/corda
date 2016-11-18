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
7. The Bank of Corda demo, which demonstrates a node acting as an issuer of assets (the Bank of Corda) and remote client
    applications requesting issuance (via RPC, HTTP) of some cash on behalf of a node called Big Corporation.

.. note:: If any demos don't work please jump on our mailing list and let us know.


Important : Common Instructions for all demos
---------------------------------------------

The demos can be run either from the command line, or from inside IntelliJ. Running from the command line is
recommended if you are just wanting to see them run, using IntelliJ can be helpful if you want to debug or
develop the demos themselves. For more details about running via the command line or within IntelliJ - see :doc:`CLI-vs-IDE`.

*For all demos:* The ``install`` gradle task is automatically ran if required; this no longer needs to be run independently.

Trader demo
-----------

This demo brings up three nodes: Bank A, Bank B and a notary/network map node that they both use. Bank A will
be the buyer, and self-issues some cash in order to acquire commercial paper from Bank B, the seller.

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

1. Run ``./gradlew samples:irs-demo:deployNodes`` to install configs and a command line tool under ``samples/irs-demo/build``.
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

Bank Of Corda demo
------------------

This demo brings up three nodes: a notary, a node acting as the Bank of Corda that accepts requests for issuance of some asset
and a node acting as Big Corporation which requests issuance of an asset (cash in this example).
Upon receipt of a request the Bank of Corda node self-issues the asset and then transfers ownership to the requester
after successful notarisation and recording of the issue transaction on the ledger.

.. note:: The Bank of Corda is somewhat like the "Bitcoin faucet", that used to dispense free bitcoins to developers for
          testing and experimentation purposes.

To run from the command line (recommended for Mac/UNIX users!):

1. Run ``./gradlew samples:bank-of-corda-demo:deployNodes`` to create a set of configs and installs under ``samples/bank-of-corda-demo/build/nodes``
2. Run ``./samples/bank-of-corda-demo/build/nodes/runnodes`` to open up three new terminal tabs/windows with the three nodes.

.. note:: to verify the Bank of Corda node is alive and running navigate to the following URL
          http://localhost:10005/api/bank/date

3. Run ``./gradlew samples:bank-of-corda-demo:runRPCCashIssue`` in another terminal window to trigger a cash issuance request
4. Run ``./gradlew samples:bank-of-corda-demo:runWebCashIssue`` in another terminal window to trigger another cash issuance request
   Now look at the other windows to see the output of the demo.

Or you can run them from inside IntelliJ as follows:

1. Open the Corda project in IntelliJ and run the "Install" configuration
2. Open the Corda samples project in IntelliJ and run the "Bank Of Corda Demo: Run Issuer" configuration
3. Run "Bank Of Corda Demo: Run RPC Cash Issue" - requests issuance of some cash on behalf of Big Corporation via RPC
4. Run "Bank Of Corda Demo: Run Web Cash Issue" - requests issuance of some cash on behalf of Big Corporation via HTTP

In the "Bank Of Corda Demo: Run Issuer" window you should see the following information lines displayed:

- Awaiting issuance request
- Self issuing asset
- Transferring asset to issuance requester
- Confirming asset issuance to requester

In the the client issue request window you should see the following printed:

- Successfully processed Cash Issue request

Launch the Explorer application to visualize the issuance and transfer of cash on each node:

    ``./gradlew tools:explorer:run``

And use the following logon details:

- for the Bank of Corda node specify localhost, port 10004, username user1, password test
- for the Big Corporation node specify localhost, port 10006, username user1, password test

See https://docs.corda.net/node-explorer.html for further details on usage.

SIMM and Portfolio Demo - aka the Initial Margin Agreement Demo
---------------------------------------------------------------

Background and SIMM Introduction
********************************

This app is a demonstration of how Corda can be used for the real world requirement of initial margin calculation and
agreement; featuring the integration of complex and industry proven third party libraries into Corda nodes.

SIMM is an acronym for "Standard Initial Margin Model". It is effectively the calculation of a "margin" that is paid
by one party to another when they agree a trade on certain types of transaction. This margin is
paid such that, in the event of one of the counterparties suffering a credit event
(a financial term and a polite way to say defaulting, not paying the debts that are due, or potentially even bankruptcy),
then the party that is owed any sum already has some of the amount that it should have been paid. This payment to the
receiving party is a preventative measure in order to reduce the risk of a potentially catastrophic default domino
effect that caused the `Great Financial Crisis <https://en.wikipedia.org/wiki/Financial_crisis_of_2007%E2%80%932008>`_,
as it means that they can be assured that if they need to pay another party, they will have a proportion of the funds
that they have been relying on.

To enact this, in September 2016, the ISDA committee - with full backing from various governing bodies -
`issued a ruling on what is known as the ISDA SIMM ™ model <http://www2.isda.org/news/isda-simm-deployed-today-new-industry-standard-for-calculating-initial-margin-widely-adopted-by-market-participants>`_,
a way of fairly and consistently calculating this margin. Any parties wishing to trade a financial product that is
covered under this ruling would, independently, use this model and calculate their margin payment requirement,
agree it with their trading counterparty and then pay (or receive, depending on the results of this calculation)
this amount. In the case of disagreement that is not resolved in a timely fashion, this payment would increase
and so therefore it is in the parties' interest to reach agreement in as short as time frame as possible.

To be more accurate, the SIMM calculation is not performed on just one trade - it is calculated on an aggregate of
intermediary values (which in this model are sensitivities to risk factors) from a portfolio of trades; therefore
the input to a SIMM is actually this data, not the individual trades themselves.

Also note that implementations of the SIMM are actually protected and subject to license restrictions by ISDA
(this is due to the model itself being protected). We were fortunate enough to technically partner with
`OpenGamma <http://www.opengamma.com>`_  who allowed us to demonstrate the SIMM process using their proprietary model.
In the source code released, we have replaced their analytics engine with very simple stub functions that allow
the process to run without actually calculating correct values, and can easily be swapped out in place for their real libraries.


Open the Corda samples project in IntelliJ and run the "Simm Valuation Demo" configuration

Now open http://localhost:10005/web/simmvaluationdemo and http://localhost:10007/web/simmvaluationdemo to view the two
nodes that this will have started respectively. You can now use the demo by creating trades and agreeing the valuations.
Also see the README located in samples/simm-valuation-demo.


What happens in the demo (notionally)
*************************************

Preliminaries
    - Ensure that there are a number of live trades with another party financial products that are covered under the
      ISDA SIMM agreement (if none, then use the demo to enter some simple trades as described below).

Initial Margin Agreement Process
    - Agree that one will be performing the margining calculation against a portfolio of trades with another party, and agree the trades in that portfolio. In practice, one node will start the flow but it does not matter which node does.
    - Individually (at the node level), identify the data (static, reference etc) one will need in order to be able to calculate the metrics on those trades
    - Confirm with the other counterparty the dataset from the above set
    - Calculate any intermediary steps and values needed for the margin calculation (ie sensitivities to risk factors)
    - Agree on the results of these steps
    - Calculate the initial margin
    - Agree on the calculation of the above with the other party
    - In practice, pay (or receive) this margin (omitted for the sake of complexity for this example)


Demo execution (step by step)
*****************************

The demonstration can be run in two ways - via IntelliJ (which will allow you to add breakpoints, debug, etc), or via gradle and the command line.

Run with IntelliJ

    1. Open the ``corda`` project with IntelliJ
    2. Run the shared run configuration "SIMM Valuation Demo"

Run via CLI

    1. Navigate to the ``samples/simm-valuation-demo`` directory in your shell
    2. Run the gradle target ``deployNodes`` (ie; ``./gradlew deployNodes`` for Unix or ``gradlew.bat`` on Windows)

        a. Unix: ``cd simm-valuation-demo/build/nodes && ./runnodes``
        b. Windows: ``cd simm-valuation-demo/build/nodes & runnodes.bat``

Then (for both)
    3. Browse to http://localhost:10005/web/simmvaluationdemo
    4. Select the other counterparty (ie Bank B)
    5. Enter at least 3 trades - via the "Create New Trade" tab.
    6. On the "Agree Valuations" tab, click the "Start Calculations" button.


Additionally, you can confirm that these trades are not visible from `Bank C's node <http://localhost:10009/web/simmvaluationdemo/>`_.

Please note that any URL text after `simmvaluationdemo` should not be bookmarked or navigated directly to as they are only for aesthetics.