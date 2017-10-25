# IRS Demo

This demo brings up three nodes: Bank A, Bank B and a node that simultaneously runs a notary, a network map and an 
interest rates oracle. The two banks agree on an interest rate swap, and then do regular fixings of the deal as the 
time on a simulated clock passes.

To run from the command line in Unix:

1. Run ``./gradlew samples:irs-demo:deployNodes`` to install configs and a command line tool under 
   ``samples/irs-demo/build``
2. Run ``./gradlew samples:irs-demo:installDist``
3. Move to the ``samples/irs-demo/build`` directory
4. Run ``./nodes/runnodes`` to open up three new terminals with the three nodes (you may have to install xterm)

To run from the command line in Windows:

1. Run ``gradlew.bat samples:irs-demo:deployNodes`` to install configs and a command line tool under 
   ``samples\irs-demo\build``
2. Run ``gradlew.bat samples:irs-demo:installDist``
3. Run ``cd samples\irs-demo\build`` to change current working directory
4. Run ``nodes\runnodes`` to open up several 6 terminals, 2 for each node. First terminal is a web-server associated 
   with every node and second one is Corda interactive shell for the node

This demo also has a web app. To use this, run nodes and then navigate to http://localhost:10007/web/irsdemo and 
http://localhost:10010/web/irsdemo to see each node's view of the ledger.

To use the web app, click the "Create Deal" button, fill in the form, then click the "Submit" button. You can then use 
the time controls at the top left of the home page to run the fixings. Click any individual trade in the blotter to 
view it.

*Note:* The IRS web UI currently has a bug when changing the clock time where it may show no numbers or apply fixings 
inconsistently. The issues will be addressed in a future milestone release. Meanwhile, you can take a look at a simpler 
oracle example here: https://github.com/corda/oracle-example.