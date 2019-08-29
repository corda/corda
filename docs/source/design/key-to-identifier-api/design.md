# Public Key to Identifier API Design

## Overview

Public keys may be created in Corda for a variety of reasons. The node identity will have a well-known public key, and may also have a number
of anonymous keys associated with it. Additionally, the node may also be aware of keys that are mapped to an external UUID. It is possible that
an app running on Corda may wish to know which of these possibilities a key belongs to, and if available some additional information about
this identifier. For example, an app may wish to know what UUID a key is mapped to if it is mapped to some external identifier. Currently
there is no API to expose this information outside of the core of the platform. This document discusses a possible design of an API to do
this.

## Background

The Corda node already has an implementation of similar functionality for use by internal features in the `PublicKeyToOwningIdentityCache`.
This provides a caching layer over the node's `pk_hash_to_external_id` table, and also distinguishes between unmapped keys and keys that
are completely unknown to the node. This information is captured in `KeyOwningIdentity` - a sealed class with an `UnmappedIdentity`
(representing a key not mapped to an external UUID) variant and a `MappedIdentity` variant (representing a key mapped to an external UUID).
The `PublicKeyToOwningIdentityCache` returns one of these variants if the node is aware of the key, and `null` if it is not.

Given this implementation, the main work is to establish what a sensible API looks like to expose this to apps. The main consumer of this
API is expected to be the token selection by UUID feature for Accounts. The requirement placed by this feature is that it should be possible
to establish the UUID a public key is mapped to (if it is mapped to a UUID at all). However, it is possible that future applications may
wish to use a more expanded functionality. As such, some consideration should be taken for potential future uses of this API.

## Requirements
 - A public API that can be used to establish what external UUID, if any, a public key is mapped to must be available.
 - A decision must be reached over what other information about a public key should be exposed through this API, and a path must be available
   for implementing this.
   
## Goals
 - Provide an API signature that meets the requirements listed above.
 - Provide some rationale for these choices.
 
## API Design Proposal

The suggested approach is to add a new method to the `KeyManagementService` interface:

```kotlin

/**
* Return information about a given public key. This API can be used to establish:
*  - Whether a key is mapped to an external ID
*  - Whether an unmapped key is a well known identity key, or an anonymous key
*  
* If this returns null, then the public key is unknown to the node.
*/
fun getPublicKeyInfo(key: PublicKey): PublicKeyInfo?
```

Where `PublicKeyInfo` is defined as follows:
```kotlin

/**
* [PublicKeyInfo] describes the nature of a public key that is known to the node.
*/
interface PublicKeyInfo {

    /**
    * The external ID this key is mapped to, or null if it an unmapped key.
    */
    val uuid: UUID?
}

/**
 * A public key that is mapped to an external UUID
 */
data class MappedKey(override val uuid: UUID) : PublicKeyInfo

/**
 * A public key that is an anonymous key 
 */
object UnmappedAnonymousKey : PublicKeyInfo {
    override val uuid: UUID? = null
}

/**
 * A public key that belongs to a well known node identity
 */
object WellKnownKey : PublicKeyInfo {
    override val uuid: UUID? = null
}

```

This API allows for the following:
 - The external UUID mapping can easily be obtained, meeting the concrete requirement currently placed on this API.
 - Extra information about whether the key is a well known key or an unmapped anonymous key can also be obtained by establishing what
   implementation of the `PublicKeyInfo` has been returned.
 - It should be possible to add new methods to the `PublicKeyInfo` interface to establish other data about the public key, for example whether
   it represents an anonymous unmapped key. This should not cause an API break for users on an older version of the API if they are run on a
   newer version, as the new methods will not be called by the old code. Any apps calling any methods added to the interface in a later release
   will only be able to run against that future release, so the use of these new methods on the interface implies a minimum platform version
   bump for the app.
   

