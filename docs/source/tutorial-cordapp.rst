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

If you the 'import gradle project' pop-up does not appear, click the small green speech bubble at the bottom-right of
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
            startNode(X500Name("CN=Controller,O=R3,OU=corda,L=London,C=UK"), setOf(ServiceInfo(ValidatingNotaryService.type)))
            val (nodeA, nodeB, nodeC) = Futures.allAsList(
                    startNode(X500Name("CN=NodeA,O=NodeA,L=London,C=UK"), rpcUsers = listOf(user)),
                    startNode(X500Name("CN=NodeB,O=NodeB,L=New York,C=US"), rpcUsers = listOf(user)),
                    startNode(X500Name("CN=NodeC,O=NodeC,L=Paris,C=FR"), rpcUsers = listOf(user))).getOrThrow()

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
    net.corda.flows.CashExitFlow
    net.corda.flows.CashIssueFlow
    net.corda.flows.CashPaymentFlow
    net.corda.flows.ContractUpgradeFlow

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
          contract:
            legalContractReference: "4DDE2A47C361106CBAEC06CC40FE418A994822A3C8054851FEECD51207BFAF82"
          participants:
          - "CN=NodeB,O=NodeB,L=New York,C=US"
          - "CN=NodeA,O=NodeA,L=London,C=UK"
        notary: "CN=Controller,O=R3,OU=corda,L=London,C=UK,OU=corda.notary.validating"
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
            contract:
              legalContractReference: "4DDE2A47C361106CBAEC06CC40FE418A994822A3C8054851FEECD51207BFAF82"
            participants:
            - "CN=NodeB,O=NodeB,L=New York,C=US"
            - "CN=NodeA,O=NodeA,L=London,C=UK"
          notary: "CN=Controller,O=R3,OU=corda,L=London,C=UK,OU=corda.notary.validating"
          encumbrance: null
        commands:
        - value: {}
          signers:
          - "8Kqd4oWdx4KQAVc3u5qvHZTGJxMtrShFudAzLUTdZUzbF9aPQcCZD5KXViC"
          - "8Kqd4oWdx4KQAVcBx98LBHwXwC3a7hNptQomrg9mq2ScY7t1Qqsyk5dCNAr"
        notary: "CN=Controller,O=R3,OU=corda,L=London,C=UK,OU=corda.notary.validating"
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
You can connect directly to your node's database to see its stored states, transactions and attachments:

* Download the `h2 platform-independent zip <http://www.h2database.com/html/download.html>`_, unzip the zip, and
  navigate in a terminal window to the unzipped folder
* Change directories to the bin folder:

  ``cd h2/bin``

* Run the following command to open the h2 web console in a web browser tab:

  * Unix: ``sh h2.sh``
  * Windows: ``h2.bat``

* Find the node's JDBC connection string. Each node outputs its connection string in the terminal
  window as it starts up. In a terminal window where a node is running, look for the following string:

  ``Database connection URL is              : jdbc:h2:tcp://10.18.0.150:56736/node``

* Paste this string into the JDBC URL field and click Connect.

You will be presented with a web interface that shows the contents of your node's storage and vault, and provides an
interface for you to query them using SQL.

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
            startNode(X500Name("CN=Controller,O=R3,OU=corda,L=London,C=UK"), setOf(ServiceInfo(ValidatingNotaryService.type)))
            val (nodeA, nodeB, nodeC) = Futures.allAsList(
                    startNode(X500Name("CN=NodeA,O=NodeA,L=London,C=UK"), rpcUsers = listOf(user)),
                    startNode(X500Name("CN=NodeB,O=NodeB,L=New York,C=US"), rpcUsers = listOf(user)),
                    startNode(X500Name("CN=NodeC,O=NodeC,L=Paris,C=FR"), rpcUsers = listOf(user))).getOrThrow()

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





********** RUNNING A NODE STUFF **********

The build.gradle file
~~~~~~~~~~~~~~~~~~~~~
It is usually necessary to make a couple of changes to the ``build.gradle`` file. Here will cover the most pertinent bits.

