Network Permissioning
=====================

The keystores located in ``<workspace>/certificates/`` are required to connect to the Corda network securely.
In development mode (when ``devMode = true``, see ":doc:`corda-configuration-file`" for more information) a pre-configured
keystore will be used if the keystore does not exist. This is to ensure developers can get the nodes working as quickly
as possible.

However this is not secure for the real network and must be protected within a firewall. This documentation will explain
the procedure of setting up a private Corda network with your own certificate authority.

To create your onw private network, you will need to create a certificate authority (CA) to issue certificates for the
nodes to join the network.

You can use any standard key tools or Corda's ``X509Utilities`` (which uses Bouncy Castle) to create the public private
keypair and certificates.

Create the certificate authority
--------------------------------

* Create root CAs and truststore
Create a new keypair and a self signed certificate, with basic constraints extension set to ``true``, this will be used as
the root keys and root certificate.

Store the root keypair and the certificate to a keystore for later use.
Store the root CA's certificate with to a java keystore with alias "cordarootca" and name the keystore "truststore.jks",
this will need to be distributed to every node.

.. warning:: The root's private key should be protected and kept safe.

* Create Intermediate CA
Create a new keypair for intermediate CA, and create a certificate using the root CA key, with basic constraints extension set to ``true``.
Store the intermediate keypair and the certificates(Intermediate CA certificate and Root CA certificate) to a keystore for later use.

.. note:: The intermediate CA is used instead of the root CA for day-to-day key signing, this is to reduce the risk of
compromising the root private key.

Create node identity keystore and TLS keystore
----------------------------------------------

* Create Node CA and TLS keystore
On each node, create a new keypair, and sign the certificate using the intermediate CA key with basic constraints extension
set to ``true``, store the keypair in a java keystore with alias set to "cordaclientca", and name the keystore "nodekeystore.jks".

.. note:: We call this "Node CA" because it has the authority to issue child certificates, this is used to sign identity keys and anonymous keys

* Create Node TLS Certificate and SSL keystore
On each node, create a new keypair, and sign it with the Node CA key with basic constraints extension set to ``false``,
then store it in a java keystore with alias "cordaclienttls", name the keystore "sslkeystore.jks".

* Save the keystores and truststore to corda node's certificate directory.


Joining the R3 network
----------------------