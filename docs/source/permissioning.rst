Network Permissioning
=====================

Corda networks are *permissioned*. A node needs three keystores in its ``<workspace>/certificates/`` folder to connect
to the network:

* truststore.jks
* nodekeystore.jks
* sslkeystore.jks

In development mode (i.e. when ``devMode = true``, see ":doc:`corda-configuration-file`" for more information),
pre-configured keystores are used if the required keystores do not exist. This ensures that developers can get the
nodes working as quickly as possible.

However, these pre-configured keystores are not secure. For a real network, you need to create your own certificate
authority that will issue certificates to nodes joining the network. The instructions below explain how to do this.

Creating the certificate authority
----------------------------------

You can use any standard key tools or Corda's ``X509Utilities`` (which uses Bouncy Castle) to create the required
public/private keypairs and certificates.

Creating the root CA and truststore
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

1. Create a new keypair

   * This will be used as the root CA's keypair

2. Self-sign the certificate, with the basic constraints extension set to ``true``

   * This will be used as the root CA's certificate

3. Store the root CA's keypair and certificate in a keystore for later use

4. Store the root CA's certificate in a java keystore named "truststore.jks" using the alias "cordarootca"

.. warning:: The root CA's private key should be protected and kept safe.

Creating the intermediate CA
^^^^^^^^^^^^^^^^^^^^^^^^^^^^

1. Create a new keypair

   * This will be used as the intermediate CA's keypair

2. Sign the certificate with the root CA key, with the basic constraints extension set to ``true``

   * This will be used as the intermediate CA's certificate

3. Store the intermediate CA's keypair and certificate chain (i.e. the intermediate CA certificate *and* the root CA
   certificate) in a keystore for later use

.. note:: The intermediate CA is used instead of the root CA for day-to-day key signing. This is to reduce the risk of
   the root CA's private key being compromised.

Creating the node identity keystore and TLS keystore
----------------------------------------------------

Creating the node CAs and TLS keystores
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

1. On each node, create a new keypair

2. Sign the certificate using the intermediate CA key with the basic constraints extension set to ``true``

3. Store the keypair in a java keystore named "nodekeystore.jks" using the alias "cordaclientca"

.. note:: Each node is considered a "node CA" because it has the authority to issue child certificates that are used to
   sign identity keys and anonymous keys

Creating the node TLS certificate and SSL keystores
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

1. On each node, create a new keypair

2. Sign the certificate with the node CA key with the basic constraints extension set to ``false``

3. Store the key and certificates in a java keystore named "sslkeystore.jks" using the alias "cordaclienttls"

4. Copy the "nodekeystore.jks" and "sslkeystore.jks" keystores to the node's certificate directory

5. Copy the "truststore.jks" keystore created by the root CA to the node's certificate directory