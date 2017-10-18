Observer demo
-------------

This demonstrates the interaction of four nodes in a massively cut down version of trade finance, where a common registry
node receives copies of all states for indexing. The sample contract (``ReceivableContract``) enforces that all transactions
must be observed by requiring an ``Observed`` command is included and signed by the registry node. In place of submitting
a transaction to the notary, they are submitted to the registry using ``RegistryObserverFlow.Client``, which signs and
then submits the transaction to the notary before returning the complete signed transaction.

The demo uses four nodes, Bank A and Bank B being the actual participants, plus independent notary and registry nodes.

To run from the command line in Unix:

1. Run ``./gradlew samples:observer-demo:deployNodes`` to create a set of configs and installs under ``samples/observer-demo/build/nodes``
2. Run ``./samples/observer-demo/build/nodes/runnodes`` to open up three new terminal tabs/windows with the three nodes and webserver for BankB
3. Run ``./gradlew samples:observer-demo:runDemo``, which will block waiting for a trade to start=

To run from the command line in Windows:

1. Run ``gradlew samples:observer-demo:deployNodes`` to create a set of configs and installs under ``samples\observer-demo\build\nodes``
2. Run ``samples\observer-demo\build\nodes\runnodes`` to open up three new terminal tabs/windows with the three nodes and webserver for BankB
3. Run ``gradlew samples:observer-demo:runDemo``, which will block waiting for a trade to start
