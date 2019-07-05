.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Running the example CorDapp
===========================

.. contents::

The example CorDapp allows nodes to agree IOUs with each other, as long as they obey the following contract rules:

* The IOU's value is strictly positive
* A node is not trying to issue an IOU to itself

We will deploy and run the CorDapp on four test nodes:

* **Notary**, which runs a notary service
* **PartyA**
* **PartyB**
* **PartyC**

Because data is only propagated on a need-to-know basis, any IOUs agreed between PartyA and PartyB become "shared
facts" between PartyA and PartyB only. PartyC won't be aware of these IOUs.

Downloading the example CorDapp
-------------------------------
Start by downloading the example CorDapp from GitHub:

* Set up your machine by following the :doc:`quickstart guide <getting-set-up>`

* Clone the samples repository from using the following command: ``git clone https://github.com/corda/samples``

* Change directories to the ``cordapp-example`` folder: ``cd samples/cordapp-example``

Opening the example CorDapp in IntelliJ
---------------------------------------
Let's open the example CorDapp in IntelliJ IDEA:

* Open IntelliJ

* A splash screen will appear. Click ``open``, navigate to and select the ``cordapp-example`` folder, and click ``OK``

* Once the project is open, click ``File``, then ``Project Structure``. Under ``Project SDK:``, set the project SDK by
  clicking ``New...``, clicking ``JDK``, and navigating to ``C:\Program Files\Java\jdk1.8.0_XXX`` on Windows or ``Library/Java/JavaVirtualMachines/jdk1.8.XXX`` on MacOSX (where ``XXX`` is the
  latest minor version number). Click ``Apply`` followed by ``OK``

* Again under ``File`` then ``Project Structure``, select ``Modules``. Click ``+``, then ``Import Module``, then select
  the ``cordapp-example`` folder and click ``Open``. Choose to ``Import module from external model``, select
  ``Gradle``, click ``Next`` then ``Finish`` (leaving the defaults) and ``OK``

* Gradle will now download all the project dependencies and perform some indexing. This usually takes a minute or so

Project structure
~~~~~~~~~~~~~~~~~
The example CorDapp has the following structure:

.. sourcecode:: none

    .
    ├── LICENCE
    ├── README.md
    ├── TRADEMARK
    ├── build.gradle
    ├── clients
    │   ├── build.gradle
    │   └── src
    │       └── main
    │           ├── kotlin
    │           │   └── com
    │           │       └── example
    │           │           └── server
    │           │               ├── MainController.kt
    │           │               ├── NodeRPCConnection.kt
    │           │               └── Server.kt
    │           └── resources
    │               ├── application.properties
    │               └── public
    │                   ├── index.html
    │                   └── js
    │                       └── angular-module.js
    ├── config
    │   ├── dev
    │   │   └── log4j2.xml
    │   └── test
    │       └── log4j2.xml
    ├── contracts-java
    │   ├── build.gradle
    │   └── src
    │       └── main
    │           └── java
    │               └── com
    │                   └── example
    │                       ├── contract
    │                       │   └── IOUContract.java
    │                       ├── schema
    │                       │   ├── IOUSchema.java
    │                       │   └── IOUSchemaV1.java
    │                       └── state
    │                           └── IOUState.java
    ├── contracts-kotlin
    │   ├── build.gradle
    │   └── src
    │       └── main
    │           └── kotlin
    │               └── com
    │                   └── example
    │                       ├── contract
    │                       │   └── IOUContract.kt
    │                       ├── schema
    │                       │   └── IOUSchema.kt
    │                       └── state
    │                           └── IOUState.kt
    ├── cordapp-example.iml
    ├── gradle
    │   └── wrapper
    │       ├── gradle-wrapper.jar
    │       └── gradle-wrapper.properties
    ├── gradle.properties
    ├── gradlew
    ├── gradlew.bat
    ├── lib
    │   ├── README.txt
    │   └── quasar.jar
    ├── settings.gradle
    ├── workflows-java
    │   ├── build.gradle
    │   └── src
    │       ├── integrationTest
    │       │   └── java
    │       │       └── com
    │       │           └── example
    │       │               └── DriverBasedTests.java
    │       ├── main
    │       │   └── java
    │       │       └── com
    │       │           └── example
    │       │               └── flow
    │       │                   └── ExampleFlow.java
    │       └── test
    │           └── java
    │               └── com
    │                   └── example
    │                       ├── NodeDriver.java
    │                       ├── contract
    │                       │   └── IOUContractTests.java
    │                       └── flow
    │                           └── IOUFlowTests.java
    └── workflows-kotlin
        ├── build.gradle
        └── src
            ├── integrationTest
            │   └── kotlin
            │       └── com
            │           └── example
            │               └── DriverBasedTests.kt
            ├── main
            │   └── kotlin
            │       └── com
            │           └── example
            │               └── flow
            │                   └── ExampleFlow.kt
            └── test
                └── kotlin
                    └── com
                        └── example
                            ├── NodeDriver.kt
                            ├── contract
                            │   └── IOUContractTests.kt
                            └── flow
                                └── IOUFlowTests.kt

