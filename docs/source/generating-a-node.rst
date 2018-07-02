Creating nodes locally
======================

.. contents::

Handcrafting a node
-------------------
A node can be created manually by creating a folder that contains the following items:

* The Corda Enterprise JAR

    * The binary ``corda-VERSION_NUMBER.jar`` provided to your organisation.

* A node configuration file entitled ``node.conf``, configured as per :doc:`corda-configuration-file`

* A folder entitled ``cordapps`` containing any CorDapp JARs you want the node to load

* **Optional:** A webserver JAR entitled ``corda-webserver-VERSION_NUMBER.jar`` that will connect to the node via RPC

    * The (deprecated) default webserver is available to you for testing and should not be used in a production environment.
    * A Spring Boot alternative can be found here: https://github.com/corda/spring-webserver

The remaining files and folders described in :doc:`node-structure` will be generated at runtime.

The Cordform task
-----------------
Corda provides a gradle plugin called ``Cordform`` that allows you to automatically generate and configure a set of
nodes for testing and demos. Here is an example ``Cordform`` task called ``deployNodes`` that creates three nodes, defined
in the `Kotlin CorDapp Template <https://github.com/corda/cordapp-template-kotlin/blob/release-V3/build.gradle#L100>`_:

.. sourcecode:: groovy

    task deployNodes(type: net.corda.plugins.Cordform, dependsOn: ['jar']) {
        directory "./build/nodes"
        node {
            name "O=Notary,L=London,C=GB"
            // The notary will offer a validating notary service.
            notary = [validating : true]
            p2pPort  10002
            rpcSettings {
                port 10003
                adminPort 10023
            }
            // No webport property, so no webserver will be created.
            h2Port   10004
            // Starts an internal SSH server providing a management shell on the node.
            sshdPort 2223
            // Includes the corda-finance CorDapp on our node.
            cordapps = ["$corda_release_distribution:corda-finance:$corda_release_version"]
            // Specify a JVM argument to be used when running the node (in this case, extra heap size).
            extraConfig = [
                jvmArgs : [ "-Xmx1g"]
            ]
        }
        node {
            name "O=PartyA,L=London,C=GB"
            p2pPort  10005
            rpcSettings {
                port 10006
                adminPort 10026
            }
            webPort  10007
            h2Port   10008
            cordapps = ["$corda_release_distribution:corda-finance:$corda_release_version"]
            // Grants user1 all RPC permissions.
            rpcUsers = [[ user: "user1", "password": "test", "permissions": ["ALL"]]]
        }
        node {
            name "O=PartyB,L=New York,C=US"
            p2pPort  10009
            rpcSettings {
                port 10010
                adminPort 10030
            }
            webPort  10011
            h2Port   10012
            cordapps = ["$corda_release_distribution:corda-finance:$corda_release_version"]
            // Grants user1 the ability to start the MyFlow flow.
            rpcUsers = [[ user: "user1", "password": "test", "permissions": ["StartFlow.net.corda.flows.MyFlow"]]]
        }
    }

Ensure Corda Enterprise binaries are available on your machine as described in :ref:`Getting Set Up <resolve-corda-enterprise-binaries>`.

Running this task will create three nodes in the ``build/nodes`` folder:

* A ``Notary`` node that:

  * Offers a validating notary service
  * Will not have a webserver (since ``webPort`` is not defined)
  * Is running the ``corda-finance`` CorDapp

* ``PartyA`` and ``PartyB`` nodes that:

  * Are not offering any services
  * Will have a webserver (since ``webPort`` is defined)
  * Are running the ``corda-finance`` CorDapp
  * Have an RPC user, ``user1``, that can be used to log into the node via RPC

Additionally, all three nodes will include any CorDapps defined in the project's source folders, even though these
CorDapps are not listed in each node's ``cordapps`` entry. This means that running the ``deployNodes`` task from the
template CorDapp, for example, would automatically build and add the template CorDapp to each node.

You can extend ``deployNodes`` to generate additional nodes.

.. warning:: When adding nodes, make sure that there are no port clashes!

To extend node configuration beyond the properties defined in the ``deployNodes`` task use the ``configFile`` property with the path (relative or absolute) set to an additional configuration file.
This file should follow the standard :doc:`corda-configuration-file` format, as per node.conf. The properties from this file will be appended to the generated node configuration. Note, if you add a property already created by the 'deployNodes' task, both properties will be present in the file.
The path to the file can also be added while running the Gradle task via the ``-PconfigFile`` command line option. However, the same file will be applied to all nodes.
Following the previous example ``PartyB`` node will have additional configuration options added from a file ``none-b.conf``:

.. sourcecode:: groovy

    task deployNodes(type: net.corda.plugins.Cordform, dependsOn: ['jar']) {
        [...]
        node {
            name "O=PartyB,L=New York,C=US"
            [...]
            // Grants user1 the ability to start the MyFlow flow.
            rpcUsers = [[ user: "user1", "password": "test", "permissions": ["StartFlow.net.corda.flows.MyFlow"]]]
            configFile = "samples/trader-demo/src/main/resources/node-b.conf"
        }
    }

