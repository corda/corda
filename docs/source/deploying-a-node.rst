Deploying a node
================

Using Gradle to build nodes
---------------------------
Nodes are usually built using a Gradle task. The canonical Gradle file for building nodes is the one used by the
CorDapp template. Both a `Java version <https://github.com/corda/cordapp-template-java/blob/master/build.gradle>`_ and
a `Kotlin version <https://github.com/corda/cordapp-template-kotlin/blob/master/build.gradle>`_ are available.

Cordform is the local node deployment system for CorDapps. The nodes generated are intended for experimenting,
debugging, and testing node configurations, but not for production or testnet deployment.

Here is an example Gradle task called ``deployNodes`` that uses the Cordform plugin to deploy three nodes, plus a
notary node:

.. sourcecode:: groovy

    task deployNodes(type: net.corda.plugins.Cordform, dependsOn: ['jar']) {
        directory "./build/nodes"
        node {
            name "O=Controller,OU=corda,L=London,C=UK"
            notary = [validating : true]
            p2pPort 10002
            rpcPort 10003
            webPort 10004
            cordapps = []
        }
        node {
            name "CN=NodeA,O=NodeA,L=London,C=UK"
            p2pPort 10005
            rpcPort 10006
            webPort 10007
            cordapps = []
            rpcUsers = [[ user: "user1", "password": "test", "permissions": []]]
        }
        node {
            name "CN=NodeB,O=NodeB,L=New York,C=US"
            p2pPort 10008
            rpcPort 10009
            webPort 10010
            cordapps = []
            rpcUsers = [[ user: "user1", "password": "test", "permissions": []]]
        }
        node {
            name "CN=NodeC,O=NodeC,L=Paris,C=FR"
            p2pPort 10011
            rpcPort 10012
            webPort 10013
            cordapps = []
            rpcUsers = [[ user: "user1", "password": "test", "permissions": []]]
        }
    }

You can extend ``deployNodes`` to generate any number of nodes you like.

.. warning:: When adding nodes, make sure that there are no port clashes!

If your CorDapp is written in Java, you should also add the following Gradle snippet so that you can pass named arguments to your flows via the Corda shell:

.. sourcecode:: groovy

    tasks.withType(JavaCompile) {
        options.compilerArgs << "-parameters"
    }

Any CorDapps defined in the project's source folders are also automatically registered with all the nodes defined in
``deployNodes``, even if the CorDapps are not listed in each node's ``cordapps`` entry.

Deploying your nodes
--------------------
You deploy a set of nodes by running your ``build.gradle`` file's Cordform task. For example, if we were using the
standard ``deployNodes`` task defined above, we'd create our nodes by running the following commands in a terminal
window from the root of the project:

* Unix/Mac OSX: ``./gradlew deployNodes``
* Windows: ``gradlew.bat deployNodes``

After the build process has finished, you will find the newly-built nodes under ``kotlin-source/build/nodes``. There
will be one folder generated for each node you built, plus a ``runnodes`` shell script (or batch file on Windows) to
run all the nodes at once. Each node in the ``nodes`` folder has the following structure:

.. sourcecode:: none

    . nodeName
    ├── corda.jar               // The Corda runtime
    ├── node.conf               // The node's configuration
    ├── cordapps                // Any installed CorDapps
    └── additional-node-infos   // Directory containing all the addresses and certificates of the other nodes.

.. note:: During the build process each node generates a NodeInfo file which is written in its own root directory,
the plug-in proceeds and copies each node NodeInfo to every other node ``additional-node-infos`` directory.
The NodeInfo file contains a node hostname and port, legal name and security certificate.

.. note:: Outside of development environments, do not store your node directories in the build folder.

If you make any changes to your ``deployNodes`` task, you will need to re-run the task to see the changes take effect.
