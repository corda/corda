Supported cipher suites
=======================

.. contents::

The set of signature schemes supported forms a part of the consensus rules for a Corda DLT network.
Thus, it is important that implementations do not support pluggability of any crypto algorithms and do take measures
to prevent algorithms supported by any underlying cryptography library from becoming accidentally accessible.
Signing a transaction with an algorithm that is not a part of the base specification would result in a transaction
being considered invalid by peer nodes and thus a loss of consensus occurring. The introduction of new algorithms
over time will require a global upgrade of all nodes.

Corda has been designed to be cryptographically agile, in the sense that the available set of signature schemes is
carefully selected based on various factors, such as provided security-level and cryptographic strength, compatibility
with various HSM vendors, algorithm standardisation, variety of cryptographic primitives, business demand, option for
post-quantum resistance, side channel security, efficiency and rigorous testing.

Before we present the pool of supported schemes it is useful to be familiar with :doc:`key-concepts-identity`,
:doc:`permissioning` and :doc:`api-identity`. An important design decision in Corda is its shared hierarchy
between the TLS and Node Identity certificates.

Certificate hierarchy
---------------------

A Corda network has 8 types of keys and a regular node requires 4 of them:

**Network Keys**

* The **root network CA** key
* The **doorman CA** key
* The **network map** key
* The **service identity** key(s) (per service, such as a notary cluster; it can be a Composite key)

**Node Keys**

* The **node CA** key(s) (one per node)
* The **legal identity** key(s) (one per node)
* The **tls** key(s) (per node)
* The **confidential identity** key(s) (per node)

We can visualise the certificate structure as follows (for a detailed description of cert-hierarchy,
see :doc:`permissioning`):

.. image:: resources/certificate_structure.png
   :scale: 55%
   :align: center

Supported cipher suites
-----------------------
Due to the shared certificate hierarchy, the following 4 key/certificate types: **root network CA**, **doorman CA**,
**node CA** and **tls** should be compatible with the standard TLS 1.2 protocol. The latter is a requirement from the
TLS certificate-path validator. It is highlighted that the rest of the keys can be any of the 5 supported cipher suites.
For instance, **network map** is ECDSA NIST P-256 (secp256r1) in the Corda Network (CN) as it is well-supported by the
underlying HSM device, but the default for dev-mode is Pure EdDSA (ed25519).

The following table presents the 5 signature schemes currently supported by Corda. The TLS column shows which of them
are compatible with TLS 1.2, while the default scheme per key type is also shown in the last column.

+-------------------------+---------------------------------------------------------------+-----+-------------------------+
| Cipher suite            | Description                                                   | TLS | Default for             |
+=========================+===============================================================+=====+=========================+
| | Pure EdDSA using the  | | EdDSA represents the current state of the art in mainstream | NO  | - node identity         |
| | ed25519 curve         | | cryptography. It implements elliptic curve cryptography     |     | - confidential identity |
| | and SHA-512           | | with deterministic signatures a fast implementation,        |     | - network map (dev)     |
|                         | | explained constants, side channel resistance and many other |     |                         |
|                         | | desirable characteristics. However, it is relatively new    |     |                         |
|                         | | and not widely supported, for example, you can't use it in  |     |                         |
|                         | | TLS yet (a draft RFC exists but is not standardised yet).   |     |                         |
+-------------------------+---------------------------------------------------------------+-----+-------------------------+
| | ECDSA using the       | | This is the default choice for most systems that support    | YES | - root network CA       |
| | NIST P-256 curve      | | elliptic curve cryptography today and is recommended by     |     | - doorman CA            |
| | (secp256r1)           | | NIST. It is also supported by the majority of the HSM       |     | - node CA               |
| | and SHA-256           | | vendors.                                                    |     | - tls                   |
|                         |                                                               |     | - network map (CN)      |
+-------------------------+---------------------------------------------------------------+-----+-------------------------+
| | ECDSA using the       | | secp256k1 is the curve adopted by Bitcoin and as such there | NO  |                         |
| | Koblitz k1 curve      | | is a wealth of infrastructure, code and advanced algorithms |     |                         |
| | (secp256k1)           | | designed for use with it. This curve is standardised by     |     |                         |
| | and SHA-256           | | NIST as part of the "Suite B" cryptographic algorithms and  |     |                         |
|                         | | as such is more widely supported than ed25519. By           |     |                         |
|                         | | supporting it we gain access to the ecosystem of advanced   |     |                         |
|                         | | cryptographic techniques and devices pioneered by the       |     |                         |
|                         | | Bitcoin community.                                          |     |                         |
+-------------------------+---------------------------------------------------------------+-----+-------------------------+
| | RSA (3072bit) PKCS#1  | | RSA is well supported by any sort of hardware or software   | YES |                         |
| | and SHA-256           | | as a signature algorithm no matter how old, for example,    |     |                         |
|                         | | legacy HSMs will support this along with obsolete operating |     |                         |
|                         | | systems. RSA is using bigger keys than ECDSA and thus it is |     |                         |
|                         | | recommended for inclusion only for its backwards            |     |                         |
|                         | | compatibility properties, and only for usage where legacy   |     |                         |
|                         | | constraints or government regulation forbids the usage of   |     |                         |
|                         | | more modern approaches.                                     |     |                         |
+-------------------------+---------------------------------------------------------------+-----+-------------------------+
| | SPHINCS-256           | | SPHINCS-256 is a post-quantum secure algorithm that relies  | NO  |                         |
| | and SHA-512           | | only on hash functions. It is included as a hedge against   |     |                         |
| | (experimental)        | | the possibility of a malicious adversary obtaining a        |     |                         |
|                         | | quantum computer capable of running Shor's algorithm in     |     |                         |
|                         | | future. SPHINCS is based ultimately on a clever usage of    |     |                         |
|                         | | Merkle hash trees. Hash functions are a very heavily        |     |                         |
|                         | | studied and well understood area of cryptography. Thus, it  |     |                         |
|                         | | is assumed that there is a much lower chance of             |     |                         |
|                         | | breakthrough attacks on the underlying mathematical         |     |                         |
|                         | | problems. However, SPHINCS uses relatively big public keys, |     |                         |
|                         | | it is slower and outputs bigger signatures than EdDSA,      |     |                         |
|                         | | ECDSA and RSA algorithms.                                   |     |                         |
+-------------------------+---------------------------------------------------------------+-----+-------------------------+