Cordform parameter `drivers` of the `node` entry lists paths of the files to be copied to the `./drivers` subdirectory of the node.
To copy the same file to all nodes `ext.drivers` can be defined in the top level and reused for each node via `drivers=ext.drivers``.

.. sourcecode:: groovy

    task deployNodes(type: net.corda.plugins.Cordform, dependsOn: ['jar']) {
        ext.drivers = ['lib/my_common_jar.jar']
        [...]
        node {
            name "O=PartyB,L=New York,C=US"
            [...]
            drivers = ext.drivers + ['lib/my_specific_jar.jar']
        }
    }


Specifying a custom webserver
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
By default, any node listing a web port will use the default development webserver, which is not production-ready. You
can use your own webserver JAR instead by using the ``webserverJar`` argument in a ``Cordform`` ``node`` configuration
block:

.. sourcecode:: groovy

    node {
        name "O=PartyA,L=New York,C=US"
        webPort 10005
        webserverJar "lib/my_webserver.jar"
    }

The webserver JAR will be copied into the node's ``build`` folder with the name ``corda-webserver.jar``.

.. warning:: This is an experimental feature. There is currently no support for reading the webserver's port from the
   node's ``node.conf`` file.

The Dockerform task
-------------------

The ``Dockerform`` is a sister task of ``Cordform`` that provides an extra file allowing you to easily spin up 
nodes using ``docker-compose``. It supports the following configuration options for each node:

* ``name``
* ``notary``
* ``cordapps``
* ``rpcUsers``
* ``useTestClock``

There is no need to specify the nodes' ports, as every node has a separate container, so no ports conflict will occur. 
Every node will expose port ``10003`` for RPC connections.

The nodes' webservers will not be started. Instead, you should interact with each node via its shell over SSH 
(see the :doc:`node configuration options <corda-configuration-file>`). You have to enable the shell by adding the 
following line to each node's ``node.conf`` file:

    ``sshd { port = 2222 }``

Where ``2222`` is the port you want to open to SSH into the shell.

Below you can find the example task from the `IRS Demo <https://github.com/corda/corda/blob/release-V3.0/samples/irs-demo/cordapp/build.gradle#L111>`_ included in the samples directory of main Corda GitHub repository:

.. sourcecode:: groovy

    def rpcUsersList = [
        ['username' : "user",
         'password' : "password",
         'permissions' : [
                 "StartFlow.net.corda.irs.flows.AutoOfferFlow\$Requester",
                 "StartFlow.net.corda.irs.flows.UpdateBusinessDayFlow\$Broadcast",
                 "StartFlow.net.corda.irs.api.NodeInterestRates\$UploadFixesFlow",
                 "InvokeRpc.vaultQueryBy",
                 "InvokeRpc.networkMapSnapshot",
                 "InvokeRpc.currentNodeTime",
                 "InvokeRpc.wellKnownPartyFromX500Name"
         ]]
    ]

    // (...)

    task deployNodes(type: net.corda.plugins.Dockerform, dependsOn: ['jar']) {

        node {
            name "O=Notary Service,L=Zurich,C=CH"
            notary = [validating : true]
            cordapps = ["$corda_release_group:corda-finance:$corda_release_version"]
            rpcUsers = rpcUsersList
            useTestClock true
        }
        node {
            name "O=Bank A,L=London,C=GB"
            cordapps = ["$corda_release_group:corda-finance:$corda_release_version"]
            rpcUsers = rpcUsersList
            useTestClock true
        }
        node {
            name "O=Bank B,L=New York,C=US"
            cordapps = ["$corda_release_group:corda-finance:$corda_release_version"]
            rpcUsers = rpcUsersList
            useTestClock true
        }
        node {
            name "O=Regulator,L=Moscow,C=RU"
            cordapps = ["$corda_release_group:corda-finance:$corda_release_version"]
            rpcUsers = rpcUsersList
            useTestClock true
        }
    }

Running the Cordform/Dockerform tasks
-------------------------------------
To create the nodes defined in our ``deployNodes`` task, run the following command in a terminal window from the root
of the project where the ``deployNodes`` task is defined:

* Linux/macOS: ``./gradlew deployNodes``
* Windows: ``gradlew.bat deployNodes``

This will create the nodes in the ``build/nodes`` folder. There will be a node folder generated for each node defined
in the ``deployNodes`` task, plus a ``runnodes`` shell script (or batch file on Windows) to run all the nodes at once
for testing and development purposes. If you make any changes to your CorDapp source or ``deployNodes`` task, you will
need to re-run the task to see the changes take effect.

If the task is a ``Dockerform`` task, running the task will also create an additional ``Dockerfile`` in each node
directory, and a ``docker-compose.yml`` file in the ``build/nodes`` directory.

You can now run the nodes by following the instructions in :doc:`Running a node <running-a-node>`.
