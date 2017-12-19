Network Permissioning - Certificate Specification
=================================================

Certificates used by Corda have additional constraints in their contents and hierarchical structure. In a typical
installation node administrators should not need to be aware of these, however in some cases node certificates may
be managed by external tools (such as an existing PKI solution deployed within an organisation), in which case it is
important to understand these constraints.

There are a number of roles in Corda that certificates are used for:

* Doorman (Intermediate CA)
* Well known service identity (network map and notary)
* Node CA
* TLS
* Well known legal identity
* Confidential legal identity

Extension
---------

The Corda role that a certificate relates to is specified by custom X.509 v3 extension. This extension has OID 1.3.6.1.4.1.50530.1.1
and is non-critical, as it is safe for implementations outside of Corda nodes to ignore the extension. The extension
contains a single ASN.1 integer identifying the type of identity the certificate is for:

1. Doorman
2. Well known service identity
3. Node CA
4. TLS
5. Well known legal identity
6. Confidential legal identity

Hierarchy
---------

Certificate path validation is extended to enforce that the extension must be present where its issuer's certificate included the extension, and that:

* Doorman certificates are issued by a certificate without the extension present
* Well known service identity certificates are issued by a certificate marked as Doorman
* Node CA certificates are issued by a certificate marked as Doorman
* Well known legal identity/TLS certificates are issued by a certificate marked as node CA
* Confidential legal identity certificates are issued by a certificate marked as well known legal identity
* Party certificates are marked as either a well known identity or a confidential identity
* The structure of certificates above Doorman/Network map is intentionally left untouched, as they are not relevant to the identity service and therefore there is no advantage in enforcing a specific structure on those certificates. The certificate hierarchy consistency checks are required because nodes can issue their own certificates and can set their own role flags on certificates, and it's important to verify that these are set consistently with the certificate hierarchy design. As as side-effect this also acts as a secondary depth restriction on issued certificates.
