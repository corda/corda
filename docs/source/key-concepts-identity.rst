Identity
========

.. topic:: Summary

   * *Identities in Corda can represent legal identities or service identities*
   * *Identities are attested to by X.509 certificate signed by the Doorman or a well known identity*
   * *Well known identities are published in the network map*
   * *Confidential identities are only shared on a need to know basis*

Identities in Corda can represent:

* The legal identity of an organisation
* The service identity of a network service

These identities are distinct from the RPC users that are able to connect to the node via RPC.

Identity types
--------------

Whereas legal identities are used to represent parties in transactions, such as the owner of a cash state, service identities 
are used for entities providing transaction-related services, such as notaries or oracles. Service identities are distinct 
from legal identities so that distributed services can exist on nodes owned by different organisations. Such distributed service identities are based on ``CompositeKeys``, which describe the valid sets of signers for a signature from the service.
See :doc:`api-core-types` for more technical detail on composite keys.

Identities are either well known or confidential, depending on whether their X.509 certificate (and corresponding
certificate path to a trusted root certificate) is published:

* Well known identities are the generally identifiable public key of a legal entity or service, which makes them
  ill-suited to transactions where confidentiality of participants is required. This certificate is published in the
  network map service for anyone to access.
* Confidential identities are only published to those who are involved in transactions with the identity. The public
  key may be exposed to third parties (for example to the notary service), but distribution of the name and X.509
  certificate is limited.

Although there are several elements to the Corda transaction privacy model, including ensuring that transactions are
only shared with those who need to see them, and planned use of Intel SGX, it is important to provide defense in depth against
privacy breaches. Confidential identities are used to ensure that even if a third party gets access to an unencrypted
transaction, they cannot identify the participants without additional information.

Certificates
------------

Nodes must be able to verify the identity of the owner of a public key, which is achieved using X.509 certificates.
When first run a node generates a key pair and submits a certificate signing request to the network Doorman service
(see  :doc:`permissioning`).
The Doorman service applies appropriate identity checks then issues a certificate to the node, which is used as the
node certificate authority (CA). From this initial CA certificate the node automatically creates and signs two further
certificates, a TLS certificate and a signing certificate for the node's well known identity. Finally the node
builds a node info record containing its address and well known identity, and registers it with the network map service.

From the signing certificate the organisation can create both well known and confidential identities. Use-cases for
well known identities include clusters of nodes representing a single identity for redundancy purposes, or creating
identities for organisational units.

It is up to organisations to decide which identities they wish to publish in the network map service, making them
well known, and which they wish to keep as confidential identities for privacy reasons (typically to avoid exposing
business sensitive details of transactions). In some cases nodes may also use private network map services in addition
to the main network map service, for operational reasons. Identities registered with such network maps must be
considered well known, and it is never appropriate to store confidential identities in a central directory without
controls applied at the record level to ensure only those who require access to an identity can retrieve its
certificate.