The key files and directories are as follows:

* The **root directory** contains some gradle files, a README and a LICENSE
* **config** contains log4j2 configs
* **gradle** contains the gradle wrapper, which allows the use of Gradle without installing it yourself and worrying
  about which version is required
* **lib** contains the Quasar jar which rewrites our CorDapp's flows to be checkpointable
* **clients** contains the source code for spring boot integration
* **contracts-java** and **workflows-java** contain the source code for the example CorDapp written in Java
* **contracts-kotlin** and **workflows-kotlin** contain the same source code, but written in Kotlin. CorDapps can be developed in either Java and Kotlin

Running the example CorDapp
---------------------------
There are two ways to run the example CorDapp:

* Via the terminal
* Via IntelliJ

Both approaches will create a set of test nodes, install the CorDapp on these nodes, and then run the nodes. You can
read more about how we generate nodes :doc:`here <generating-a-node>`.

Running the example CorDapp from the terminal
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Building the example CorDapp
^^^^^^^^^^^^^^^^^^^^^^^^^^^^
* Open a terminal window in the ``cordapp-example`` directory

* Run the ``deployNodes`` Gradle task to build four nodes with our CorDapp already installed on them:

  * Unix/Mac OSX: ``./gradlew deployNodes``

  * Windows: ``gradlew.bat deployNodes``

.. note:: CorDapps can be written in any language targeting the JVM. In our case, we've provided the example source in
   both Kotlin and Java. Since both sets of source files are functionally identical, we will refer to the Kotlin version
   throughout the documentation.

* After the build finishes, you will see the following output in the ``workflows-kotlin/build/nodes`` folder:

  * A folder for each generated node
  * A ``runnodes`` shell script for running all the nodes simultaneously on osX
  * A ``runnodes.bat`` batch file for running all the nodes simultaneously on Windows

* Each node in the ``nodes`` folder will have the following structure:

  .. sourcecode:: none
      
      . nodeName
      ├── additional-node-infos  // 
      ├── certificates
      ├── corda.jar              // The Corda node runtime
      ├── cordapps               // The node's CorDapps
      │   ├── corda-finance-contracts-|corda_version|.jar
      │   ├── corda-finance-workflows-|corda_version|.jar
      │   └── cordapp-example-0.1.jar
      ├── drivers
      ├── logs
      ├── network-parameters
      ├── node.conf              // The node's configuration file
      ├── nodeInfo-<HASH>        // The hash will be different each time you generate a node
      └── persistence.mv.db      // The node's database

.. note:: ``deployNodes`` is a utility task to create an entirely new set of nodes for testing your CorDapp. In production, 
   you would instead create a single node as described in :doc:`generating-a-node` and build your CorDapp JARs as described 
   in :doc:`cordapp-build-systems`.
      
Running the example CorDapp
^^^^^^^^^^^^^^^^^^^^^^^^^^^
Start the nodes by running the following command from the root of the ``cordapp-example`` folder:

* Unix/Mac OSX: ``workflows-kotlin/build/nodes/runnodes`` 
* Windows: ``call workflows-kotlin\build\nodes\runnodes.bat``

Each Spring Boot server needs to be started in its own terminal/command prompt, replace X with A, B and C:

* Unix/Mac OSX: ``./gradlew runPartyXServer``
* Windows: ``gradlew.bat runPartyXServer``

Look for the Started ServerKt in X seconds message, don't rely on the % indicator.

.. warning:: On Unix/Mac OSX, do not click/change focus until all seven additional terminal windows have opened, or some
   nodes may fail to start. You can run ``workflows-kotlin/build/nodes/runnodes --headless`` to prevent each server from opening in a new terminal window. To interact with the nodes will need to use ssh, see :doc:`shell`.

