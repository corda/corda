Using the Bootstrapper
++++++++++++++++++++++

You can skip this section when you're setting up or joining a cluster with
doorman and network map.

Once the database is set up, you can prepare your configuration files of your notary
nodes and use the bootstrapper to create a Corda network, see
:doc:`../setting-up-a-corda-network`. Remember to configure
``notary.serviceLegalName`` in addition to ``myLegalName`` for all members of
your cluster.

You can find the documentation of the bootstrapper at :doc:`../setting-up-a-corda-network`.

Expected Outcome
~~~~~~~~~~~~~~~~

You will go from a set of configuration files to a directory tree containing a fully functional Corda network.

The notaries will be visible and available on the network. You can list available notaries using the node shell.

.. code:: sh

  run notaryIdentities

The output of the above command should include the ``notary.serviceLegalName``
you have configured, e.g. ``O=HA Notary, L=London, C=GB``.

CorDapp developers should select the notary service identity from the network map cache.

.. code:: kotlin

  serviceHub.networkMapCache.getNotary(CordaX500Name("HA Notary", "London", "GB"))
