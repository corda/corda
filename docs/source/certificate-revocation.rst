Certificate Revocation List
===========================

The certificate revocation list consists of certificate serial numbers of issued certificates that are no longer valid.
It is used by nodes when they establish a TLS connection between each other and need to ensure on certificate validity.
In order to add entries to the certificate revocation list there is the certificate revocation process that resembles
the one from the certificate signing request (CSR).
Note that, once added the entries cannot be removed from the certificate revocation list.

In the similar vein as CSR, it is integrated with the JIRA tool, and the submitted requests follow exactly the same lifecycle.
To support the above functionality, there are two externally available REST endpoints: one for the certificate revocation request submission and
one for the certificate revocation list retrieval.

Since the certificate revocation list needs to be signed, the revocation process integrates with the HSM signing service.
The certificate revocation list signing process requires human interaction and there is a separate tool designed for that purpose.
Once signed the certificate revocation list replaces the current one.

Note: It is assumed that the signed certificate revocation list is always available - even if it's empty.

HTTP certificate revocation protocol
------------------------------------

The set of REST end-points for the revocation service are as follows.

+----------------+-----------------------------------------+----------------------------------------------------------------------------------------------------------------------------------------------+
| Request method | Path                                    | Description                                                                                                                                  |
+================+=========================================+==============================================================================================================================================+
| POST           | /certificate-revocation-request         | For the node to upload a certificate revocation request.                                                                                     |
+----------------+-----------------------------------------+----------------------------------------------------------------------------------------------------------------------------------------------+
| GET            | /certificate-revocation-list/doorman    | For the node to obtain the certificate revocation list. Returns an ASN.1 DER-encoded java.security.cert.X509CRL object.                              |
+----------------+-----------------------------------------+----------------------------------------------------------------------------------------------------------------------------------------------+

Submission of the certificate revocation requests expects the following fields to be present in the request payload:

:certificateSerialNumber: Serial number of the certificate that is to be revoked.

:csrRequestId: Certificate signing request identifier associated with the certificate that is to be revoked.

:legalName: Legal name associated with the certificate that is to be revoked.

:reason: Revocation reason (as specified in the java.security.cert.CRLReason). The following values are allowed.

    :UNSPECIFIED: This reason indicates that it is unspecified as to why the certificate has been revoked.

    :KEY_COMPROMISE: This reason indicates that it is known or suspected that the certificate subject's private key has been compromised. It applies to end-entity certificates only.

    :CA_COMPROMISE: This reason indicates that it is known or suspected that the certificate subject's private key has been compromised. It applies to certificate authority (CA) certificates only.

    :AFFILIATION_CHANGED: This reason indicates that the subject's name or other information has changed.

    :SUPERSEDED: This reason indicates that the certificate has been superseded.

    :CESSATION_OF_OPERATION: This reason indicates that the certificate is no longer needed.
    
    :PRIVILEGE_WITHDRAWN: This reason indicates that the privileges granted to the subject of the certificate have been withdrawn.

:reporter: Issuer of this certificate revocation request.

Note: At least one of the three: certificateSerialNumber, csrRequestId or legalName needs to be specified.
      Also, Corda AMQP serialization framework is used as the serialization framework.

Because of the proprietary serialization mechanism, it is assumed that those endpoints are used by dedicated tools that support this kind of data encoding.


Internal protocol
-----------------

There is an internal communication protocol between the revocation service and the HSM signing service for producing the signed CRLs.
This does not use HTTP to avoid exposing any web vulnerabilities to the signing process.


