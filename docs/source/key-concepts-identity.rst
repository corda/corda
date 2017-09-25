Identity
========

.. topic:: Summary

   * *Identities in Corda can represent legal identities or service identities*
   * *Identities are verified by X.509 certificate*
   * *Well known identities are stored in the network map*

Identities in Corda can represent:

* Legal identity (almost always ofan organisation), or a service identity. Legal identities
are used for parties in a transaction, such as owner of some cash state.
* Service identit are used for those providing transaction-related services, such as notary, or oracle.

Service identities are distinct to legal identities so that distributed services can exist on nodes owned by different
organisations. Such distributed service identities are based on ``CompositeKeys`` (see :doc:`api-core-types` for more
details on composite keys).

Identities are either well known or confidential, depending on whether they are distributed publicly:

* Well known identities are the generally identifiable public key of a legal entity or service, which makes them
ill-suited to transactions where confidentiality of participants is required.
* Confidential identities are only distributed to those who are involved in transactions with the identity.

Although there are several elements to the Corda transaction privacy model, including ensuring that transactions are
only shared with those who need to see them, and use of Intel SGX, it is important to provide defense in depth against
privacy breaches. Confidential identities are used to ensure that even if a third party gets access to an unencrypted
transaction, they cannot identify the participants without additional information.

Name
----

Identity names are X.500 distinguished names with Corda-specific constraints applied. In order to be compatible with
other implementations (particularly TLS implementations), we constrain the allowed X.500 attribute types to a subset of
the minimum supported set for X.509 certificates (specified in RFC 3280), plus the locality attribute:

* organization (O)
* state (ST)
* locality (L)
* country (C)
* organizational-unit (OU)
* common name (CN) - used only for service identities

The organisation, locality and country attributes are required, while state, organisational-unit and common name are
optional. Attributes cannot be be present more than once in the name. The "country" code is strictly restricted to valid
ISO 3166-1 two letter codes.

Certificates
------------

Nodes must be able to verify the identity of the owner of a public key, which is achieved using X.509 certificates.
Organisations are issued a certificate for a node certificate authority (CA) by submitting a signing request to the
network Doorman service. The Doorman service applies appropriate identity checks then issues a certificate to the
calling organisation. From this initial CA certificate the node creates and signs two further certificates, one for TLS
use, one for identifying the node in transactions. These are submitted to the network map service for publication as
part of the node information.

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

