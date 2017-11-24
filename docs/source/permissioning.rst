Network Permissioning
=====================

Corda networks are *permissioned*. To connect to a network, a node needs three keystores in its
``<workspace>/certificates/`` folder:

* ``truststore.jks``, which stores trusted public keys and certificates (in our case, those of the network root CA)
* ``nodekeystore.jks``, which stores the node’s identity keypairs and certificates
* ``sslkeystore.jks``, which stores the node’s TLS keypairs and certificates

In development mode (i.e. when ``devMode = true``, see :doc:`corda-configuration-file` for more information),
pre-configured keystores are used if the required keystores do not exist. This ensures that developers can get the
nodes working as quickly as possible.

However, these pre-configured keystores are not secure. For a real network, you need to create a certificate authority
that will be used in the creation of these keystores for each node joining the network. The instructions below explain
how to do this.

Creating the node certificate authority
---------------------------------------

You can use any standard key tools or Corda's ``X509Utilities`` (which uses Bouncy Castle) to create the required
public/private keypairs and certificates. The keypairs and certificates should obey the following restrictions:

* The certificates must follow the `X.509 standard <https://tools.ietf.org/html/rfc5280>`_

   * We recommend X.509 v3 for forward compatibility

* The TLS certificates must follow the `TLS v1.2 standard <https://tools.ietf.org/html/rfc5246>`_

Creating the root CA's keystore and truststore
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

1. Create a new keypair

   * This will be used as the root CA's keypair

2. Create a self-signed certificate for the keypair. The basic constraints extension must be set to ``true``

   * This will be used as the root CA's certificate

3. Store the root CA's keypair and certificate in a keystore for later use

4. Store the root CA's certificate in a Java keystore named ``truststore.jks`` using the alias ``cordarootca``

.. warning:: The root CA's private key should be protected and kept safe.

Creating the intermediate CA's keystore
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

1. Create a new keypair

   * This will be used as the intermediate CA's keypair

2. Obtain a certificate for the keypair signed with the root CA key. The basic constraints extension must be set to
   ``true``

   * This will be used as the intermediate CA's certificate

3. Store the intermediate CA's keypair and certificate chain (i.e. the intermediate CA certificate *and* the root CA
   certificate) in a keystore for later use

.. note:: The intermediate CA is used instead of the root CA for day-to-day key signing. This is to reduce the risk of
   the root CA's private key being compromised.

Creating the node CA keystores and TLS keystores
------------------------------------------------

Creating the node CA keystores
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

1. On each node, create a new keypair

2. Obtain a certificate for the keypair signed with the intermediate CA key. The basic constraints extension must be
   set to ``true``

3. Store the keypair in a Java keystore named ``nodekeystore.jks`` using the alias ``cordaclientca``

.. note:: Each node is considered a "node CA" because it has the authority to issue child certificates that are used to
   sign identity keys and anonymous keys

Creating the node TLS keystores
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

1. On each node, create a new keypair

2. Create a certificate for the keypair signed with the node CA key. The basic constraints extension must be set to
   ``false``

3. Store the key and certificates in a Java keystore named ``sslkeystore.jks`` using the alias ``cordaclienttls``

Installing the certificates on the nodes
----------------------------------------

For each node:

1. Copy the node's ``nodekeystore.jks`` and ``sslkeystore.jks`` keystores to the node's certificate directory

2. Copy the ``truststore.jks`` keystore created by the root CA to the node's certificate directory