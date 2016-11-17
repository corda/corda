Running the demos
=================

The repository contains a small number of demo programs that run two-node networks, demonstrating functionality developed
so far. We have:

1. The trader demo, which shows a delivery-vs-payment atomic swap of commercial paper for cash. You can learn more about
   how this works in :doc:`protocol-state-machines`.
2. The IRS demo, which shows two nodes establishing an interest rate swap between them and performing fixings with a
   rates oracle, all driven via the HTTP API.
3. The IRS demo web interface - a web interface to the IRS demo.
4. The attachment demo, which demonstrates uploading attachments to nodes.
5. The SIMM valuation demo, a large demo which shows two nodes agreeing on a portfolio and valuing the initial margin
   using the Standard Initial Margin Model.

.. note:: The demos currently must be run from IntelliJ, this will change before M6.

.. note:: If any demos don't work please jump on our mailing list and let us know.

Trader demo
-----------

This demo brings up three nodes: Bank A, Bank B and a notary/network map node that they both use. Bank A will
be the buyer, and self-issues some cash in order to acquire the commercial paper from Bank B, the seller.

This can be run either from the command line (recommended for Mac/Linux/BSD users), or from inside IntelliJ.

To run from the command line:

1. Run ``./gradlew samples:trader-demo:deployNodes`` to create a set of configs and installs under ``samples/trader-demo/build/nodes``
2. Run ``./samples/trader-demo/build/nodes/runnodes`` to open up three new terminal tabs/windows with the three nodes.
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

1. Open the Corda project in IntelliJ and run the "Install" configuration
2. Open the Corda samples project in IntelliJ and run the "IRS Demo: Run Nodes" configuration
3. Run "IRS Demo: Run Upload Rates" to upload rates to the oracle.
4. Run "IRS Demo: Run Trade" to have nodes agree on a trade.
5. Run "IRS Demo: Run Date Change" to run the fixings.

In the "IRS Demo: Run Nodes" window you'll see a lot of activity when you run the trade and when you run the date change.
The date change rolls the clock forwards and causes the nodes to agree on the fixings over a period.

IRS web demo
------------

There is also an IRS web demo installed. To use this follow steps 1-3 in the IRS demo and then navigate to
http://localhost:10005/web/irsdemo and http://localhost:10005/web/irsdemo to see both node's view of the trades.

To use the demos click the "Create Deal" button, fill in the form, then click the "Submit" button. Now you will be
able to use the time controls at the top left of the home page to run the fixings. Click any individual trade in the
blotter to view it.

Attachment demo
---------------

1. Open the Corda project in IntelliJ and run the "Install" configuration
2. Open the Corda samples project in IntelliJ and run the "Attachment Demo: Run Nodes" configuration
3. Run "Attachment Demo: Run Recipient" - this waits for a trade to start
4. Run "Attachment Demo: Run Sender" - sends the attachment

In the "Attachment Demo: Run Nodes" window you should see some log lines scroll past, and within a few seconds the
message "File received - we're happy!" should be printed.

SIMM and Portfolio Demo
-----------------------

.. note:: Read more about this demo at :doc:`initialmarginagreement`.

To run the demo run:

1. Open the Corda project in IntelliJ and run the "Install" configuration
2. Open the Corda samples project in IntelliJ and run the "Simm Valuation Demo" configuration

Now open http://localhost:10005/web/simmvaluationdemo and http://localhost:10005/web/simmvaluationdemo to view the two nodes that this
will have started respectively. You can now use the demo by creating trades and agreeing the valuations.

