.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

The example CorDapp
===================

.. contents::

The example CorDapp allows nodes to agree IOUs with each other. Nodes will always agree to the creation of a new IOU
if:

* Its value is strictly positive
* The node is not trying to issue the IOU to itself

We will deploy the CorDapp on 4 test nodes:

* **Controller**, which hosts a validating notary service
* **PartyA**
* **PartyB**
* **PartyC**

Because data is only propagated on a need-to-know basis, any IOUs agreed between PartyA and PartyB become "shared
facts" between PartyA and PartyB only. PartyC won't be aware of these IOUs.

Downloading the example CorDapp
-------------------------------
We need to download the example CorDapp from GitHub.

* Set up your machine by following the :doc:`quickstart guide <getting-set-up>`

* Clone the example CorDapp from the `cordapp-example repository <https://github.com/corda/cordapp-example>`_ using
  the following command: ``git clone https://github.com/corda/cordapp-example``

* Change directories to the freshly cloned repo: ``cd cordapp-example``

.. note:: If you wish to build off the latest, unstable version of the codebase, follow the instructions in
   :doc:`building against Master <building-against-master>` instead.

Opening the example CorDapp in IntelliJ
---------------------------------------
Let's open the example CorDapp in IntelliJ IDEA.

**If opening a fresh IntelliJ instance**

* Open IntelliJ
* A dialogue box will appear:

  .. image:: resources/intellij-welcome.png
     :width: 400

* Click open, navigate to the folder where you cloned the ``cordapp-example``, and click OK

* IntelliJ will show several pop-up windows, one of which requires our attention:

  .. image:: resources/unlinked-gradle-project.png
     :width: 400

* Click the 'import gradle project' link. Press OK on the dialogue that pops up

* Gradle will now download all the project dependencies and perform some indexing. This usually takes a minute or so.

  * If the 'import gradle project' pop-up does not appear, click the small green speech bubble at the bottom-right of
    the IDE, or simply close and re-open IntelliJ again to make it reappear.

**If you already have IntelliJ open**

* Open the ``File`` menu

* Navigate to ``Open ...``

* Navigate to the directory where you cloned the ``cordapp-example``

* Click OK

Project structure
-----------------
The example CorDapp has the following directory structure:

.. sourcecode:: none

    .
    ├── config
    │   ├── dev
    │   │   └── log4j2.xml
    │   └── test
    │       └── log4j2.xml
    ├── doc
    │   └── example_flow.plantuml
    ├── gradle
    │   └── wrapper
    │       ├── gradle-wrapper.jar
    │       └── gradle-wrapper.properties
    ├── lib
    │   ├── README.txt
    │   └── quasar.jar
    ├── java-source
    │   └── ...
    ├── kotlin-source
    │   ├── build.gradle
    │   └── src
    │       ├── main
    │       │   ├── kotlin
    │       │   │   └── com
    │       │   │       └── example
    │       │   │           ├── api
    │       │   │           │   └── ExampleApi.kt
    │       │   │           ├── client
    │       │   │           │   └── ExampleClientRPC.kt
    │       │   │           ├── contract
    │       │   │           │   └── IOUContract.kt
    │       │   │           ├── flow
    │       │   │           │   └── ExampleFlow.kt
    │       │   │           ├── model
    │       │   │           │   └── IOU.kt
    │       │   │           ├── plugin
    │       │   │           │   └── ExamplePlugin.kt
    │       │   │           ├── schema
    │       │   │           │   └── IOUSchema.kt
    │       │   │           └── state
    │       │   │               └── IOUState.kt
    │       │   └── resources
    │       │       ├── META-INF
    │       │       │   └── services
    │       │       │       └── net.corda.webserver.services.WebServerPluginRegistry
    │       │       ├── certificates
    │       │       │   ├── readme.txt
    │       │       │   ├── sslkeystore.jks
    │       │       │   └── truststore.jks
    │       │       └── exampleWeb
    │       │           ├── index.html
    │       │           └── js
    │       │               └── angular-module.js
    │       └── test
    │           └── kotlin
    │               └── com
    │                   └── example
    │                       ├── Main.kt
    │                       ├── contract
    │                       │   └── IOUContractTests.kt
    │                       └── flow
    │                           └── IOUFlowTests.kt
    ├── .gitignore
    ├── LICENCE
    ├── README.md
    ├── TRADEMARK
    ├── build.gradle
    ├── gradle.properties
    ├── gradlew
    ├── gradlew.bat
    └── settings.gradle

