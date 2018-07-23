Corda Network Builder
=====================



TODO:
    * Move to creating the node images from the templates
    * What output is displayed when using Azure?



.. contents::

The Corda Network Builder is a tool for building Corda networks for testing purposes. It leverages Docker and
containers to abstract the complexity of managing a distributed network away from the user.

At the moment, there are integrations for ``docker`` and ``azure container service``.

The Corda Network Builder can be downloaded from https://ci-artifactory.corda.r3cev.com/artifactory/corda-releases/net/corda/corda-network-builder/.

.. _pre-requisites:

Prerequisites
-------------

* **Docker:** docker > 17.12.0-ce
* **Azure:** authenticated az-cli >= 2.0 (see: https://docs.microsoft.com/en-us/cli/azure/install-azure-cli?view=azure-cli-latest)

Building a network
------------------

Creating the node images
~~~~~~~~~~~~~~~~~~~~~~~~

The network builder uses node docker images as the base for all other operations. A node is anything that satisfies
the following layout:

.. sourcecode:: shell

  -
   -- node.conf
   -- corda.jar
   -- cordapps/


An easy way to build a valid set of nodes is to use the ``deployNodes`` utility. In this document, we will be using
the output of running ``deployNodes`` for the ``bank-of-corda-demo`` sample in the main Corda repository:

1. ``./gradlew clean samples:bank-of-corda-demo:deployNodes``
2. ``cd samples/bank-of-corda-demo/build/nodes``

Starting the nodes
~~~~~~~~~~~~~~~~~~

Quickstart Local Docker
^^^^^^^^^^^^^^^^^^^^^^^

1. ``java -jar <network-builder-jar> -d .``
2. ``docker ps``

The following output should be displayed:

.. sourcecode:: shell

    CONTAINER ID        IMAGE                               COMMAND             CREATED             STATUS              PORTS                                                                                                    NAMES
    8b65c104ba7c        node-bigcorporation:corda-network   "/run-corda.sh"     14 seconds ago      Up 13 seconds       0.0.0.0:32788->10003/tcp, 0.0.0.0:32791->10005/tcp, 0.0.0.0:32790->10020/tcp, 0.0.0.0:32789->12222/tcp   bigcorporation0
    3a7af5543c3a        node-bankofcorda:corda-network      "/run-corda.sh"     14 seconds ago      Up 13 seconds       0.0.0.0:32787->10003/tcp, 0.0.0.0:32786->10005/tcp, 0.0.0.0:32785->10020/tcp, 0.0.0.0:32784->12222/tcp   bankofcorda0
    a7b84444feed        node-notaryservice:corda-network    "/run-corda.sh"     23 seconds ago      Up 22 seconds       0.0.0.0:32783->10003/tcp, 0.0.0.0:32782->10005/tcp, 0.0.0.0:32781->10020/tcp, 0.0.0.0:32780->12222/tcp   notaryservice0

Quickstart Remote Azure
^^^^^^^^^^^^^^^^^^^^^^^

1. ``java -jar <network-builder-jar> -b AZURE -d .``

.. note:: The Azure configuration is handled by the az-cli utility. See the :ref:`pre-requisites`.

Interacting with the nodes
~~~~~~~~~~~~~~~~~~~~~~~~~~

To interact with the nodes, it is possible to SSH into the nodes via the port 12222 mapping. For example, to SSH into the ``bankofcorda0`` node:

.. sourcecode:: shell

    ssh bankUser@localhost -p 32784
    Password authentication
    Password:


    Welcome to the Corda interactive shell.
    Useful commands include 'help' to see what is available, and 'bye' to shut down the node.

    >>> run networkMapSnapshot
    [
      {"legalIdentities":[{"name":"O=BankOfCorda, L=London, C=GB"}],"addresses":["bankofcorda0:10020"],"serial":1531841642785,"platformVersion":3},
      {"legalIdentities":[{"name":"O=Notary Service, L=Zurich, C=CH"}],"addresses":["notaryservice0:10020"],"serial":1531841631144,"platformVersion":3},
      {"legalIdentities":[{"name":"O=BigCorporation, L=New York, C=US"}],"addresses":["bigcorporation0:10020"],"serial":1531841642864,"platformVersion":3}
    ]

    >>>

Now that the node images have been built, it is possible to add additional nodes reusing the same images. To add a node
reusing the ``BankOfCorda`` base image:

``java -jar <bootstrapper-jar> --add "BankOfCorda=O=WayTooBigToFailBank,L=London,C=GB"``

And to confirm the node has been started correctly in the previously connected SSH session:

.. sourcecode:: shell

  Tue Jul 17 15:47:14 GMT 2018>>> run networkMapSnapshot
  [
    {"legalIdentities":[{"name":"O=BankOfCorda, L=London, C=GB"}],"addresses":["bankofcorda0:10020"],"serial":1531841642785,"platformVersion":3},
    {"legalIdentities":[{"name":"O=Notary Service, L=Zurich, C=CH"}],"addresses":["notaryservice0:10020"],"serial":1531841631144,"platformVersion":3},
    {"legalIdentities":[{"name":"O=BigCorporation, L=New York, C=US"}],"addresses":["bigcorporation0:10020"],"serial":1531841642864,"platformVersion":3},
    {"legalIdentities":[{"name":"O=WayTooBigToFailBank, L=London, C=GB"}],"addresses":["bankofcorda1:10020"],"serial":1531842358730,"platformVersion":3}
  ]

Graphical User Mode
~~~~~~~~~~~~~~~~~~~

The Corda network builder also provides a GUI for when automated interactions are not required. To launch this, run
``java -jar <network-builder-jar> -g``.