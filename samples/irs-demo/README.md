# IRS Demo

This demo brings up three nodes: Bank A, Bank B and a node that simultaneously runs a notary, a network map and an 
interest rates oracle. The two banks agree on an interest rate swap, and then do regular fixings of the deal as the 
time on a simulated clock passes.

Functionality is split into two parts - CordApp which provides actual distributed ledger backend and Spring Boot 
webapp which provides REST API and web frontend. Application communicate using Corda RPC protocol.

To run from the command line in Unix:
1. Run ``./gradlew samples:irs-demo:cordapp:deployNodes`` to install configs and a command line tool under 
   ``samples/irs-demo/cordapp/build``
2. Run ``./gradlew samples:irs-demo:web:deployWebapps`` to install configs and tools for running webservers
3. Move to the ``samples/irs-demo/`` directory
4. Run ``./cordapp/build/nodes/runnodes`` to open up three new terminals with the three nodes (you may have to install xterm)
5. On Linux, run ``./web/build/webapps/runwebapps`` to open three more terminals for associated webservers. On macOS,
   use the following command instead: ``osascript ./web/build/webapps/runwebapps.scpt``

To run from the command line in Windows:

1. Run ``gradlew.bat samples:irs-demo:cordapp:deployNodes`` to install configs and a command line tool under 
   ``samples\irs-demo\build``
2. Run ``gradlew.bat samples:irs-demo:web:deployWebapps`` to install configs and tools for running webservers
3. Run ``cd samples\irs-demo`` to change current working directory
4. Run ``cordapp\build\nodes\runnodes.bat`` to open up several 3 terminals for each nodes
5. Run ``web\build\webapps\webapps.bat`` to open up several 3 terminals for each nodes' webservers

This demo also has a web app. To use this, run nodes and then navigate to http://localhost:10007/ and 
http://localhost:10010/ to see each node's view of the ledger.

To use the web app, click the "Create Deal" button, fill in the form, then click the "Submit" button. You can then use 
the time controls at the top left of the home page to run the fixings. Click any individual trade in the blotter to 
view it.

*Note:* The IRS web UI currently has a bug when changing the clock time where it may show no numbers or apply fixings 
inconsistently. The issues will be addressed in a future milestone release. Meanwhile, you can take a look at a simpler 
oracle example here: https://github.com/corda/oracle-example.
