Corda Network Builder
=====================

.. contents::

The Corda Network Builder is a tool for building Corda networks for testing purposes. It leverages Docker and
containers to abstract the complexity of managing a distributed network away from the user.

At the moment, there are integrations for ``docker`` and ``azure container service``.

The Corda Network Builder can be downloaded from
https://ci-artifactory.corda.r3cev.com/artifactory/corda-releases/net/corda/corda-network-builder/X.Y-corda/corda-network-builder-X.Y-corda-executable.jar,
where ``X`` is the major Corda version and ``Y`` is the minor Corda version.

.. _pre-requisites:

Prerequisites
-------------

* **Docker:** docker > 17.12.0-ce
* **Azure:** authenticated az-cli >= 2.0 (see: https://docs.microsoft.com/en-us/cli/azure/install-azure-cli?view=azure-cli-latest)

Building a network
------------------

Creating the base nodes
~~~~~~~~~~~~~~~~~~~~~~~

The network builder uses a set of nodes as the base for all other operations. A node is anything that satisfies
the following layout:

.. sourcecode:: shell

  -
   -- node.conf
   -- corda.jar
   -- cordapps/


An easy way to build a valid set of nodes is by running ``deployNodes``. In this document, we will be using
the output of running ``deployNodes`` for the `Example CorDapp <https://github.com/corda/cordapp-example>`_:

1. ``git clone https://github.com/corda/cordapp-example``
2. ``cd cordapp-example``
3. ``./gradlew clean deployNodes``

Starting the nodes
~~~~~~~~~~~~~~~~~~

Quickstart Local Docker
^^^^^^^^^^^^^^^^^^^^^^^

1. ``cd kotlin-source/build/nodes``
2. ``java -jar <path/to/network-builder-jar> -d .``

If you run ``docker ps`` to see the running containers, the following output should be displayed:

.. sourcecode:: shell

    CONTAINER ID        IMAGE                               COMMAND             CREATED             STATUS              PORTS                                                                                                    NAMES
    8b65c104ba7c        node-bigcorporation:corda-network   "/run-corda.sh"     14 seconds ago      Up 13 seconds       0.0.0.0:32788->10003/tcp, 0.0.0.0:32791->10005/tcp, 0.0.0.0:32790->10020/tcp, 0.0.0.0:32789->12222/tcp   bigcorporation0
    3a7af5543c3a        node-bankofcorda:corda-network      "/run-corda.sh"     14 seconds ago      Up 13 seconds       0.0.0.0:32787->10003/tcp, 0.0.0.0:32786->10005/tcp, 0.0.0.0:32785->10020/tcp, 0.0.0.0:32784->12222/tcp   bankofcorda0
    a7b84444feed        node-notaryservice:corda-network    "/run-corda.sh"     23 seconds ago      Up 22 seconds       0.0.0.0:32783->10003/tcp, 0.0.0.0:32782->10005/tcp, 0.0.0.0:32781->10020/tcp, 0.0.0.0:32780->12222/tcp   notaryservice0

Quickstart Remote Azure
^^^^^^^^^^^^^^^^^^^^^^^

1. ``cd kotlin-source/build/nodes``
2. ``java -jar <path/to/network-builder-jar> -b AZURE -d .``

.. note:: The Azure configuration is handled by the az-cli utility. See the :ref:`pre-requisites`.

Interacting with the nodes
~~~~~~~~~~~~~~~~~~~~~~~~~~

You can interact with the nodes by SSHing into them on the port that is mapped to 12222. For example, to SSH into the
``bankofcorda0`` node, you would run:

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

Adding additional nodes
~~~~~~~~~~~~~~~~~~~~~~~

It is possible to add additional nodes to the network by reusing the nodes you built earlier. For example, to add a
node reusing the existing ``BankOfCorda`` node, you would run:

``java -jar <network-builder-jar> --add "BankOfCorda=O=WayTooBigToFailBank,L=London,C=GB"``

To confirm the node has been started correctly, run the following in the previously connected SSH session:

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

The Corda Network Builder also provides a GUI for when automated interactions are not required. To launch this, run
``java -jar <path/to/network-builder-jar> -g``.