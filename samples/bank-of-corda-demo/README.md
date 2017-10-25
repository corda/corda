Bank Of Corda demo
------------------

This demo brings up three nodes: a notary, a node acting as the Bank of Corda that accepts requests for issuance of 
some asset and a node acting as Big Corporation which requests issuance of an asset (cash in this example).

Upon receipt of a request the Bank of Corda node self-issues the asset and then transfers ownership to the requester
after successful notarisation and recording of the issue transaction on the ledger.

.. note:: The Bank of Corda is somewhat like a "Bitcoin faucet" that dispenses free bitcoins to developers for
          testing and experimentation purposes.

To run from the command line in Unix:

1. Run ``./gradlew samples:bank-of-corda-demo:deployNodes`` to create a set of configs and installs under 
   ``samples/bank-of-corda-demo/build/nodes``
2. Run ``./samples/bank-of-corda-demo/build/nodes/runnodes`` to open up three new terminal tabs/windows with the three 
   nodes
3. Run ``./gradlew samples:bank-of-corda-demo:runRPCCashIssue`` to trigger a cash issuance request
4. Run ``./gradlew samples:bank-of-corda-demo:runWebCashIssue`` to trigger another cash issuance request.
   Now look at your terminal tab/window to see the output of the demo

To run from the command line in Windows:

1. Run ``gradlew samples:bank-of-corda-demo:deployNodes`` to create a set of configs and installs under 
   ``samples\bank-of-corda-demo\build\nodes``
2. Run ``samples\bank-of-corda-demo\build\nodes\runnodes`` to open up three new terminal tabs/windows with the three 
   nodes
3. Run ``gradlew samples:bank-of-corda-demo:runRPCCashIssue`` to trigger a cash issuance request
4. Run ``gradlew samples:bank-of-corda-demo:runWebCashIssue`` to trigger another cash issuance request.
   Now look at the your terminal tab/window to see the output of the demo

To verify that the Bank of Corda node is alive and running, navigate to the following URL: 
http://localhost:10007/api/bank/date

In the window you run the command you should see (in case of Web, RPC is similar):

- Requesting Cash via Web ...
- Successfully processed Cash Issue request

If you want to see flow activity enter in node's shell ``flow watch``. It will display all state machines running 
currently on the node.

Launch the Explorer application to visualize the issuance and transfer of cash for each node:

    ``./gradlew tools:explorer:run`` (on Unix) or ``gradlew tools:explorer:run`` (on Windows)

Using the following login details:

- For the Bank of Corda node: localhost / port 10006 / username bankUser / password test
- For the Big Corporation node: localhost / port 10009 / username bigCorpUser / password test

See https://docs.corda.net/node-explorer.html for further details on usage.