The key files and directories are as follows:

* The **root directory** contains some gradle files, a README and a LICENSE
* **config** contains log4j configs
* **gradle** contains the gradle wrapper, which allows the use of Gradle without installing it yourself and worrying
  about which version is required
* **lib** contains the Quasar jar which rewrites our CorDapp's flows to be checkpointable
* **kotlin-source** contains the source code for the example CorDapp written in Kotlin
 * **kotlin-source/src/main/kotlin** contains the source code for the example CorDapp
 * **kotlin-source/src/main/resources** contains the certificate store, some static web content to be served by the
   nodes and the WebServerPluginRegistry file
 * **kotlin-source/src/test/kotlin** contains unit tests for the contracts and flows, and the driver to run the nodes
   via IntelliJ
* **java-source** contains the same source code, but written in Java. CorDapps can be developed in any language
  targeting the JVM

Running the example CorDapp
---------------------------
There are two ways to run the example CorDapp:

* Via the terminal
* Via IntelliJ

In both cases, we will deploy a set of test nodes with our CorDapp installed, then run the nodes. You can read more
about how we define the nodes to be deployed :doc:`here <deploying-a-node>`.

Terminal
~~~~~~~~

Building the example CorDapp
^^^^^^^^^^^^^^^^^^^^^^^^^^^^
* Open a terminal window in the ``cordapp-example`` directory

* Build the test nodes with our CorDapp using the following command:

  * Unix/Mac OSX: ``./gradlew deployNodes``

  * Windows: ``gradlew.bat deployNodes``

  This will automatically build four pre-configured nodes with our CorDapp installed. These nodes are meant for local
  testing only

.. note:: CorDapps can be written in any language targeting the JVM. In our case, we've provided the example source in
   both Kotlin (``/kotlin-source/src``) and Java (``/java-source/src``) Since both sets of source files are
   functionally identical, we will refer to the Kotlin build throughout the documentation.

* After the build process has finished, you will see the newly-build nodes in the ``kotlin-source/build/nodes`` folder

  * There will be one folder generated for each node you built, plus a ``runnodes`` shell script (or batch file on
    Windows) to run all the nodes simultaneously

  * Each node in the ``nodes`` folder has the following structure:

    .. sourcecode:: none

        . nodeName
        ├── corda.jar
        ├── node.conf
        └── cordapps

    ``corda.jar`` is the Corda runtime, ``cordapps`` contains our node's CorDapps, and the node's configuration is
    given by ``node.conf``

Running the example CorDapp
^^^^^^^^^^^^^^^^^^^^^^^^^^^
Start the nodes by running the following command from the root of the ``cordapp-example`` folder:

* Unix/Mac OSX: ``kotlin-source/build/nodes/runnodes``
* Windows: ``call kotlin-source\build\nodes\runnodes.bat``

.. warn:: On Unix/Mac OSX, do not click/change focus until all seven additional terminal windows have opened, or some
   nodes may fail to start.

For each node, the ``runnodes`` script creates a node tab/window:

