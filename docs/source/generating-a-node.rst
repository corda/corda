Creating nodes locally
======================

.. contents::

Node structure
--------------
A Corda node has the following structure:

.. sourcecode:: none

    .
    ├── certificates            // The node's certificates
    ├── corda-webserver.jar     // The built-in node webserver
    ├── corda.jar               // The core Corda libraries
    ├── logs                    // The node logs
    ├── node.conf               // The node's configuration files
    ├── persistence.mv.db       // The node's database
    └── cordapps                // The CorDapps jars installed on the node

The node is configured by editing its ``node.conf`` file. You install CorDapps on the node by dropping the CorDapp JARs
into the ``cordapps`` folder.

In development mode (i.e. when ``devMode = true``, see :doc:`corda-configuration-file` for more information), the ``certificates``
directory is filled with pre-configured keystores if the required keystores do not exist. This ensures that developers
can get the nodes working as quickly as possible. However, these pre-configured keystores are not secure, to learn more see :doc:`permissioning`.

Node naming
-----------
A node's name must be a valid X.500 distinguished name. In order to be compatible with other implementations
(particularly TLS implementations), we constrain the allowed X.500 name attribute types to a subset of the minimum
supported set for X.509 certificates (specified in RFC 3280), plus the locality attribute:

* Organization (O)
* State (ST)
* Locality (L)
* Country (C)
* Organizational-unit (OU)
* Common name (CN)

Note that the serial number is intentionally excluded in order to minimise scope for uncertainty in the distinguished name format.
The distinguished name qualifier has been removed due to technical issues; consideration was given to "Corda" as qualifier,
however the qualifier needs to reflect the compatibility zone, not the technology involved. There may be many Corda namespaces,
but only one R3 namespace on Corda. The ordering of attributes is important.

``State`` should be avoided unless required to differentiate from other ``localities`` with the same or similar names at the
country level. For example, London (GB) would not need a ``state``, but St Ives would (there are two, one in Cornwall, one
in Cambridgeshire). As legal entities in Corda are likely to be located in major cities, this attribute is not expected to be
present in the majority of names, but is an option for the cases which require it.

The name must also obey the following constraints:

* The ``organisation``, ``locality`` and ``country`` attributes are present

    * The ``state``, ``organisational-unit`` and ``common name`` attributes are optional

* The fields of the name have the following maximum character lengths:

    * Common name: 64
    * Organisation: 128
    * Organisation unit: 64
    * Locality: 64
    * State: 64

* The ``country`` attribute is a valid ISO 3166-1 two letter code in upper-case

* All attributes must obey the following constraints:

    * Upper-case first letter
    * Has at least two letters
    * No leading or trailing whitespace
    * Does not include the following characters: ``,`` , ``=`` , ``$`` , ``"`` , ``'`` , ``\``
    * Is in NFKC normalization form
    * Does not contain the null character
    * Only the latin, common and inherited unicode scripts are supported

* The ``organisation`` field of the name also obeys the following constraints:

    * No double-spacing

        * This is to avoid right-to-left issues, debugging issues when we can't pronounce names over the phone, and
          character confusability attacks

External identifiers
^^^^^^^^^^^^^^^^^^^^
Mappings to external identifiers such as Companies House nos., LEI, BIC, etc. should be stored in custom X.509
certificate extensions. These values may change for operational reasons, without the identity they're associated with
necessarily changing, and their inclusion in the distinguished name would cause significant logistical complications.
The OID and format for these extensions will be described in a further specification.

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
            // Includes the corda-finance CorDapp on our node.
            cordapps = ["net.corda:corda-finance:$corda_release_version"]
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
            cordapps = ["net.corda:corda-finance:$corda_release_version"]
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
            cordapps = ["net.corda:corda-finance:$corda_release_version"]
            // Grants user1 the ability to start the MyFlow flow.
            rpcUsers = [[ user: "user1", "password": "test", "permissions": ["StartFlow.net.corda.flows.MyFlow"]]]
        }
    }

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
            configFile = "samples/trader-demo/src/main/resources/none-b.conf"
        }
    }

Specifying a custom webserver
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
By default, any node listing a webport will use the default development webserver, which is not production-ready. You
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

The ```Dockerform``` is a sister task of ```Cordform```. It has nearly the same syntax and produces very
similar results - enhanced by an extra file to enable easy spin up of nodes using ```docker-compose```.
Below you can find the example task from the ```IRS Demo<https://github.com/corda/corda/blob/release-V3.0/samples/irs-demo/cordapp/build.gradle#L111>```
included in the samples directory of main Corda GitHub repository:

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

    task prepareDockerNodes(type: net.corda.plugins.Dockerform, dependsOn: ['jar']) {

        node {
            name "O=Notary Service,L=Zurich,C=CH"
            notary = [validating : true]
            cordapps = ["${project(":finance").group}:finance:$corda_release_version"]
            rpcUsers = rpcUsersList
            useTestClock true
        }
        node {
            name "O=Bank A,L=London,C=GB"
            cordapps = ["${project(":finance").group}:finance:$corda_release_version"]
            rpcUsers = rpcUsersList
            useTestClock true
        }
        node {
            name "O=Bank B,L=New York,C=US"
            cordapps = ["${project(":finance").group}:finance:$corda_release_version"]
            rpcUsers = rpcUsersList
            useTestClock true
        }
        node {
            name "O=Regulator,L=Moscow,C=RU"
            cordapps = ["${project.group}:finance:$corda_release_version"]
            rpcUsers = rpcUsersList
            useTestClock true
        }
    }

There is no need to specify the ports, as every node is a separated container, so no ports conflict will occur.
Running the task will create the same folders structure as described in :ref:`The Cordform task` with an additional
```Dockerfile`` in each node directory, and ```docker-compose.yml``` in ```build/nodes``` directory. Every node
by default exposes port 10003 which is the default one for RPC connections.

.. warning:: Webserver is not supported by this task!

.. warning:: Nodes are run without the local shell enabled!

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

You can now run the nodes by following the instructions in :doc:`Running a node <running-a-node>`.
