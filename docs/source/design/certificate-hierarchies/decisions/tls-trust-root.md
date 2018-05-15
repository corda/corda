Design Decision: Certificate Hierarchy
======================================

## Background / Context

This document purpose is to make a decision on the certificate hierarchy. It is necessary to make this decision as it
affects development of features (e.g. Certificate Revocation List).

## Options Analysis

There are various options in how we structure the hierarchy above the node CA.

### Option 1: Single trust root

Under this option, TLS certificates are issued by the node CA certificate.

#### Advantages

- Existing design

#### Disadvantages

- The Root CA certificate is used to sign both intermediate certificates and CRL. This may be considered as a drawback as the Root CA should be used only to issue other certificates.

### Option 2: Separate TLS vs. identity trust roots

This option splits the hierarchy by introducing a separate trust root for TLS certificates. 

#### Advantages

- Simplifies issuance of TLS certificates (implementation constraints beyond those of other certificates used by Corda - specifically, EdDSA keys are not yet widely supported for TLS certificates)
- Avoids requirement to specify accurate usage restrictions on node CA certificates to issue their own TLS certificates

#### Disadvantages

- Additional complexity

## Recommendation and justification

Proceed with option 1 (Single Trust Root) for current purposes.

Feasibility of option 2 in the code should be further explored in due course.