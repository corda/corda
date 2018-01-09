Network Permissioning
=====================

.. contents::

Corda networks are *permissioned*. To connect to a network, a node needs three keystores in its
``<workspace>/certificates/`` folder:

* ``truststore.jks``, which stores trusted public keys and certificates (in our case, those of the network root CA)
* ``nodekeystore.jks``, which stores the node’s identity keypairs and certificates
* ``sslkeystore.jks``, which stores the node’s TLS keypairs and certificates

In development mode (i.e. when ``devMode = true``, see :doc:`corda-configuration-file` for more information),
pre-configured keystores are used if the required keystores do not exist. This ensures that developers can get the
nodes working as quickly as possible.

However, these pre-configured keystores are not secure. Production deployments require a secure certificate authority.
Most production deployments will use an existing certificate authority or construct one using software that will be
made available in the coming months. Until then, the documentation below can be used to create your own certificate
authority.

Network structure
-----------------
A Corda network has three types of certificate authorities (CAs):

* The **root network CA**
* The **intermediate network CA**

  * The intermediate network CA is used instead of the root network CA for day-to-day
    key signing to reduce the risk of the root network CA's private key being compromised

* The **node CAs**

  * Each node serves as its own CA in issuing the child certificates that it uses to sign its identity
    keys and TLS certificates

We can visualise the permissioning structure as follows:

.. image:: resources/certificate_structure.png
   :scale: 55%
   :align: center

Keypair and certificate formats
-------------------------------
You can use any standard key tools or Corda's ``X509Utilities`` (which uses Bouncy Castle) to create the required
public/private keypairs and certificates. The keypairs and certificates should obey the following restrictions:

* The certificates must follow the `X.509 standard <https://tools.ietf.org/html/rfc5280>`_

   * We recommend X.509 v3 for forward compatibility

* The TLS certificates must follow the `TLS v1.2 standard <https://tools.ietf.org/html/rfc5246>`_

* The root network CA, intermediate network CA and node CA keys, as well as the node TLS
  keys, must follow one of the following schemes:

    * ECDSA using the NIST P-256 curve (secp256r1)

    * RSA with 3072-bit key size

Creating the root and intermediate network CAs
----------------------------------------------

Creating the root network CA's keystore and truststore
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

1. Create a new keypair

   * This will be used as the root network CA's keypair

2. Create a self-signed certificate for the keypair. The basic constraints extension must be set to ``true``

   * This will be used as the root network CA's certificate

3. Create a new keystore and store the root network CA's keypair and certificate in it for later use

   * This keystore will be used by the root network CA to sign the intermediate network CA's certificate

4. Create a new Java keystore named ``truststore.jks`` and store the root network CA's certificate in it using the
   alias ``cordarootca``

   * This keystore will be provisioned to the individual nodes later

.. warning:: The root network CA's private key should be protected and kept safe.

Creating the intermediate network CA's keystore
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

1. Create a new keypair

   * This will be used as the intermediate network CA's keypair

2. Obtain a certificate for the keypair signed with the root network CA key. The basic constraints extension must be
   set to ``true``

   * This will be used as the intermediate network CA's certificate

3. Create a new keystore and store the intermediate network CA's keypair and certificate chain
   (i.e. the intermediate network CA certificate *and* the root network CA certificate) in it for later use

   * This keystore will be used by the intermediate network CA to sign the nodes' identity certificates

Creating the node CA keystores and TLS keystores
------------------------------------------------

Creating the node CA keystores
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

1. For each node, create a new keypair

2. Obtain a certificate for the keypair signed with the intermediate network CA key. The basic constraints extension must be
   set to ``true``

3. Create a new Java keystore named ``nodekeystore.jks`` and store the keypair in it using the alias ``cordaclientca``

   * The node will store this keystore locally to sign its identity keys and anonymous keys

Creating the node TLS keystores
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

1. For each node, create a new keypair

2. Create a certificate for the keypair signed with the node CA key. The basic constraints extension must be set to
   ``false``

3. Create a new Java keystore named ``sslkeystore.jks`` and store the key and certificates in it using the alias
   ``cordaclienttls``

   * The node will store this keystore locally to sign its TLS certificates

Installing the certificates on the nodes
----------------------------------------
For each node, copy the following files to the node's certificate directory (``<workspace>/certificates/``):

1. The node's ``nodekeystore.jks`` keystore
2. The node's ``sslkeystore.jks`` keystore
3. The root network CA's ``truststore.jks`` keystore