**The buildscript**

The buildscript is always located at the top of the file. It determines which plugins, task classes, and other classes
are available for use in the rest of the build script. It also specifies version numbers for dependencies, among other
things.

**Project dependencies**

If you have any additional external dependencies for your CorDapp then add them below the comment at the end of this
code snippet.package. Use the standard format:

``compile "{groupId}:{artifactId}:{versionNumber}"``

.. sourcecode:: groovy

  dependencies {
      compile "org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version"
      testCompile group: 'junit', name: 'junit', version: '4.11'

      // Corda integration dependencies
      compile "net.corda:client:$corda_release_version"
      compile "net.corda:core:$corda_release_version"
      compile "net.corda:contracts:$corda_release_version"
      compile "net.corda:node:$corda_release_version"
      compile "net.corda:corda:$corda_release_version"
      compile "net.corda:test-utils:$corda_release_version"

      ...

      // Cordapp dependencies
      // Specify your cordapp's dependencies below, including dependent cordapps
  }

For further information about managing dependencies with
`look at the Gradle docs <https://docs.gradle.org/current/userguide/dependency_management.html>`_.

**CordFormation**

This is the local node deployment system for CorDapps. The nodes generated are intended to be used for experimenting,
debugging, and testing node configurations, but are not intended for production or testnet deployment.

In the CorDapp build.gradle file you'll find a ``deployNodes`` task. This is where you configure the nodes you would
like to deploy for testing:

