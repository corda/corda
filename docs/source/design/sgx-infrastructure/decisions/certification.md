![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

--------------------------------------------
Design Decision: CPU certification method
============================================

## Background / Context

Remote attestation is done in two main steps.
1. Certification of the CPU. This boils down to some kind of Intel signature over a key that only a specific enclave has
   access to.
2. Using the certified key to sign business logic specific enclave quotes and providing the full chain of trust to
   challengers.

This design question concerns the way we can manage a certification key. A more detailed description is
[here](../details/attestation.md)

## Options Analysis

### A. Use Intel's recommended protocol

This involves using aesmd and the Intel SDK to establish an opaque attestation key that transparently signs quotes.
Then for each enclave we need to do several roundtrips to IAS to get a revocation list (which we don't need) and request
a direct Intel signature over the quote (which we shouldn't need as the trust has been established already during EPID
join)

#### Advantages

1. We have a PoC implemented that does this

#### Disadvantages

1. Frequent roundtrips to Intel infrastructure
2. Intel can reproduce the certifying private key
3. Involves unnecessary protocol steps and features we don't need (EPID)

### B. Use Intel's protocol to bootstrap our own certificate

This involves using Intel's current attestation protocol to have Intel sign over our own certifying enclave's
certificate that derives its certification key using the sealing fuse values.

#### Advantages

1. Certifying key not reproducible by Intel
2. Allows for our own CPU enrollment process, should we need one
3. Infrequent roundtrips to Intel infrastructure (only needed once per microcode update)

#### Disadvantages

1. Still uses the EPID protocol

### C. Intercept Intel's recommended protocol

This involves using Intel's current protocol as is but instead of doing roundtrips to IAS to get signatures over quotes
we try to establish the chain of trust during EPID provisioning and reuse it later.

#### Advantages

1. Uses Intel's current protocol
2. Infrequent rountrips to Intel infrastructure

#### Disadvantages

1. The provisioning protocol is underdocumented and it's hard to decipher how to construct the trust chain
2. The chain of trust is not a traditional certificate chain but rather a sequence of signed messages

## Recommendation and justification

Proceed with Option B. This is the most readily available and flexible option.
