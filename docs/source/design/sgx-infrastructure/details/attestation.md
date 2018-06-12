### Terminology recap

**measurement**: The hash of an enclave image, uniquely pinning the code and related configuration
**report**: A datastructure produced by an enclave including the measurement and other non-static properties of the
  running enclave instance (like the security version number of the hardware)
**quote**: A signed report of an enclave produced by Intel's quoting enclave.

# Attestation

The goal of attestation is to authenticate enclaves. We are concerned with two variants of this, enclave to non-enclave
attestation and enclave to enclave attestation.

In order to authenticate an enclave we need to establish a chain of trust rooted in an Intel signature certifying that a
report is coming from an enclave running on genuine Intel hardware.

Intel's recommended attestation protocol is split into two phases.

1. Provisioning
The first phase's goal is to establish an Attestation Key(AK) aka EPID key, unique to the SGX installation.
The establishment of this key uses an underdocumented protocol similar to the attestation protocol:
 - Intel provides a Provisioning Certification Enclave(PCE). This enclave has special privileges in that it can derive a
   key in a deterministic fashion based on the *provisioning* fuse values. Intel stores these values in their databases
   and can do the same derivation to later check a signature from PCE.
 - Intel provides a separate enclave called the Provisioning Enclave(PvE), also privileged, which interfaces with PCE
   (using local attestation) to certify the PvE's report and talks with a special Intel endpoint to join an EPID group
   anonymously. During the join Intel verifies the PCE's signature. Once the join happened the PvE creates a related
   private key(the AK) that cannot be linked by Intel to a specific CPU. The PvE seals this key (also sometimes referred
   to as the "EPID blob") to MRSIGNER, which means it can only be unsealed by Intel enclaves.

2. Attestation
 - When a user wants to do attestation of their own enclave they need to do so through the Quoting Enclave(QE), also
   signed by Intel. This enclave can unseal the EPID blob and use the key to sign over user provided reports
 - The signed quote in turn is sent to the Intel Attestation Service, which can check whether the quote was signed by a
   key in the EPID group. Intel also checks whether the QE was provided with an up-to-date revocation list.

The end result is a signature of Intel over a signature of the AK over the user enclave quote. Challengers can then
simply check this chain to make sure that the user provided data in the quote (probably another key) comes from a
genuine enclave.

All enclaves involved (PCE, PvE, QE) are owned by Intel, so this setup basically forces us to use Intel's infrastructure
during attestation (which in turn forces us to do e.g. MutualTLS, maintain our own proxies etc). There are two ways we
can get around this.

1. Hook the provisioning phase. During the last step of provisioning the PvE constructs a chain of trust rooted in
   Intel. If we can extract some provable chain that allows proving of membership based on an EPID signature then we can
   essentially replicate what IAS does.
2. Bootstrap our own certification. This would involve deriving another certification key based on sealing fuse values
   and getting an Intel signature over it using the original IAS protocol. This signature would then serve the same
   purpose as the certificate in 1.

## Non-enclave to enclave channels

When a non-enclave connects to a "leaf" enclave the goal is to establish a secure channel between the non-enclave and
the enclave by authenticating the enclave and possibly authenticating the non-enclave. In addition we want to provide
secrecy of the non-enclave. To this end we can use SIGMA-I to do a Diffie-Hellman key exchange between the non-enclave
identity and the enclave identity.

The enclave proves the authenticity of its identity by providing a certificate chain rooted in Intel. If we do our own
enclave certification then the chain goes like this:

* Intel signs quote of certifying enclave containing the certifying key pair's public part.
* Certifying key signs report of leaf enclave containing the enclave's temporary identity.
* Enclave identity signs the relevant bits in the SIGMA protocol.

Intel's signature may be cached on disk, and the certifying enclave signature over the temporary identity may be cached
in enclave memory.

We can provide various invalidations, e.g. non-enclave won't accept signature if X time has passed since Intel's
signature, or R3's whitelisting cert expired etc.

If the enclave needs to authorise the non-enclave the situation is a bit more complicated. Let's say the enclave holds
some secret that it should only reveal to authorised non-enclaves. Authorisation is expressed as a whitelisting
signature over the non-enclave identity. How do we check the expiration of the whitelisting key's certificate?

Calendar time inside enclaves deserves its own [document](time.md), the gist is that we simply don't have access to time
unless we trust a calendar time oracle.

Note however that we probably won't need in-enclave authorisation for *stateless* enclaves, as these have no secrets to 
reveal at all. Authorisation would simply serve as access control, and we can solve access control in the hosting
infrastructure instead.

## Enclave to enclave channels

Doing remote attestation between enclaves is similar to enclave to non-enclave, only this time authentication involves
verifying the chain of trust on both sides. However note that this is also predicated on having access to a calendar
time oracle, as this time expiration checks of the chain must be done in enclaves. So in a sense both enclave to enclave
and stateful enclave to non-enclave attestation forces us to trust a calendar time oracle.

But note that remote enclave to enclave attestation is mostly required when there *is* sealed state (secrets to share
with the other enclave). One other use case is the reduction of audit surface, once it comes to that. We may be able to
split stateless enclaves into components that have different upgrade lifecycles. By doing so we ease the auditors' job
by reducing the enclaves' contracts and code size.