.. sourcecode:: groovy

    task deployNodes(type: net.corda.plugins.Cordform, dependsOn: ['jar']) {
        directory "./build/nodes"
        networkMap "CN=Controller,O=R3,OU=corda,L=London,C=UK"
        node {
            name "CN=Controller,O=R3,OU=corda,L=London,C=UK"
            advertisedServices = ["corda.notary.validating"]
            p2pPort 10002
            rpcPort 10003
            webPort 10004
            cordapps = []
        }
        node {
            name "CN=NodeA,O=NodeA,L=London,C=UK"
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

You can add any number of nodes, along with the services and CorDapps they offer, by editing the templates in
``deployNodes``. The only requirement is that you must specify a node to run as the network map service.

.. note:: CorDapps defined in the cordapp-tutorial project are automatically registered with all nodes defined in
  ``deployNodes``. We expect this to change in the near future.

.. warning:: When adding nodes, make sure that there are no port clashes!

When you are finished editing the ``deployNodes`` task, the changes will be reflected the next time you run
``./gradlew deployNodes``.

Service provider configuration file
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
If you are building a CorDapp from scratch or adding a new CorDapp to the cordapp-tutorial project then you must provide
a reference to your sub-class of ``CordaPluginRegistry`` or ``WebServerPluginRegistry`` (for Wep API) in the provider-configuration file
located in the ``resources/META-INF/services`` directory.

Re-deploying your nodes locally
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
If you need to create any additional nodes you can do it via the ``build.gradle`` file as discussed above in
``the build.gradle file`` and in more detail in the "cordFormation" section.

You may also wish to edit the ``/kotlin-source/build/nodes/<node name>/node.conf`` files for your nodes. For more information on
doing this, see the :doc:`Corda configuration file <corda-configuration-file>` page.

Once you have made some changes to your CorDapp you can redeploy it with the following command:

Unix/Mac OSX: ``./gradlew deployNodes``

Windows: ``gradlew.bat deployNodes``





********** MASTER STUFF **********

**Using a Corda SNAPSHOT build.** Alternatively, if you wish to work from the master branch of the Corda repo which contains
the most up-to-date Corda feature set then you will need to clone the ``corda`` repository and publish the latest master
build (or previously tagged releases) to your local Maven repository. You will then need to ensure that Gradle
grabs the correct dependencies for you from Maven local by changing the ``corda_release_version`` in ``build.gradle``.
This will be covered below in `Using a SNAPSHOT release`_.

Using a SNAPSHOT release
~~~~~~~~~~~~~~~~~~~~~~~~

If you wish to build a CorDapp against the most current version of Corda, follow these instructions.

Firstly navigate to the folder on your machine you wish to clone the Corda repository to. Then use the following command
to clone the Corda repository:

``git clone https://github.com/corda/corda.git``

Now change directories:

``cd corda``

Once you've cloned the ``corda`` repository and are in the repo directory you have the option to remain on the master
branch or checkout a specific branch. Use:

``git branch --all``

to enumerate all the branches. To checkout a specific branch, use:

``git checkout -b [local_branch_name] origin/[remote_branch_name]``

where ``local_branch_name`` is a name of your choice and ``remote_branch_name`` is the name of the remote branch you wish
to checkout.

.. note:: When working with ``master`` you will have access to the most up-to-date feature set. However you will be
potentially sacrificing stability. We will endeavour to keep the ``master`` branch of the ``cordapp-tutorial`` repo in sync
  with the ``master`` branch of ``corda`` repo. A milestone tagged release would be more stable for CorDapp development.

The next step is to publish the Corda JARs to your local Maven repository. By default the Maven local repository can be
found:

* ``~/.m2/repository`` on Unix/Mac OS X
* ``%HOMEPATH%\.m2`` on windows.

Publishing can be done with running the following Gradle task from the root project directory:

Unix/Mac OSX: ``./gradlew install``

Windows: ``gradlew.bat install``

This will install all required modules, along with sources and JavaDocs to your local Maven repository. The ``version``
and ``groupid`` of Corda installed to Maven local is specified in the ``build.gradle`` file in the root of the ``corda``
repository. You shouldn't have to change these values unless you want to publish multiple versions of a SNAPSHOT, e.g.
if you are trying out new features, in this case you can change ``version`` for each SNAPSHOT you publish.

.. note:: **A quick point on corda version numbers used by Gradle.**

  In the ``build.gradle`` file for your CorDapp, you can specify the ``corda_release_version`` to use. It is important
  that when developing your CorDapp that you use the correct version number. For example, when wanting to work from a SNAPSHOT
  release, the release numbers are suffixed with 'SNAPSHOT', e.g. if the latest milestone release is M6 then the
  SNAPSHOT release will be 0.7-SNAPSHOT, and so on. As such, you will set your ``corda_release_version`` to ``'0.7-SNAPSHOT'``
  in the ``build.gradle`` file in your CorDapp. Gradle will automatically grab the SNAPSHOT dependencies from your local
  Maven repository. Alternatively, if working from a milestone release, you will use the version number only, for example
  ``0.6`` or ``0.7``.

  Lastly, as the Corda repository evolves on a daily basis up until the next milestone release, it is worth nothing that
  the substance of two SNAPSHOT releases of the same number may be different. If you are using a SNAPSHOT and need help
  debugging an error then please tell us the **commit** you are working from. This will help us ascertain the issue.

As additional feature branches are merged into Corda you can ``git pull`` the new changes from the ``corda`` repository.
If you are feeling inquisitive, you may also wish to review some of the current feature branches. All new features are
developed on separate branches. To enumerate all the current branches use:

``git branch --all``

and to check out an open feature branch, use:

``git checkout -b [local_branch_name] origin/[branch_name]``

.. note:: Publishing Corda JARs from unmerged feature branches might cause some unexpected behaviour / broken CorDapps.
It would also replace any previously published SNAPSHOTS of the same version.

.. warning:: If you do modify Corda after you have previously published it to Maven local then you must republish your
SNAPSHOT build such that Maven reflects the changes you have made.

Once you have published the Corda JARs to your local Maven repository, you are ready to get started building your
CorDapp using the latest Corda features.

If you are working from a Corda SNAPSHOT release which you have published to Maven local then ensure that
``corda_release_version`` is the same as the version of the Corda core modules you published to Maven local. If not then
change the ``kotlin_version`` property. Also, if you are working from a previous cordapp-tutorial milestone release, then
be sure to ``git checkout`` the correct version of the example CorDapp from the ``cordapp-tutorial`` repo.