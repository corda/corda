Creating nodes locally
======================

.. contents::

Node structure
--------------
Each Corda node has the following structure:

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

Node naming
-----------
A node's name must be a valid X.500 distinguished name. In order to be compatible with other implementations
(particularly TLS implementations), we constrain the allowed X.500 attribute types to a subset of the minimum supported
set for X.509 certificates (specified in RFC 3280), plus the locality attribute:

* Organization (O)
* State (ST)
* Locality (L)
* Country (C)
* Organizational-unit (OU)
* Common name (CN) (only used for service identities)

The name must also obey the following constraints:

* The organisation, locality and country attributes are present

    * The state, organisational-unit and common name attributes are optional

* The fields of the name have the following maximum character lengths:

    * Common name: 64
    * Organisation: 128
    * Organisation unit: 64
    * Locality: 64
    * State: 64

* The country attribute is a valid ISO 3166-1 two letter code in upper-case

* All attributes must obey the following constraints:

    * Upper-case first letter
    * Has at least two letters
    * No leading or trailing whitespace
    * Does not include the following characters: ``,`` , ``=`` , ``$`` , ``"`` , ``'`` , ``\``
    * Is in NFKC normalization form
    * Does not contain the null character
    * Only the latin, common and inherited unicode scripts are supported

* The organisation field of the name also obeys the following constraints:

    * No double-spacing
    * Does not contain the words "node" or "server"

        * This is to avoid right-to-left issues, debugging issues when we can't pronounce names over the phone, and
          character confusability attacks

The Cordform task
-----------------
Corda provides a gradle plugin called ``Cordform`` that allows you to automatically generate and configure a set of
nodes. Here is an example ``Cordform`` task called ``deployNodes`` that creates three nodes, defined in the
`Kotlin CorDapp Template <https://github.com/corda/cordapp-template-kotlin/blob/release-V2/build.gradle#L97>`_:

.. sourcecode:: groovy

    task deployNodes(type: net.corda.plugins.Cordform, dependsOn: ['jar']) {
        directory "./build/nodes"
        networkMap "O=NetworkMapAndNotary,L=London,C=GB"
        node {
            name "O=NetworkMapAndNotary,L=London,C=GB"
            // The notary will offer a validating notary service.
            notary = [validating : true]
            p2pPort  10002
            rpcPort  10003
            // No webport property, so no webserver will be created.
            h2Port   10004
            // Includes the corda-finance CorDapp on our node.
            cordapps = ["net.corda:corda-finance:$corda_release_version"]
        }
        node {
            name "O=PartyA,L=London,C=GB"
            p2pPort  10005
            rpcPort  10006
            webPort  10007
            h2Port   10008
            cordapps = ["net.corda:corda-finance:$corda_release_version"]
            // Grants user1 all RPC permissions.
            rpcUsers = [[ user: "user1", "password": "test", "permissions": ["ALL"]]]
        }
        node {
            name "O=PartyB,L=New York,C=US"
            p2pPort  10009
            rpcPort  10010
            webPort  10011
            h2Port   10012
            cordapps = ["net.corda:corda-finance:$corda_release_version"]
            // Grants user1 the ability to start the MyFlow flow.
            rpcUsers = [[ user: "user1", "password": "test", "permissions": ["StartFlow.net.corda.flows.MyFlow"]]]
        }
    }

Running this task will create three nodes in the ``build/nodes`` folder:

* A ``NetworkMapAndNotary`` node that:

  * Serves as the network map
  * Offers a validating notary service
  * Will not have a webserver (since ``webPort`` is not defined)
  * Is running the ``corda-finance`` CorDapp

* ``PartyA`` and ``PartyB`` nodes that:

  * Are pointing at the ``NetworkMapAndNotary`` as the network map service
  * Are not offering any services
  * Will have a webserver (since ``webPort`` is defined)
  * Are running the ``corda-finance`` CorDapp
  * Have an RPC user, ``user1``, that can be used to log into the node via RPC

Additionally, all three nodes will include any CorDapps defined in the project's source folders, even though these
CorDapps are not listed in each node's ``cordapps`` entry. This means that running the ``deployNodes`` task from the
template CorDapp, for example, would automatically build and add the template CorDapp to each node.

You can extend ``deployNodes`` to generate additional nodes. The only requirement is that you must specify
a single node to run the network map service, by putting its name in the ``networkMap`` field.

.. warning:: When adding nodes, make sure that there are no port clashes!

Running deployNodes
-------------------
To create the nodes defined in our ``deployNodes`` task, run the following command in a terminal window from the root
of the project where the ``deployNodes`` task is defined:

* Linux/macOS: ``./gradlew deployNodes``
* Windows: ``gradlew.bat deployNodes``

This will create the nodes in the ``build/nodes`` folder. There will be a node folder generated for each node defined
in the ``deployNodes`` task, plus a ``runnodes`` shell script (or batch file on Windows) to run all the nodes at once
for testing and development purposes. If you make any changes to your CorDapp source or ``deployNodes`` task, you will
need to re-run the task to see the changes take effect.

You can now run the nodes by following the instructions in :doc:`Running a node <running-a-node>`.
