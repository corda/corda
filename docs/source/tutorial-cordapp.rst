.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

The example CorDapp
===================

.. contents::

The example CorDapp allows nodes to agree IOUs with each other. Nodes will always agree to the creation of a new IOU
unless:

* Its value is less than 1, or greater than 99
* A node tries to issue an IOU to itself

By default, the CorDapp is deployed on 4 test nodes:

* **Controller**, which hosts the network map service and validating notary service
* **NodeA**
* **NodeB**
* **NodeC**

Because data is only propagated on a need-to-know basis, any IOUs agreed between NodeA and NodeB become "shared facts"
between NodeA and NodeB only. NodeC won't be aware of these IOUs.

Downloading the example CorDapp
-------------------------------
If you haven't already, set up your machine by following the :doc:`quickstart guide <getting-set-up>`. Then clone the
example CorDapp from the `cordapp-tutorial repository <https://github.com/corda/cordapp-tutorial>`_ using the following
command:

``git clone https://github.com/corda/cordapp-tutorial``

And change directories to the freshly cloned repo:

``cd cordapp-tutorial``

We want to work off the latest Milestone release. To enumerate all the Milestone releases, run:

``git tag``

And check out the latest (highest-numbered) Milestone release using:

``git checkout [tag_name]``

Where ``tag_name`` is the name of the tag you wish to checkout. Gradle will grab all the required dependencies for you
from our `public Maven repository <https://bintray.com/r3/corda>`_.

.. note:: If you wish to build off the latest, unstable version of the codebase, follow the instructions in
   `Using a SNAPSHOT release`_.

Opening the example CorDapp in IntelliJ
---------------------------------------
Let's open the example CorDapp in the IntelliJ IDE.

**For those completely new to IntelliJ**

Upon opening IntelliJ, a dialogue will appear:

.. image:: resources/intellij-welcome.png
  :width: 400

Click open, then navigate to the folder where you cloned the ``cordapp-tutorial`` and click OK.

Next, IntelliJ will show several pop-up windows, one of which requires our attention:

.. image:: resources/unlinked-gradle-project.png
  :width: 400

Click the 'import gradle project' link. A dialogue will pop-up. Press OK. Gradle will now download all the
project dependencies and perform some indexing. This usually takes a minute or so.

If the 'import gradle project' pop-up does not appear, click the small green speech bubble at the bottom-right of
the IDE, or simply close and re-open IntelliJ again to make it reappear.

**If you already have IntelliJ open**

From the ``File`` menu, navigate to ``Open ...`` and then navigate to the directory where you cloned the
``cordapp-tutorial`` and click OK.

Project structure
-----------------
The example CorDapp has the following directory structure:

.. sourcecode:: none

    .
    ├── LICENCE
    ├── README.md
    ├── TRADEMARK
    ├── build.gradle
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
    ├── gradle.properties
    ├── gradlew
    ├── gradlew.bat
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
    ├── lib
    │   ├── README.txt
    │   └── quasar.jar
    └── settings.gradle

The most important files and directories to note are:

* The **root directory** contains some gradle files, a README and a LICENSE
* **config** contains log4j configs
* **gradle** contains the gradle wrapper, which allows the use of Gradle without installing it yourself and worrying
  about which version is required
* **lib** contains the Quasar jar which is required for runtime instrumentation of classes by Quasar
* **kotlin-source** contains the source code for the example CorDapp written in Kotlin
 * **kotlin-source/src/main/kotlin** contains the source code for the example CorDapp
 * **kotlin-source/src/main/python** contains a python script which accesses nodes via RPC
 * **kotlin-source/src/main/resources** contains the certificate store, some static web content to be served by the
   nodes and the WebServerPluginRegistry file
 * **kotlin-source/src/test/kotlin** contains unit tests for the contracts and flows, and the driver to run the nodes
   via IntelliJ
* **java-source** contains the same source code, but written in java. This is an aid for users who do not want to
  develop in Kotlin, and serves as an example of how CorDapps can be developed in any language targeting the JVM

