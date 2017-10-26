Deploying a node
================

Node structure
--------------
Each Corda node has the following structure:

.. sourcecode:: none

    .
    ├── certificates            // The node's doorman certificates
    ├── corda-webserver.jar     // The built-in node webserver
    ├── corda.jar               // The core Corda libraries
    ├── logs                    // The node logs
    ├── node.conf               // The node's configuration files
    ├── persistence.mv.db       // The node's database
    └── plugins                 // The CorDapps jars installed on the node

The node is configured by editing its ``node.conf`` file. You install CorDapps on the node by dropping the CorDapp JARs
into the ``plugins`` folder.

The deployNodes task
--------------------
The CorDapp template defines a ``deployNodes`` task that allows you to automatically generate and configure a set of
nodes:

.. sourcecode:: groovy

    task deployNodes(type: net.corda.plugins.Cordform, dependsOn: ['jar']) {
        directory "./build/nodes"
        networkMap "O=Controller,L=London,C=GB"
        node {
            name "O=Controller,L=London,C=GB"
            advertisedServices = ["corda.notary.validating"]
            p2pPort 10002
            rpcPort 10003
            cordapps = ["net.corda:corda-finance:$corda_release_version"]
        }
        node {
            name "O=PartyA,L=London,C=GB"
            advertisedServices = []
            p2pPort 10005
            rpcPort 10006
            webPort 10007
            cordapps = ["net.corda:corda-finance:$corda_release_version"]
            rpcUsers = [[ user: "user1", "password": "test", "permissions": []]]
        }
        node {
            name "O=PartyB,L=New York,C=US"
            advertisedServices = []
            p2pPort 10008
            rpcPort 10009
            webPort 10010
            cordapps = ["net.corda:corda-finance:$corda_release_version"]
            rpcUsers = [[ user: "user1", "password": "test", "permissions": []]]
        }
    }

Running this task will create three nodes in the ``build/nodes`` folder:

* A ``Controller`` node that:

  * Serves as the network map
  * Offers a validating notary service
  * Will not have a webserver (since ``webPort`` is not defined)
  * Is running the ``corda-finance`` CorDapp

* ``PartyA`` and ``PartyB`` nodes that:

  * Are pointing at the ``Controller`` as the network map service
  * Are not offering any services
  * Will have a webserver (since ``webPort`` is defined)
  * Are running the ``corda-finance`` CorDapp
  * Have an RPC user, ``user1``, that can be used to log into the node via RPC

Additionally, all three nodes will include any CorDapps defined in the project's source folders, even though these
CorDapps are not listed in each node's ``cordapps`` entry. This means that running the ``deployNodes`` task from the
template CorDapp, for example, would automatically build and add the template CorDapp to each node.

You can extend ``deployNodes`` to generate additional nodes. The only requirement is that you must specify
a single node to run the network map service, by putting their name in the ``networkMap`` field.

.. warning:: When adding nodes, make sure that there are no port clashes!

Running deployNodes
-------------------
To create the nodes defined in our ``deployNodes`` task, we'd run the following command in a terminal window from the
root of the project:

* Unix/Mac OSX: ``./gradlew deployNodes``
* Windows: ``gradlew.bat deployNodes``

This will create the nodes in the ``build/nodes`` folder.

.. warning:: Outside of development environments, do not store your node directories in the build folder.

There will be a node folder generated for each node you defined, plus a ``runnodes`` shell script (or batch file on
Windows) to run all the nodes at once. If you make any changes to your ``deployNodes`` task, you will need to re-run
the task to see the changes take effect.

You can now run the nodes by following the instructions in :doc:`Running a node <running-a-node>`.