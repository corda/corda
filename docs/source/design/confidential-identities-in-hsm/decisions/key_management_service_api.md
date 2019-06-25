# Design Decision: Key Management Service API

## Background / Context

`KeyManagementService` is the component that wraps the `CryptoService` and is responsible for storing the required mappings for legal & confidential identities and using this mapping to perform signing, leveraging the operations provided by the `CryptoService`.
This section covers the discussion around the changes needed on the APIs of the `KeyManagementService` and the associated implications of each alternative.

Note that `KeyManagementService` is actually an interface with multiple implementations. All of them are used for testing purposes, except for one, the `BasicHSMKeyManagementService`.
This is the component that is actually used in production code. The name contains the `HSM` part for historical reasons, since the component can also perform cryptographic operations locally without the use of an HSM, depending on the configuration of the underlying `CryptoService`.

The main APIs that are relevant in the context of this section are the following:
```kotlin
fun freshKey(): PublicKey
fun freshKey(externalId: UUID): PublicKey
fun freshKeyAndCert(identity: PartyAndCertificate, revocationEnabled: Boolean): PartyAndCertificate
fun freshKeyAndCert(identity: PartyAndCertificate, revocationEnabled: Boolean, externalId: UUID): PartyAndCertificate
...
fun sign(bytes: ByteArray, publicKey: PublicKey): DigitalSignature.WithKey
fun sign(signableData: SignableData, publicKey: PublicKey): TransactionSignature
```
The first group is used for generating new confidential identities, while the next two are used for signing with the associated private key of an identity's public key.

An important observation is the following: these APIs are used from the confidential identities flows, but they are also used from other flows and potentially from customers' custom flows as well, since they consist public APIs.

For the discussion below, we will refer to the first group of operations as `freshKey()` and to the second group as `sign()`.

## Options Analysis

### A. Use existing APIs of `KeyManagementService`

This option entails using the existing APIs. This means the API will not change at all externally, inputs and outputs are the same.
However, when `freshKey()` is invoked, the component identifies what kind of confidential identities should be used depending on the node's configuration.
It then generates and stores the confidential identity's keys accordingly in the database.
When `sign()` is invoked, the component retrieves the mapping for the provided `PublicKey` from the database and identifies the type of the confidential identity and performs the signing operation accordingly.
For instance, in the case of wrapped keys it will delegate the signing operation to the HSM, while in the case of regular confidential identities, it will perform the signing locally.

#### Advantages

1. No new public APIs introduced.
2. Conditional logic on the type of confidential identities used is encapsulated inside the `KeyManagementService`, not exposed to callers. 


#### Disadvantages

1. The behaviour change will be applied on a bigger code surface, not only on the confidential identities flows, which introduces bigger risk.

### B. Create new APIs for `KeyManagementService`

This option entails introducing a new API for `freshKey()`, which will explicitly create confidential identities using an HSM.
The `sign()` operation can still be re-used.
As a result of this, the confidential identities flows can be adjusted to use the new API, while all the other places can still keep using the old API.
Note that customers that want to use the feature from custom flows (and not using the confidential identities flows), they will have to make code changes to switch to the new API for generating keys.

Given this is planned to be released in a minor release, the new API will most likely have to be annotated with `@CordaInternal`, so that there's no addition to the public API in a minor release.
This annotation could be removed in the next major release, exposing it as part of the public API. This means clients that want to leverage the new feature will need to either use the confidential identities flows or accept to use an internal API.

#### Advantages

1. The impact of the change is smaller, thus reducing the risk.

#### Disadvantages

1. The conditional logic, depending on the node's configuration will now be exposed and the customers will have to select the proper API to invoke accordingly (e.g. old `freshKey` or the new one). This transfers code complexity from the platform to the customers' code.
2. Customers that want to use the new feature in their existing flows, without using the existing confidential identities flows will need to write additional code, instead of just adding the necessary node configuration.

## Recommendation and justification

Proceed with option A, so that customers can make use of the feature in the easiest and simplest possible way (i.e. only adjusting the node's configuration).