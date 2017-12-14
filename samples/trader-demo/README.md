Trader demo
-----------

This demo brings up four nodes: Bank A, Bank B, Bank Of Corda, and a notary node that they all use. Bank A
will be the buyer, and requests some cash from the Bank of Corda in order to acquire commercial paper from Bank B, the 
seller.

To run from the command line in Unix:

1. Run ``./gradlew samples:trader-demo:deployNodes`` to create a set of configs and installs under 
   ``samples/trader-demo/build/nodes``
2. Run ``./samples/trader-demo/build/nodes/runnodes`` to open up four new terminals with the four nodes
3. Run ``./gradlew samples:trader-demo:runBank`` to instruct the bank node to issue cash and commercial paper to the 
   buyer and seller nodes respectively
4. Run ``./gradlew samples:trader-demo:runSeller`` to trigger the transaction. If you entered ``flow watch``, you can 
   see flows running on both sides of transaction. Additionally you should see final trade information displayed to 
   your terminal

To run from the command line in Windows:

1. Run ``gradlew samples:trader-demo:deployNodes`` to create a set of configs and installs under 
   ``samples\trader-demo\build\nodes``
2. Run ``samples\trader-demo\build\nodes\runnodes`` to open up four new terminals with the four nodes
3. Run ``gradlew samples:trader-demo:runBank`` to instruct the buyer node to request issuance of some cash from the 
   Bank of Corda node
4. Run ``gradlew samples:trader-demo:runSeller`` to trigger the transaction. If you entered ``flow watch``, you can see 
   flows running on both sides of transaction. Additionally you should see final trade information displayed to your 
   terminal