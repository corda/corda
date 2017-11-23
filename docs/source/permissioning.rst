Network Permissioning
=====================

The keystores located in ``<workspace>/certificates/`` are required to connect to the Corda network securely.
In development mode (when ``devMode = true``, see ":doc:`corda-configuration-file`" for more information) a pre-configured
keystore will be used if the keystore does not exist. This is to ensure developers can get the nodes working as quickly
as possible.

However this is not secure for the real network and must be protected within a firewall. This documentation will explain
the procedure of setting up a permissioned Corda network with your own certificate authority.

To create your onw private network, you will need to create a certificate authority (CA) to issue certificates for the
nodes to join the network.

You can use any standard key tools or Corda's ``X509Utilities`` (which uses Bouncy Castle) to create the public private
keypair and certificates.

Create the certificate authority
--------------------------------

* Create root CAs and truststore
1, Create a new keypair
2, Self sign the certificate, with basic constraints extension set to ``true``, this will be used asthe root CA keys and root CA certificate.
3, Store the root keypair and the certificate to a keystore for later use.
4, Store the root CA's certificate with to a java keystore with alias "cordarootca" and name the keystore "truststore.jks",
this will need to be distributed to every node.

.. warning:: The root's private key should be protected and kept safe.

* Create Intermediate CA
1, Create a new keypair for intermediate CA
2, Sign the certificate with the root CA key, with basic constraints extension set to ``true``.
3, Store the intermediate keypair and the certificate chain (Intermediate CA certificate and Root CA certificate) to a keystore for later use.

.. note:: The intermediate CA is used instead of the root CA for day-to-day key signing, this is to reduce the risk of
compromising the root private key.

Create node identity keystore and TLS keystore
----------------------------------------------

* Create Node CA and TLS keystore
1, On each node, create a new keypair.
2, Sign the certificate using the intermediate CA key with basic constraints extension set to ``true``
3, Store the keypair in a java keystore using alias "cordaclientca", and name the keystore "nodekeystore.jks".

.. note:: We call this "Node CA" because it has the authority to issue child certificates, this is used to sign identity keys and anonymous keys

* Create Node TLS Certificate and SSL keystore
1, On each node, create a new keypair.
2, Sign the certificate with the Node CA key with basic constraints extension set to ``false``.
3, Store the key and certificates in a java keystore with alias "cordaclienttls", name the keystore "sslkeystore.jks".

* Finally, copy "nodekeystore.jks", "sslkeystore.jks" and "truststore.jks" created in previous steps to corda node's certificate directory.

Joining the R3 network
----------------------