.. sourcecode:: none

       ______               __
      / ____/     _________/ /___ _
     / /     __  / ___/ __  / __ `/         It's kind of like a block chain but
    / /___  /_/ / /  / /_/ / /_/ /          cords sounded healthier than chains.
    \____/     /_/   \__,_/\__,_/

    --- Corda Open Source 0.12.1 (da47f1c) -----------------------------------------------

    📚  New! Training now available worldwide, see https://corda.net/corda-training/

    Logs can be found in                    : /Users/joeldudley/Desktop/cordapp-example/kotlin-source/build/nodes/PartyA/logs
    Database connection url is              : jdbc:h2:tcp://10.163.199.132:54763/node
    Listening on address                    : 127.0.0.1:10005
    RPC service listening on address        : localhost:10006
    Loaded plugins                          : com.example.plugin.ExamplePlugin
    Node for "PartyA" started up and registered in 35.0 sec


    Welcome to the Corda interactive shell.
    Useful commands include 'help' to see what is available, and 'bye' to shut down the node.

    Fri Jul 07 10:33:47 BST 2017>>>

For every node except the controller, the script also creates a webserver terminal tab/window:

.. sourcecode:: none

    Logs can be found in /Users/joeldudley/Desktop/cordapp-example/kotlin-source/build/nodes/PartyA/logs/web
    Starting as webserver: localhost:10007
    Webserver started up in 42.02 sec

It usually takes around 60 seconds for the nodes to finish starting up. To ensure that all the nodes are running OK,
you can query the 'status' end-point located at ``http://localhost:[port]/api/status`` (e.g.
``http://localhost:10007/api/status`` for ``PartyA``).

IntelliJ
~~~~~~~~
* Select the ``Run Example CorDapp - Kotlin`` run configuration from the drop-down menu at the top right-hand side of
  the IDE

* Click the green arrow to start the nodes:

  .. image:: resources/run-config-drop-down.png
    :width: 400

  The node driver defined in ``/src/test/kotlin/com/example/Main.kt`` allows you to specify how many nodes you would like
  to run and the configuration settings for each node. For the example CorDapp, the driver starts up four nodes
  and adds an RPC user for all but the "Controller" node (which serves as the notary):

  .. sourcecode:: kotlin

      fun main(args: Array<String>) {
          // No permissions required as we are not invoking flows.
          val user = User("user1", "test", permissions = setOf())
          driver(isDebug = true) {
              startNode(getX500Name(O="Controller",L="London",C='GB"), setOf(ServiceInfo(ValidatingNotaryService.type)))
              val (nodeA, nodeB, nodeC) = Futures.allAsList(
                      startNode(getX500Name(O="PartyA",L="London",C="GB"), rpcUsers = listOf(user)),
                      startNode(getX500Name(O="PartyB",L="New York",C="US"), rpcUsers = listOf(user)),
                      startNode(getX500Name(O="PartyC",L="Paris",C="FR"), rpcUsers = listOf(user))).getOrThrow()

              startWebserver(nodeA)
              startWebserver(nodeB)
              startWebserver(nodeC)

              waitForAllNodesToFinish()
          }
      }

* To stop the nodes, press the red square button at the top right-hand side of the IDE, next to the run configurations

Later, we'll look at how the node driver can be useful for `debugging your CorDapp`_.

Interacting with the example CorDapp
------------------------------------

Via HTTP
~~~~~~~~
The CorDapp defines several HTTP API end-points and a web front-end. The end-points allow you to list the IOUs a node
is involved in, agree new IOUs, and see who is on the network.

The nodes are running locally on the following ports:

* PartyA:      ``localhost:10007``
* PartyB:      ``localhost:10010``
* PartyC:      ``localhost:10013``

These ports are defined in build.gradle and in each node's node.conf file under ``kotlin-source/build/nodes/NodeX``.

As the nodes start up, they should tell you which port their embedded web server is running on. The available API
endpoints are:

* ``/api/example/me``
* ``/api/example/peers``
* ``/api/example/ious``
* ``/api/example/create-iou`` with parameters ``iouValue`` and ``partyName`` which is CN name of a node

The web front-end is served from ``/web/example``.

An IOU can be created by sending a PUT request to the ``api/example/create-iou`` end-point directly, or by using the
the web form hosted at ``/web/example``.

.. warning:: The content in ``web/example`` is only available for demonstration purposes and does not implement
   anti-XSS, anti-XSRF or any other security techniques. Do not use this code in production.

Creating an IOU via the endpoint
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
To create an IOU between PartyA and PartyB, run the following command from the command line:

.. sourcecode:: bash

   curl -X PUT 'http://localhost:10007/api/example/create-iou?iouValue=1&partyName=O=PartyB,L=New%20York,C=US'

Note that both PartyA's port number (``10007``) and PartyB are referenced in the PUT request path. This command
instructs PartyA to agree an IOU with PartyB. Once the process is complete, both nodes will have a signed, notarised
copy of the IOU. PartyC will not.

Submitting an IOU via the web front-end
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
To create an IOU between PartyA and PartyB, navigate to ``/web/example``, click the "create IOU" button at the top-left
of the page, and enter the IOU details into the web-form. The IOU must have a positive value. For example:

.. sourcecode:: none

  Counter-party: Select from list
  Value (Int):   5

And click submit. Upon clicking submit, the modal dialogue will close, and the nodes will agree the IOU.

Once an IOU has been submitted
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
Assuming all went well, you should see some activity in PartyA's web-server terminal window:

.. sourcecode:: none

   >> Signing transaction with our private key.
   >> Gathering the counterparty's signature.
   >> Structural step change in child of Gathering the counterparty's signature.
   >> Collecting signatures from counter-parties.
   >> Verifying collected signatures.
   >> Done
   >> Obtaining notary signature and recording transaction.
   >> Structural step change in child of Obtaining notary signature and recording transaction.
   >> Requesting signature by notary service
   >> Broadcasting transaction to participants
   >> Done
   >> Done

You can view the newly-created IOU by accessing the vault of PartyA or PartyB:

*Via the HTTP API:*

* PartyA's vault: Navigate to http://localhost:10007/api/example/ious
* PartyB's vault: Navigate to http://localhost:10010/api/example/ious

*Via web/example:*

* PartyA: Navigate to http://localhost:10007/web/example and hit the "refresh" button
* PartyA: Navigate to http://localhost:10010/web/example and hit the "refresh" button

The vault and web front-end of PartyC (on ``localhost:10013``) will not display any IOUs. This is because PartyC was
not involved in this transaction.

Via the interactive shell (terminal only)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Nodes started via the terminal will display an interactive shell:

.. sourcecode:: none

    Welcome to the Corda interactive shell.
    Useful commands include 'help' to see what is available, and 'bye' to shut down the node.

    Fri Jul 07 16:36:29 BST 2017>>>

Type ``flow list`` in the shell to see a list of the flows that your node can run. In our case, this will return the
following list:

.. sourcecode:: none

   com.example.flow.ExampleFlow$Initiator
   net.corda.core.flows.ContractUpgradeFlow$Initiator
   net.corda.core.flows.ContractUpgradeFlow$Initiator
   net.corda.finance.flows.CashExitFlow
   net.corda.finance.flows.CashIssueAndPaymentFlow
   net.corda.finance.flows.CashIssueFlow
   net.corda.finance.flows.CashPaymentFlow

We can create a new IOU using the ``ExampleFlow$Initiator`` flow. For example, from the interactive shell of PartyA,
you can agree an IOU of 50 with PartyB by running
``flow start ExampleFlow$Initiator iouValue: 50, otherParty: "O=PartyB,L=New York,C=US"``.

This will print out the following progress steps:

.. sourcecode:: none

    ✅   Generating transaction based on new IOU.
    ✅   Verifying contract constraints.
    ✅   Signing transaction with our private key.
    ✅   Gathering the counterparty's signature.
        ✅   Collecting signatures from counter-parties.
        ✅   Verifying collected signatures.
    ✅   Obtaining notary signature and recording transaction.
        ✅   Requesting signature by notary service
                Requesting signature by Notary service
                Validating response from Notary service
        ✅   Broadcasting transaction to participants
    ✅   Done

We can also issue RPC operations to the node via the interactive shell. Type ``run`` to see the full list of available
operations.

Via the h2 web console
~~~~~~~~~~~~~~~~~~~~~~
You can connect directly to your node's database to see its stored states, transactions and attachments. To do so,
please follow the instructions in :doc:`node-database`.

Using the example RPC client
~~~~~~~~~~~~~~~~~~~~~~~~~~~~
The ``/src/main/kotlin-source/com/example/client/ExampleClientRPC.kt`` file is a simple utility that uses the client
RPC library to connect to a node. It will log any existing IOUs and listen for any future IOUs. If you haven't created
any IOUs when you first connect to one of the nodes, the client will simply log any future IOUs that are agreed.

*Running the client via IntelliJ:*

Select the 'Run Example RPC Client' run configuration which, by default, connects to PartyA. Click the green arrow to
run the client. You can edit the run configuration to connect on a different port.

*Running the client via the command line:*

Run the following gradle task:

``./gradlew runExampleClientRPCKotlin``

This will connect the RPC client to PartyA and log their past and future IOU activity.

You can close the application using ``ctrl+C``.

For more information on the client RPC interface and how to build an RPC client application, see:

* :doc:`Client RPC documentation <clientrpc>`
* :doc:`Client RPC tutorial <tutorial-clientrpc-api>`

Running Nodes Across Machines
-----------------------------
The nodes can be split across machines and configured to communicate across the network.

After deploying the nodes, navigate to the build folder (``kotlin-source/build/nodes``) and move some of the individual
node folders to a different machine (e.g. using a USB key). It is important that none of the nodes - including the
controller node - end up on more than one machine. Each computer should also have a copy of ``runnodes`` and
``runnodes.bat``.

For example, you may end up with the following layout:

* Machine 1: ``controller``, ``nodea``, ``runnodes``, ``runnodes.bat``
* Machine 2: ``nodeb``, ``nodec``, ``runnodes``, ``runnodes.bat``

You must now edit the configuration file for each node, including the controller. Open each node's config file,
and make the following changes:

* Change the Artemis messaging address to the machine's IP address (e.g. ``p2pAddress="10.18.0.166:10006"``)

After starting each node, the nodes will be able to see one another and agree IOUs among themselves.

Debugging your CorDapp
----------------------
Debugging is done via IntelliJ as follows:

1. Edit the node driver code in ``Main.kt`` based on the number of nodes you wish to start, along with any other
   configuration options. For example, the code below starts 4 nodes, with one being the network map service and
   notary. It also sets up RPC credentials for the three non-notary nodes

.. sourcecode:: kotlin

    fun main(args: Array<String>) {
        // No permissions required as we are not invoking flows.
        val user = User("user1", "test", permissions = setOf())
        driver(isDebug = true) {
            startNode(getX500Name(O="Controller",L="London",C="GB"), setOf(ServiceInfo(ValidatingNotaryService.type)))
            val (nodeA, nodeB, nodeC) = Futures.allAsList(
                    startNode(getX500Name(O="PartyA",L=London,C=GB"), rpcUsers = listOf(user)),
                    startNode(getX500Name(O="PartyB",L=New York,C=US"), rpcUsers = listOf(user)),
                    startNode(getX500Name(O="PartyC",L=Paris,C=FR"), rpcUsers = listOf(user))).getOrThrow()

            startWebserver(nodeA)
            startWebserver(nodeB)
            startWebserver(nodeC)

            waitForAllNodesToFinish()
        }
    }

2. Select and run the “Run Example CorDapp” run configuration in IntelliJ

3. IntelliJ will build and run the CorDapp. The remote debug ports for each node will be automatically generated and
   printed to the terminal. For example:

.. sourcecode:: none

    [INFO ] 15:27:59.533 [main] Node.logStartupInfo - Working Directory: /Users/joeldudley/cordapp-example/build/20170707142746/PartyA
    [INFO ] 15:27:59.533 [main] Node.logStartupInfo - Debug port: dt_socket:5007

4. Edit the “Debug CorDapp” run configuration with the port of the node you wish to connect to

5. Run the “Debug CorDapp” run configuration

6. Set your breakpoints and start interacting with the node you wish to connect to. When the node hits a breakpoint,
   execution will pause