Running the example CorDapp
---------------------------
There are two ways to run the example CorDapp:

* Via the terminal
* Via IntelliJ

We explain both below.

Terminal: Building the example CorDapp
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Return to your terminal window and make sure you are in the ``cordapp-tutorial`` directory. To build the example
CorDapp use the following command:

* Unix/Mac OSX: ``./gradlew deployNodes``
* Windows: ``gradlew.bat deployNodes``

This will package up our CorDapp source files into a plugin and automatically build four pre-configured nodes that have
our CorDapp plugin installed. These nodes are meant for local testing only.

After the build process has finished, you will see the newly-build nodes in the ``kotlin-source/build/nodes``. There
will be one folder generated for each node you built, plus a ``runnodes`` shell script (or batch file on Windows).

.. note:: CorDapps can be written in any language targeting the JVM. In our case, we've provided the example source in
   both Kotlin (``/kotlin-source/src``) and Java (``/java-source/src``) Since both sets of source files are
   functionally identical, we will refer to the Kotlin build throughout the documentation.

Each node in the ``nodes`` folder has the following structure:

.. sourcecode:: none

    . nodeName
    ├── corda.jar
    ├── node.conf
    └── plugins

``corda.jar` is the Corda runtime, ``plugins`` contains our node's CorDapps, and our node's configuration is provided
in ``node.conf``.

Terminal: Running the example CorDapp
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
To run our nodes, run the following command from the root of the ``cordapp-tutorial`` folder:

* Unix/Mac OSX: ``kotlin-source/build/nodes/runnodes``
* Windows: ``call kotlin-source\build\nodes\runnodes.bat``

On Unix/Mac OSX, do not click/change focus until all eight additional terminal windows have opened, or some nodes may
fail to start.

The ``runnodes`` script creates a terminal tab/window for each node:

.. sourcecode:: none

       ______               __
      / ____/     _________/ /___ _
     / /     __  / ___/ __  / __ `/         It's kind of like a block chain but
    / /___  /_/ / /  / /_/ / /_/ /          cords sounded healthier than chains.
    \____/     /_/   \__,_/\__,_/

    --- Corda Open Source 0.12.1 (da47f1c) -----------------------------------------------

    📚  New! Training now available worldwide, see https://corda.net/corda-training/

    Logs can be found in                    : /Users/joeldudley/Desktop/cordapp-tutorial/kotlin-source/build/nodes/NodeA/logs
    Database connection url is              : jdbc:h2:tcp://10.163.199.132:54763/node
    Listening on address                    : 127.0.0.1:10005
    RPC service listening on address        : localhost:10006
    Loaded plugins                          : com.example.plugin.ExamplePlugin
    Node for "NodeA" started up and registered in 35.0 sec


    Welcome to the Corda interactive shell.
    Useful commands include 'help' to see what is available, and 'bye' to shut down the node.

    Fri Jul 07 10:33:47 BST 2017>>>

The script will also create a webserver terminal tab for each node:

.. sourcecode:: none

    Logs can be found in /Users/joeldudley/Desktop/cordapp-tutorial/kotlin-source/build/nodes/NodeA/logs/web
    Starting as webserver: localhost:10007
    Webserver started up in 42.02 sec

Depending on your machine, it usually takes around 60 seconds for the nodes to finish starting up. If you want to
ensure that all the nodes are running OK, you can query the 'status' end-point located at
``http://localhost:[port]/api/status`` (e.g. ``http://localhost:10007/api/status`` for ``NodeA``).

IntelliJ: Building and running the example CorDapp
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
To run the example CorDapp via IntelliJ you can use the ``Run Example CorDapp - Kotlin`` run configuration. Select it
from the drop-down menu at the top right-hand side of the IDE and press the green arrow to start the nodes:

.. image:: resources/run-config-drop-down.png
  :width: 400

The node driver defined in ``/src/test/kotlin/com/example/Main.kt`` allows you to specify how many nodes you would like
to run and the configuration settings for each node. With the example CorDapp, the driver starts up four nodes
and adds an RPC user for all but the "Controller" node (which serves as the notary and network map service):

