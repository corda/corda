.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Running our CorDapp
===================

Now that we've written a CorDapp, it's time to test it by running it on some real Corda nodes.

Deploying our CorDapp
---------------------
Let's take a look at the nodes we're going to deploy. Open the project's build file under ``java-source/build.gradle``
or ``kotlin-source/build.gradle`` and scroll down to the ``task deployNodes`` section. This section defines four
nodes - the Controller, and NodeA, NodeB and NodeC:

.. container:: codeset

    .. code-block:: kotlin

        task deployNodes(type: net.corda.plugins.Cordform, dependsOn: ['build']) {
            directory "./build/nodes"
            networkMap "CN=Controller,O=R3,OU=corda,L=London,C=GB"
            node {
                name "CN=Controller,O=R3,OU=corda,L=London,C=GB"
                advertisedServices = ["corda.notary.validating"]
                p2pPort 10002
                rpcPort 10003
                webPort 10004
                cordapps = []
            }
            node {
                name "CN=NodeA,O=NodeA,L=London,C=GB"
                advertisedServices = []
                p2pPort 10005
                rpcPort 10006
                webPort 10007
                cordapps = []
                rpcUsers = [[ user: "user1", "password": "test", "permissions": []]]
            }
            node {
                name "CN=NodeB,O=NodeB,L=New York,C=US"
                advertisedServices = []
                p2pPort 10008
                rpcPort 10009
                webPort 10010
                cordapps = []
                rpcUsers = [[ user: "user1", "password": "test", "permissions": []]]
            }
            node {
                name "CN=NodeC,O=NodeC,L=Paris,C=FR"
                advertisedServices = []
                p2pPort 10011
                rpcPort 10012
                webPort 10013
                cordapps = []
                rpcUsers = [[ user: "user1", "password": "test", "permissions": []]]
            }
        }

We have three standard nodes, plus a special Controller node that is running the network map service, and is also
advertising a validating notary service. Feel free to add additional node definitions here to expand the size of the
test network.

We can run this ``deployNodes`` task using Gradle. For each node definition, Gradle will:

* Package the project's source files into a CorDapp jar
* Create a new node in ``build/nodes`` with our CorDapp already installed

We can do that now by running the following commands from the root of the project:

.. code:: python

    // On Windows
    gradlew clean deployNodes

    // On Mac
    ./gradlew clean deployNodes

Running the nodes
-----------------
Running ``deployNodes`` will build the nodes under both ``java-source/build/nodes`` and ``kotlin-source/build/nodes``.
If we navigate to one of these folders, we'll see four node folder. Each node folder has the following structure:

    .. code:: python

        .
        // The runnable node
        |____corda.jar
        // The node's webserver
        |____corda-webserver.jar
        |____dependencies
        // The node's configuration file
        |____node.conf
        |____plugins
          // Our IOU CorDapp
          |____java/kotlin-source-0.1.jar

Let's start the nodes by running the following commands from the root of the project:

.. code:: python

    // On Windows for a Java CorDapp
    java-source/build/nodes/runnodes.bat

    // On Windows for a Kotlin CorDapp
    kotlin-source/build/nodes/runnodes.bat

    // On Mac for a Java CorDapp
    java-source/build/nodes/runnodes

    // On Mac for a Kotlin CorDapp
    kotlin-source/build/nodes/runnodes

This will start a terminal window for each node, and an additional terminal window for each node's webserver - eight
terminal windows in all. Give each node a moment to start - you'll know it's ready when its terminal windows displays
the message, "Welcome to the Corda interactive shell.".

  .. image:: resources/running_node.png
     :scale: 25%
     :align: center

Interacting with the nodes
--------------------------
Now that our nodes are running, let's order one of them to create an IOU by kicking off our ``IOUFlow``. In a larger
app, we'd generally provide a web API sitting on top of our node. Here, for simplicity, we'll be interacting with the
node via its built-in CRaSH shell.

Go to the terminal window displaying the CRaSH shell of Node A. Typing ``help`` will display a list of the available
commands.

We want to create an IOU of 100 with Node B. We start the ``IOUFlow`` by typing:

.. code:: python

    start IOUFlow arg0: 99, arg1: "CN=NodeB,O=NodeB,L=New York,C=US"

Node A and Node B will automatically agree an IOU.

If the flow worked, it should have led to the recording of a new IOU in the vaults of both Node A and Node B. Equally
importantly, Node C - although it sits on the same network - should not be aware of this transaction.

We can check the flow has worked by using an RPC operation to check the contents of each node's vault. Typing ``run``
will display a list of the available commands. We can examine the contents of a node's vault by running:

.. code:: python

     run vaultAndUpdates

And we can also examine a node's transaction storage, by running:

.. code:: python

     run verifiedTransactions

The vaults of Node A and Node B should both display the following output:

.. code:: python

    first:
    - state:
        data:
          value: 99
          sender: "CN=NodeA,O=NodeA,L=London,C=GB"
          recipient: "CN=NodeB,O=NodeB,L=New York,C=US"
          contract:
            legalContractReference: "559322B95BCF7913E3113962DC3F3CBD71C818C66977721580C045DC41C813A5"
          participants:
          - "CN=NodeA,O=NodeA,L=London,C=GB"
          - "CN=NodeB,O=NodeB,L=New York,C=US"
        notary: "CN=Controller,O=R3,OU=corda,L=London,C=GB,OU=corda.notary.validating"
        encumbrance: null
      ref:
        txhash: "656A1BF64D5AEEC6F6C944E287F34EF133336F5FC2C5BFB9A0BFAE25E826125F"
        index: 0
    second: "(observable)"

But the vault of Node C should output nothing!

.. code:: python

    first: []
    second: "(observable)"

Conclusion
----------
We have written a simple CorDapp that allows IOUs to be issued onto the ledger. Like all CorDapps, our
CorDapp is made up of three key parts:

* The ``IOUState``, representing IOUs on the ledger
* The ``IOUContract``, controlling the evolution of IOUs over time
* The ``IOUFlow``, orchestrating the process of agreeing the creation of an IOU on-ledger.

Together, these three parts completely determine how IOUs are created and evolved on the ledger.

Next steps
----------
You should now be ready to develop your own CorDapps. There's
`a more fleshed-out version of the IOU CorDapp <https://github.com/corda/cordapp-tutorial>`_
with an API and web front-end, and a set of example CorDapps in
`the main Corda repo <https://github.com/corda/corda>`_, under ``samples``. An explanation of how to run these
samples :doc:`here <running-the-demos>`.

As you write CorDapps, you can learn more about the API available :doc:`here <api>`.

If you get stuck at any point, please reach out on `Slack <https://slack.corda.net/>`_,
`Discourse <https://discourse.corda.net/>`_, or `Stack Overflow <https://stackoverflow.com/questions/tagged/corda>`_.