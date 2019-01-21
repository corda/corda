Trader demo
-----------

This demo brings up five nodes: Bank A, Bank B, Bank Of Corda, NonLogging Bank and a notary node that they all use. Bank A
will be the buyer, and requests some cash from the Bank of Corda in order to acquire commercial paper from Bank B, the 
seller.

The NonLogging Bank node is present to demonstrate the usage of the "Configuring Responder Flows" feature of Corda described [here](https://docs.corda.net/head/flow-overriding.html). 
The override is defined within the deployNodes section of the `build.gradle`. In this case, we are overriding the default responder for `net.corda.traderdemo.flow.SellerFlow`
to be a version that does not print out information about the transaction. 

```groovy
    node {
        name "O=NonLogging Bank,L=London,C=GB"
        p2pPort 10025
        rpcUsers = ext.rpcUsers
        rpcSettings {
            address "localhost:10026"
            adminAddress "localhost:10027"
        }
        extraConfig = ['h2Settings.address' : 'localhost:10035']
        flowOverride("net.corda.traderdemo.flow.SellerFlow", "net.corda.traderdemo.flow.BuyerFlow")
    }
```

To run from the command line in Unix:

1. Run ``./gradlew samples:trader-demo:deployNodes`` to create a set of configs and installs under 
   ``samples/trader-demo/build/nodes``
2. Run ``./samples/trader-demo/build/nodes/runnodes`` to open up five new terminals with the five nodes
3. Run ``./gradlew samples:trader-demo:runBank`` to instruct the bank node to issue cash and commercial paper to the 
   buyer and seller nodes respectively
4. Run ``./gradlew samples:trader-demo:runSeller`` to trigger the transaction. If you entered ``flow watch``, you can 
   see flows running on both sides of transaction. Additionally you should see final trade information displayed to 
   your terminal

To run from the command line in Windows:

1. Run ``gradlew samples:trader-demo:deployNodes`` to create a set of configs and installs under 
   ``samples\trader-demo\build\nodes``
2. Run ``samples\trader-demo\build\nodes\runnodes`` to open up five new terminals with the five nodes
3. Run ``gradlew samples:trader-demo:runBank`` to instruct the buyer node to request issuance of some cash from the 
   Bank of Corda node
4. Run ``gradlew samples:trader-demo:runSeller`` to trigger the transaction. If you entered ``flow watch``, you can see 
   flows running on both sides of transaction. Additionally you should see final trade information displayed to your 
   terminal