.. sourcecode:: kotlin

    fun main(args: Array<String>) {
        // No permissions required as we are not invoking flows.
        val user = User("user1", "test", permissions = setOf())
        driver(isDebug = true) {
            startNode(getX500Name(O="Controller",OU="corda",L="London",C='UK"), setOf(ServiceInfo(ValidatingNotaryService.type)))
            val (nodeA, nodeB, nodeC) = Futures.allAsList(
                    startNode(getX500Name(O="NodeA",L="London",C="UK"), rpcUsers = listOf(user)),
                    startNode(getX500Name(O="NodeB",L="New York",C="US"), rpcUsers = listOf(user)),
                    startNode(getX500Name(O="NodeC",L="Paris",C="FR"), rpcUsers = listOf(user))).getOrThrow()

            startWebserver(nodeA)
            startWebserver(nodeB)
            startWebserver(nodeC)

            waitForAllNodesToFinish()
        }
    }

To stop the nodes, press the red square button at the top right-hand side of the IDE, next to the run configurations.

We'll look later at how the node driver can be useful for `debugging your CorDapp`_.

Interacting with the example CorDapp
------------------------------------

Via HTTP
~~~~~~~~
The CorDapp defines several HTTP API end-points and a web front-end. The end-points allow you to list your existing
IOUs, agree new IOUs, and see who is on the network.

The nodes are running locally on the following ports:

* Controller: ``localhost:10004``
* NodeA:      ``localhost:10007``
* NodeB:      ``localhost:10010``
* NodeC:      ``localhost:10013``

These ports are defined in build.gradle and in each node's node.conf file under ``kotlin-source/build/nodes/NodeX``.

As the nodes start up, they should tell you which port their embedded web server is running on. The available API
endpoints are:

* ``/api/example/me``
* ``/api/example/peers``
* ``/api/example/ious``
* ``/api/example/{COUNTERPARTY}/create-iou``

The web front-end is served from ``/web/example``.

An IOU can be created by sending a PUT request to the ``api/example/create-iou`` end-point directly, or by using the
the web form hosted at ``/web/example``.

.. warning:: The content in ``web/example`` is only available for demonstration purposes and does not implement
   anti-XSS, anti-XSRF or any other security techniques. Do not use this code in production.

**Creating an IOU via the HTTP API:**

To create an IOU between NodeA and NodeB, we would run the following from the command line:

.. sourcecode:: bash

  echo '{"value": "1"}' | cURL -T - -H 'Content-Type: application/json' http://localhost:10007/api/example/NodeB/create-iou

Note that both NodeA's port number (``10007``) and NodeB are referenced in the PUT request path. This command instructs
NodeA to agree an IOU with NodeB. Once the process is complete, both nodes will have a signed, notarised copy of the
IOU. NodeC will not.

**Submitting an IOU via the web front-end:**

Navigate to ``/web/example``, click the "create IOU" button at the top-left of the page, and enter the IOU details into
the web-form. The IOU must have a value of between 1 and 99.

.. sourcecode:: none

  Counter-party: Select from list
  Value (Int):   5

And click submit. Upon clicking submit, the modal dialogue will close, and the nodes will agree the IOU.

**Once an IOU has been submitted:**

Assuming all went well, you should see some activity in NodeA's web-server terminal window:

.. sourcecode:: none

    >> Generating transaction based on new IOU.
    >> Verifying contract constraints.
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

You can view the newly-created IOU by accessing the vault of NodeA or NodeB:

*Via the HTTP API:*

* NodeA's vault: Navigate to http://localhost:10007/api/example/ious
* NodeB's vault: Navigate to http://localhost:10010/api/example/ious

*Via web/example:*

* NodeA: Navigate to http://localhost:10007/web/example and hit the "refresh" button
* NodeA: Navigate to http://localhost:10010/web/example and hit the "refresh" button

If you access the vault or web front-end of NodeC (on ``localhost:10013``), there will be no IOUs. This is because
NodeC was not involved in this transaction.

Via the interactive shell (terminal only)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Once a node has been started via the terminal, it will display an interactive shell:

.. sourcecode:: none

    Welcome to the Corda interactive shell.
    Useful commands include 'help' to see what is available, and 'bye' to shut down the node.

    Fri Jul 07 16:36:29 BST 2017>>>

You can see a list of the flows that your node can run using `flow list`. In our case, this will return the following
list:

.. sourcecode:: none

    com.example.flow.ExampleFlow$Initiator
    net.corda.finance.flows.CashExitFlow
    net.corda.finance.flows.CashIssueFlow
    net.corda.finance.flows.CashPaymentFlow
    net.corda.finance.flows.ContractUpgradeFlow

We can create a new IOU using the ``ExampleFlow$Initiator`` flow. For example, from the interactive shell of NodeA, you
can agree an IOU of 50 with NodeB by running ``flow start Initiator iouValue: 50, otherParty: NodeB``.

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

We can see a list of the states in our node's vault using ``run vaultAndUpdates``:

.. sourcecode:: none

    ---
    first:
    - state:
        data:
          iou:
            value: 50
          sender: "CN=NodeB,O=NodeB,L=New York,C=US"
          recipient: "CN=NodeA,O=NodeA,L=London,C=UK"
          linearId:
            externalId: null
            id: "84628565-2688-45ef-bb06-aae70fcf3be7"
          contract: {}
          participants:
          - "CN=NodeB,O=NodeB,L=New York,C=US"
          - "CN=NodeA,O=NodeA,L=London,C=UK"
        notary: "O=Controller,OU=corda,L=London,C=UK,OU=corda.notary.validating"
        encumbrance: null
      ref:
        txhash: "52A1B18E6ABD535EF36B2075469B01D2EF888034F721C4BECD26F40355C8C9DC"
        index: 0
    second: "(observable)"

We can also see the transactions stored in our node's local storage using ``run verifiedTransactions`` (we've
abbreviated the output below):

.. sourcecode:: none

    first:
    - txBits: "Y29yZGEAAAEOAQEAamF2YS51dGlsLkFycmF5TGlz9AABAAABAAEBAW5ldC5jb3JkYS5jb3JlLmNvbnRyYWN0cy5UcmFuc2FjdGlvblN0YXTlA1RyYW5zYWN0aW9uU3RhdGUuZGF04VRyYW5zYWN0aW9uU3RhdGUuZW5jdW1icmFuY+VUcmFuc2FjdGlvblN0YXRlLm5vdGFy+WkBAmNvbS5leGFtcGxlLnN0YXRlLklPVVN0YXTlBElPVVN0YXRlLmlv9UlPVVN0YXRlLmxpbmVhcknkSU9VU3RhdGUucmVjaXBpZW70SU9VU3RhdGUuc2VuZGXyDQEBSU9VLnZhbHXlAWQCAQA0ADIBAlVuaXF1ZUlkZW50aWZpZXIuZXh0ZXJuYWxJ5FVuaXF1ZUlkZW50aWZpZXIuaeQBgDAvAC0BAlVVSUQubGVhc3RTaWdCaXTzVVVJRC5tb3N0U2lnQml08wmxkIaDnsaq+YkNDAsACaHovZfbpr2d9wMCAQACAQBIAEYBAkFic3RyYWN0UGFydHkub3duaW5nS2X5UGFydHkubmFt5SIuIOnhdbFQY3EL/LQD90w6y+kCfj4x8UWXaqKtW68GBPlnREMAQTkwPjEOMAwGA1UEAwwFTm9kZUExDjAMBgNVBAoMBU5vZGVBMQ8wDQYDVQQHDAZMb25kb24xCzAJBgNVBAYTAlVLAgEAJgAkASIuIHI7goTSxPMdaRgJgGJVLQbFEzE++qJeYbEbQjrYxzuVRkUAQzkwQDEOMAwGA1UEAwwFTm9kZUIxDjAMBgNVBAoMBU5vZGVCMREwDwYDVQQHDAhOZXcgWW9yazELMAkGA1UEBhMCVVMCAQABAAABAAAkASIuIMqulslvpZ0PaM6fdyFZm+JsDGkuJ7xWnL3zB6PqpzANdwB1OTByMRMwEQYDVQQDDApDb250cm9sbGVyMQswCQYDVQQKDAJSMzEOMAwGA1UECwwFY29yZGExDzANBgNVBAcMBkxvbmRvbjELMAkGA1UEBhMCVUsxIDAeBgNVBAsMF2NvcmRhLm5vdGFyeS52YWxpZGF0aW5nAQAAAQABAQNuZXQuY29yZGEuY29yZS5jb250cmFjdHMuQ29tbWFu5AJDb21tYW5kLnNpZ25lcvNDb21tYW5kLnZhbHXlRwEAAi4gcjuChNLE8x1pGAmAYlUtBsUTMT76ol5hsRtCOtjHO5UuIOnhdbFQY3EL/LQD90w6y+kCfj4x8UWXaqKtW68GBPlnADMBBGNvbS5leGFtcGxlLmNvbnRyYWN0LklPVUNvbnRyYWN0JENvbW1hbmRzJENyZWF05QAAAQVuZXQuY29yZGEuY29yZS5pZGVudGl0eS5QYXJ0+SIuIMqulslvpZ0PaM6fdyFZm+JsDGkuJ7xWnL3zB6PqpzANAHU5MHIxEzARBgNVBAMMCkNvbnRyb2xsZXIxCzAJBgNVBAoMAlIzMQ4wDAYDVQQLDAVjb3JkYTEPMA0GA1UEBwwGTG9uZG9uMQswCQYDVQQGEwJVSzEgMB4GA1UECwwXY29yZGEubm90YXJ5LnZhbGlkYXRpbmcAAQACLiByO4KE0sTzHWkYCYBiVS0GxRMxPvqiXmGxG0I62Mc7lS4g6eF1sVBjcQv8tAP3TDrL6QJ+PjHxRZdqoq1brwYE+WcBBm5ldC5jb3JkYS5jb3JlLmNvbnRyYWN0cy5UcmFuc2FjdGlvblR5cGUkR2VuZXJh7AA="
      sigs:
      - "cRgJlF8cUMMooyaV2OIKmR4/+3XmMsEPsbdlhU5YqngRhqgy9+tLzylh7kvWOhYZ4hjjOfrazLoZ6uOx6BAMCQ=="
      - "iGLRDIbhlwguMz6yayX5p6vfQcAsp8haZc1cLGm7DPDIgq6hFyx2fzoI03DjXAV/mBT1upcUjM9UZ4gbRMedAw=="
      id: "52A1B18E6ABD535EF36B2075469B01D2EF888034F721C4BECD26F40355C8C9DC"
      tx:
        inputs: []
        attachments: []
        outputs:
        - data:
            iou:
              value: 50
            sender: "CN=NodeB,O=NodeB,L=New York,C=US"
            recipient: "CN=NodeA,O=NodeA,L=London,C=UK"
            linearId:
              externalId: null
              id: "84628565-2688-45ef-bb06-aae70fcf3be7"
            contract: {}
            participants:
            - "CN=NodeB,O=NodeB,L=New York,C=US"
            - "CN=NodeA,O=NodeA,L=London,C=UK"
          notary: "O=Controller,OU=corda,L=London,C=UK,OU=corda.notary.validating"
          encumbrance: null
        commands:
        - value: {}
          signers:
          - "8Kqd4oWdx4KQAVc3u5qvHZTGJxMtrShFudAzLUTdZUzbF9aPQcCZD5KXViC"
          - "8Kqd4oWdx4KQAVcBx98LBHwXwC3a7hNptQomrg9mq2ScY7t1Qqsyk5dCNAr"
        notary: "O=Controller,OU=corda,L=London,C=UK,OU=corda.notary.validating"
        type: {}
        timeWindow: null
        mustSign:
        - "8Kqd4oWdx4KQAVc3u5qvHZTGJxMtrShFudAzLUTdZUzbF9aPQcCZD5KXViC"
        - "8Kqd4oWdx4KQAVcBx98LBHwXwC3a7hNptQomrg9mq2ScY7t1Qqsyk5dCNAr"
        id: "52A1B18E6ABD535EF36B2075469B01D2EF888034F721C4BECD26F40355C8C9DC"
        merkleTree: ...
        availableComponents: ...
        availableComponentHashes: ...
        serialized: "Y29yZGEAAAEOAQEAamF2YS51dGlsLkFycmF5TGlz9AABAAABAAEBAW5ldC5jb3JkYS5jb3JlLmNvbnRyYWN0cy5UcmFuc2FjdGlvblN0YXTlA1RyYW5zYWN0aW9uU3RhdGUuZGF04VRyYW5zYWN0aW9uU3RhdGUuZW5jdW1icmFuY+VUcmFuc2FjdGlvblN0YXRlLm5vdGFy+WkBAmNvbS5leGFtcGxlLnN0YXRlLklPVVN0YXTlBElPVVN0YXRlLmlv9UlPVVN0YXRlLmxpbmVhcknkSU9VU3RhdGUucmVjaXBpZW70SU9VU3RhdGUuc2VuZGXyDQEBSU9VLnZhbHXlAWQCAQA0ADIBAlVuaXF1ZUlkZW50aWZpZXIuZXh0ZXJuYWxJ5FVuaXF1ZUlkZW50aWZpZXIuaeQBgDAvAC0BAlVVSUQubGVhc3RTaWdCaXTzVVVJRC5tb3N0U2lnQml08wmxkIaDnsaq+YkNDAsACaHovZfbpr2d9wMCAQACAQBIAEYBAkFic3RyYWN0UGFydHkub3duaW5nS2X5UGFydHkubmFt5SIuIOnhdbFQY3EL/LQD90w6y+kCfj4x8UWXaqKtW68GBPlnREMAQTkwPjEOMAwGA1UEAwwFTm9kZUExDjAMBgNVBAoMBU5vZGVBMQ8wDQYDVQQHDAZMb25kb24xCzAJBgNVBAYTAlVLAgEAJgAkASIuIHI7goTSxPMdaRgJgGJVLQbFEzE++qJeYbEbQjrYxzuVRkUAQzkwQDEOMAwGA1UEAwwFTm9kZUIxDjAMBgNVBAoMBU5vZGVCMREwDwYDVQQHDAhOZXcgWW9yazELMAkGA1UEBhMCVVMCAQABAAABAAAkASIuIMqulslvpZ0PaM6fdyFZm+JsDGkuJ7xWnL3zB6PqpzANdwB1OTByMRMwEQYDVQQDDApDb250cm9sbGVyMQswCQYDVQQKDAJSMzEOMAwGA1UECwwFY29yZGExDzANBgNVBAcMBkxvbmRvbjELMAkGA1UEBhMCVUsxIDAeBgNVBAsMF2NvcmRhLm5vdGFyeS52YWxpZGF0aW5nAQAAAQABAQNuZXQuY29yZGEuY29yZS5jb250cmFjdHMuQ29tbWFu5AJDb21tYW5kLnNpZ25lcvNDb21tYW5kLnZhbHXlRwEAAi4gcjuChNLE8x1pGAmAYlUtBsUTMT76ol5hsRtCOtjHO5UuIOnhdbFQY3EL/LQD90w6y+kCfj4x8UWXaqKtW68GBPlnADMBBGNvbS5leGFtcGxlLmNvbnRyYWN0LklPVUNvbnRyYWN0JENvbW1hbmRzJENyZWF05QAAAQVuZXQuY29yZGEuY29yZS5pZGVudGl0eS5QYXJ0+SIuIMqulslvpZ0PaM6fdyFZm+JsDGkuJ7xWnL3zB6PqpzANAHU5MHIxEzARBgNVBAMMCkNvbnRyb2xsZXIxCzAJBgNVBAoMAlIzMQ4wDAYDVQQLDAVjb3JkYTEPMA0GA1UEBwwGTG9uZG9uMQswCQYDVQQGEwJVSzEgMB4GA1UECwwXY29yZGEubm90YXJ5LnZhbGlkYXRpbmcAAQACLiByO4KE0sTzHWkYCYBiVS0GxRMxPvqiXmGxG0I62Mc7lS4g6eF1sVBjcQv8tAP3TDrL6QJ+PjHxRZdqoq1brwYE+WcBBm5ldC5jb3JkYS5jb3JlLmNvbnRyYWN0cy5UcmFuc2FjdGlvblR5cGUkR2VuZXJh7AA="
    second: "(observable)"

The same states and transactions will be present on NodeB, who was NodeA's counterparty in the creation of the IOU.
However, the vault and local storage of NodeC will remain empty, since NodeC was not involved in the transaction.

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

Select the 'Run Example RPC Client' run configuration which, by default, connects to NodeA (Artemis port 10007). Click the
Green Arrow to run the client. You can edit the run configuration to connect on a different port.

*Running the client via the command line:*

Run the following gradle task:

``./gradlew runExampleClientRPC localhost:10007``

You can close the application using ``ctrl+C``.

For more information on the client RPC interface and how to build an RPC client application, see:

* :doc:`Client RPC documentation <clientrpc>`
* :doc:`Client RPC tutorial <tutorial-clientrpc-api>`

Running Nodes Across Machines
-----------------------------
The nodes can also be configured to communicate across the network when residing on different machines.

After deploying the nodes, navigate to the build folder (``kotlin-source/build/nodes`` or ``java-source/build/nodes``)
and move some of the individual node folders to separate machines (e.g. using a USB key). It is important that none of
the nodes - including the controller node - end up on more than one machine. Each computer should also have a copy of
``runnodes`` and ``runnodes.bat``.

For example, you may end up with the following layout:

* Machine 1: ``controller``, ``nodea``, ``runnodes``, ``runnodes.bat``
* Machine 2: ``nodeb``, ``nodec``, ``runnodes``, ``runnodes.bat``

You must now edit the configuration file for each node, including the controller. Open each node's config file,
and make the following changes:

* Change the Artemis messaging address to the machine's IP address (e.g. ``p2pAddress="10.18.0.166:10006"``)
* Change the network map service's address to the IP address of the machine where the controller node is running
  (e.g. ``networkMapService { address="10.18.0.166:10002" ...``). The controller will not have the
  ``networkMapService`` config

After starting each node, they should be able to see one another and agree IOUs among themselves.

Debugging your CorDapp
----------------------
Debugging is done via IntelliJ as follows:

1. Edit the node driver code in ``Main.kt`` to reflect the number of nodes you wish to start, along with any other
   configuration options. For example, the code below starts 4 nodes, with one being the network map service and notary.
   It also sets up RPC credentials for the three non-notary nodes

.. sourcecode:: kotlin

    fun main(args: Array<String>) {
        // No permissions required as we are not invoking flows.
        val user = User("user1", "test", permissions = setOf())
        driver(isDebug = true) {
            startNode(getX500Name(O="Controller",OU="corda",L="London",C="UK"), setOf(ServiceInfo(ValidatingNotaryService.type)))
            val (nodeA, nodeB, nodeC) = Futures.allAsList(
                    startNode(getX500Name(O="NodeA",L=London,C=UK"), rpcUsers = listOf(user)),
                    startNode(getX500Name(O="NodeB",L=New York,C=US"), rpcUsers = listOf(user)),
                    startNode(getX500Name(O="NodeC",L=Paris,C=FR"), rpcUsers = listOf(user))).getOrThrow()

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

    [INFO ] 15:27:59.533 [main] Node.logStartupInfo - Working Directory: /Users/joeldudley/cordapp-tutorial/build/20170707142746/NodeA
    [INFO ] 15:27:59.533 [main] Node.logStartupInfo - Debug port: dt_socket:5007

4. Edit the “Debug CorDapp” run configuration with the port of the node you wish to connect to
5. Run the “Debug CorDapp” run configuration
6. Set your breakpoints and start using your node. When your node hits a breakpoint, execution will pause
