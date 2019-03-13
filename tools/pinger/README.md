# Simple RPC client tool to exercise nodes and aid HA testing

The intent is that this program is deployed onto machines used for HA testing. The ``pinger.conf``
should be setup to point to the node and to have an appropriate partner node and notary to transact
simple cash flows with. Once started with ``java -jar pinger.jar`` the program will submit up to ``parallelFlows``
into the local node. As flows complete new ones are submitted to top this up and ensure messages are always flowing.
The principal HA test is:
 1. Start the pinger
 2. Force the relevant component failures.
 3. Wait for full recovery.
 4. Observe the restoration of new flow submissions and new log output in the pinger.
 5. Press CTRL+C on the pinger session. This will stop any new submissions and wait ``waitDoneTime`` seconds for all of them
 to finish. It will print any flows that had not been fully completed in the time window
 (the expectation is they actually failed during the recovery period).

An example ``pinger.conf`` is:
```HOCON
parallelFlows = 10
waitDoneTime = 60
nodeConnection = "localhost:10130"
rpcUser = "user"
rpcPassword = "password"
targetPeers = ["O=Bank B, L=London, C=GB"]
notary = "O=Notary, L=London, C=GB"
```