For each node, the ``runnodes`` script creates a node tab/window:

.. sourcecode:: none

      ______               __
     / ____/     _________/ /___ _
    / /     __  / ___/ __  / __ `/         Top tip: never say "oops", instead
   / /___  /_/ / /  / /_/ / /_/ /          always say "Ah, Interesting!"
   \____/     /_/   \__,_/\__,_/

   --- Corda Open Source corda-|corda_version| (4157c25) -----------------------------------------------


   Logs can be found in                    : /Users/joeldudley/Desktop/cordapp-example/workflows-kotlin/build/nodes/PartyA/logs
   Database connection url is              : jdbc:h2:tcp://localhost:59472/node
   Incoming connection address             : localhost:10005
   Listening on port                       : 10005
   Loaded CorDapps                         : corda-finance-corda-|corda_version|, cordapp-example-0.1, corda-core-corda-|corda_version|
   Node for "PartyA" started up and registered in 38.59 sec


   Welcome to the Corda interactive shell.
   Useful commands include 'help' to see what is available, and 'bye' to shut down the node.

   Fri Mar 02 17:34:02 GMT 2018>>>

It usually takes around 60 seconds for the nodes to finish starting up. Each node will display "Welcome to the Corda interactive shell." along with a prompt when ready.

Running the example CorDapp from IntelliJ
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
* Load the project by opening the project folder (Do not use "Import Project" functionality by IntelliJ because it will overwrite the pre-existing configuration) 

* Follow the prompt to ``import Gradle project``

* Select the ``Run Example CorDapp - Kotlin`` run configuration from the drop-down menu at the top right-hand side of
  the IDE

* Click the green arrow to start the nodes:

  .. image:: resources/run-config-drop-down.png
    :width: 400

* Select ``cordapp-example.workflows-kotlin.test`` for the Use classpath of module field, and then click Run

* To stop the nodes, press the red square button at the top right-hand side of the IDE, next to the run configurations

Interacting with the example CorDapp
------------------------------------

Via HTTP
~~~~~~~~
The Spring Boot servers run locally on the following ports:

* PartyA: ``localhost:50005``
* PartyB: ``localhost:50006``
* PartyC: ``localhost:50007``

These ports are defined in ``clients/build.gradle``.

Each Spring Boot server exposes the following endpoints:

* ``/api/example/me``
* ``/api/example/peers``
* ``/api/example/ious``
* ``/api/example/create-iou`` with parameters ``iouValue`` and ``partyName`` which is CN name of a node

There is also a web front-end served from the home web page e.g. ``localhost:50005``.

.. warning:: The content is only available for demonstration purposes and does not implement
   anti-XSS, anti-XSRF or other security techniques. Do not use this code in production.

Creating an IOU via the endpoint
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
An IOU can be created by sending a PUT request to the ``/api/example/create-iou`` endpoint directly, or by using the
the web form served from the home directory.

To create an IOU between PartyA and PartyB, run the following command from the command line:

.. sourcecode:: bash

   curl -X POST -H 'Content-Type: application/x-www-form-urlencoded' 'http://localhost:50005/api/example/create-iou?iouValue=1&partyName=O=PartyB,L=New%20York,C=US'

Note that both PartyA's port number (``50005``) and PartyB are referenced in the PUT request path. This command
instructs PartyA to agree an IOU with PartyB. Once the process is complete, both nodes will have a signed, notarised
copy of the IOU. PartyC will not.

Submitting an IOU via the web front-end
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
To create an IOU between PartyA and PartyB, navigate to the home directory for the node, click the "create IOU" button at the top-left
of the page, and enter the IOU details into the web-form. The IOU must have a positive value. For example:

.. sourcecode:: none

  Counterparty: Select from list
  Value (Int):   5

And click submit. Upon clicking submit, the modal dialogue will close, and the nodes will agree the IOU.

Checking the output
^^^^^^^^^^^^^^^^^^^
Assuming all went well, you can view the newly-created IOU by accessing the vault of PartyA or PartyB:

*Via the HTTP API:*

* PartyA's vault: Navigate to http://localhost:50005/api/example/ious
* PartyB's vault: Navigate to http://localhost:50006/api/example/ious

*Via home page:*

* PartyA: Navigate to http://localhost:50005 and hit the "refresh" button
* PartyB: Navigate to http://localhost:50006 and hit the "refresh" button

The vault and web front-end of PartyC (at ``localhost:50007``) will not display any IOUs. This is because PartyC was
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
    net.corda.core.flows.ContractUpgradeFlow$Authorise
    net.corda.core.flows.ContractUpgradeFlow$Deauthorise
    net.corda.core.flows.ContractUpgradeFlow$Initiate


Creating an IOU via the interactive shell
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
We can create a new IOU using the ``ExampleFlow$Initiator`` flow. For example, from the interactive shell of PartyA,
you can agree an IOU of 50 with PartyB by running
``flow start ExampleFlow$Initiator iouValue: 50, otherParty: "O=PartyB,L=New York,C=US"``.

This will print out the following progress steps:

.. sourcecode:: none

    ✅   Generating transaction based on new IOU.
    ✅   Verifying contract constraints.
    ✅   Signing transaction with our private key.
    ✅   Gathering the counterparty's signature.
        ✅   Collecting signatures from counterparties.
        ✅   Verifying collected signatures.
    ✅   Obtaining notary signature and recording transaction.
        ✅   Requesting signature by notary service
                Requesting signature by Notary service
                Validating response from Notary service
        ✅   Broadcasting transaction to participants
    ✅   Done

Checking the output
^^^^^^^^^^^^^^^^^^^
We can also issue RPC operations to the node via the interactive shell. Type ``run`` to see the full list of available
operations.

You can see the newly-created IOU by running ``run vaultQuery contractStateType: com.example.state.IOUState``.

As before, the interactive shell of PartyC will not display any IOUs.

Via the h2 web console
~~~~~~~~~~~~~~~~~~~~~~
You can connect directly to your node's database to see its stored states, transactions and attachments. To do so,
please follow the instructions in :doc:`node-database`.

Running nodes across machines
-----------------------------
The nodes can be configured to communicate as a network even when distributed across several machines:

* Deploy the nodes as usual:

  * Unix/Mac OSX: ``./gradlew deployNodes``
  * Windows: ``gradlew.bat deployNodes``

* Navigate to the build folder (``workflows-kotlin/build/nodes``)
* For each node, open its ``node.conf`` file and change ``localhost`` in its ``p2pAddress`` to the IP address of the machine
  where the node will be run (e.g. ``p2pAddress="10.18.0.166:10007"``)
* These changes require new node-info files to be distributed amongst the nodes. Use the network bootstrapper tool
  (see :doc:`network-bootstrapper`) to update the files and have them distributed locally:

  ``java -jar network-bootstrapper.jar workflows-kotlin/build/nodes``

* Move the node folders to their individual machines (e.g. using a USB key). It is important that none of the
  nodes - including the notary - end up on more than one machine. Each computer should also have a copy of ``runnodes``
  and ``runnodes.bat``.

  For example, you may end up with the following layout:

  * Machine 1: ``Notary``, ``PartyA``, ``runnodes``, ``runnodes.bat``
  * Machine 2: ``PartyB``, ``PartyC``, ``runnodes``, ``runnodes.bat``

* After starting each node, the nodes will be able to see one another and agree IOUs among themselves

.. warning:: The bootstrapper must be run **after** the ``node.conf`` files have been modified, but **before** the nodes 
   are distributed across machines. Otherwise, the nodes will not be able to communicate.

.. note:: If you are using H2 and wish to use the same ``h2port`` value for two or more nodes, you must only assign them that
   value after the nodes have been moved to their individual machines. The initial bootstrapping process requires access to 
   the nodes' databases and if two nodes share the same H2 port, the process will fail.

Testing your CorDapp
--------------------

Corda provides several frameworks for writing unit and integration tests for CorDapps.

Contract tests
~~~~~~~~~~~~~~
You can run the CorDapp's contract tests by running the ``Run Contract Tests - Kotlin`` run configuration.

Flow tests
~~~~~~~~~~
You can run the CorDapp's flow tests by running the ``Run Flow Tests - Kotlin`` run configuration.

Integration tests
~~~~~~~~~~~~~~~~~
You can run the CorDapp's integration tests by running the ``Run Integration Tests - Kotlin`` run configuration.

.. _tutorial_cordapp_running_tests_intellij:

Running tests in IntelliJ
~~~~~~~~~~~~~~~~~~~~~~~~~

See :ref:`Running tests in IntelliJ<tutorial_cordapp_alternative_test_runners>`

Debugging your CorDapp
----------------------

See :doc:`debugging-a-cordapp`.
