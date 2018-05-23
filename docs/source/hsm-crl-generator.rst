HSM Certificate Generation Tool
===============================

The purpose of the HSM Certificate Revocation List (CRL) Generation Tool is to provide means for the ROOT signed CRL creation.
Currently, only the NODE-level CRL creation is automated. Other levels (i.e. INTERMEDIATE and TLS) need to be addressed as well.
Since we do not presume to update the INTERMEDIATE-level CRL often, the automation in this case is not required.
With respect to the TLS certificates, we (from the perspective of R3) are not the maintainers of those CRLs.
It is a customer responsibility to maintain those lists. However, in order to ensure correct CRL checking procedure in case of the
SSL communication we need to provide the endpoint serving an empty CRL in case the customer is not able to provide for a CRL infrastructure.
Thus necessity for an empty CRL creation.

The HSM CRL Generation Tool allows for both empty and non-empty CRL creation. It can be configured to generate direct and indirect CRLs.
A direct CRL is a CRL issued by the certificate issuer, which applies to the INTERMEDIATE certificates.
However, sometimes there is a need for creating an indirect CRL - i.e. issued by another authority different than the certificate issuer. This is the case in the TLS certificates.
The tool is implemented in such a way that the ROOT CA is always the issuing authority. Depending on the configuration, the generated
CRL can be flagged as direct or indirect.

The output of the tool is a file containing ASN.1 DER-encoded bytes of the generated CRL.