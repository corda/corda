# Design Decision: Interface for cryptographic operations

## Background / Context

Ideally, all the cryptographic operations that will be needed should be part of some interface.
This will make it easier :
* to implement the solution against a generic abstraction, being unaware of the underlying details of the HSM device used.
* to integrate multiple HSM vendors that might have small discrepancies in their APIs.
* to create a single test suite that can be run against different implementations in an easy way.

For example, this is already the case for other basic cryptographic operations (such as generating key pairs or creating signatures) in Corda, which are part of the `CryptoService` interface.
This section does not intend to specify the exact signatures of this interface, but rather investigate the alternatives on where this interface will reside and the associated implications.

## Options Analysis

### A. Use existing methods of `CryptoService`

This option entails using existing methods in the `CryptoService` interface. 
To give an example, the existing interface contains the following methods:
```kotlin
fun generateKeyPair(alias: String, scheme: SignatureScheme): PublicKey
fun sign(alias: String, data: ByteArray): ByteArray
```

Some of these methods can be re-purposed to cover the new use-case.

To provide a concrete example, if we assume that we use a wrapped key approach, then a potential adjustment could be the following:
```kotlin
fun generateKeyPair(alias: String, scheme: SignatureScheme): (PublicKey, WrappedPrivateKey?)
fun sign(alias: String, payloadToSign: ByteArray, wrappedKey: WrappedPrivateKey?): Signature
```
Note that in this case, some of the parameters will have different semantics according to the intended use (regular cryptographic operations or ones related to wrapped keys).

#### Advantages

1. Maintain a single interface with fewer methods, so potentially less code to understand.

#### Disadvantages

1. Some of the operations in `CryptoService` will now acquire a significant amount of conditional code to accomodate the different use-cases. This can make the code harder to reason about from a caller's perspective.
2. Semantics of the API will become less clear.
3. Implementation for 2 different sets of functionality (legal identities & confidential identities) are in risk of getting tightly coupled. This could mean that changes intended for the former could affect the latter or in reverse.

### B. Introduce new methods in `CryptoService`

This option involves using the same interface `CryptoService`, but adding separate methods used only for the new APIs (i.e. wrapped keys).
This would look like the following:
```kotlin
interface CryptoService {
    //existing methods
    ...
    fun generateWrappedKey(masterKeyAlias: String, childKeyScheme: SignatureScheme): WrappedPrivateKey
    fun sign(masterKeyAlias: String, wrappedPrivateKey: WrappedPrivateKey, payloadToSign: ByteArray): Signature
}
```
where:
* the `childKeyScheme` parameter contains information for the generated key pair, such as the algorithm (RSA/DSA/EC) or the key size (number of bits).
* `WrappedPrivateKey` is a container of all the data related to the key (except the key material), such as any algorithm metadata and the wrapped/encrypted key material.

#### Advantages
1. There is a single interface/component representing the interactions with HSM, thus making it a little easier to reason about the code.
2. Functionality that needs to be re-used across the original cryptographic operations and the new ones (i.e. authentication) are located in a single place.
3. Adding support for new HSMs is easier to track, so that support for both the node identity and confidential identities is added at a single release, unless not possible.

#### Disadvantages
1. These new methods will only be included in ENT, so a new diff will be introduced in `CryptoService` between OS and ENT. Note: the impact of this should be minimal, since this interface is relatively stable and implementations are expected to be added only in ENT.

### C. Create a new interface

This option entails creating a new interface for the operations needed, which will be the same as in Option B. 

An indicative example of such an interface would be the following:
```kotlin
interface WrappedKeyCryptoService {
    fun generateWrappedKey(masterKeyAlias: String, childKeyScheme: SignatureScheme): WrappedPrivateKey
    fun sign(masterKeyAlias: String, wrappedPrivateKey: WrappedPrivateKey, payloadToSign: ByteArray): Signature
}
```

#### Advantages
1. Prevents OS/ENT diffs in a single file.

#### Disadvantages

1. Multiple components representing an HSM, potentially making code harder to follow.

## Recommendation and justification

Proceed with Option B, since it's the simpler option, allows for easy code reuse and will encourage easier tracking of adding support for new HSMs.
