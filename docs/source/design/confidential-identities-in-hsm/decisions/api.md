# Design Decision: HSM API

## Background / Context

The obvious option of storing the confidential identities keys directly inside in the HSM is not taken into consideration, because the number of keys is expected to be a lot higher than the capacity of HSM devices, as demonstrated in the requirements section.

There are different APIs available in HSMs that can be used, so that keys for confidential identities are stored outside the HSM in a way 
that does not reveal the key material, while also allowing the node to provide some data to the HSM that can be used to infer the key material and sign a payload inside the device.

## Options Analysis

### A. Wrapped keys approach

Using this approach, a master (AES) symmetric key is generated and stored in the HSM, referred to as the *wrapping key*. 
Every time a new public/private key pair is needed for a confidential identity, the node provides the alias of the wrapping key to the HSM.
The HSM generates internally a public/private key pair, encrypts the private key and returns to the node the public key and the encrypted private key, which is referred to as the *wrapped key*.
The node stores in the database the public key along with the associated wrapped key. When a signature is required for a public key, the node retrieves the wrapped key associated with this public key and provides it to the HSM along with the wrapping key alias.
The HSM decrypts the wrapped key, using the wrapping key and then uses the decrypted private key to sign the provided payload, which is returned to the node.

#### Advantages

1. A simple construct, easy to reason about and explain to customers.
2. This attack vectors of this approach are essentially the ones on the underlying standard algorithms used for key encryption/decryption.
3. This approach has been standardised (as part of [NIST](https://csrc.nist.gov/publications/detail/sp/800-38f/final) and [PKCS#11](http://docs.oasis-open.org/pkcs11/pkcs11-curr/v2.40/csprd02/pkcs11-curr-v2.40-csprd02.html#_Toc228894722)), 
which means it's more thoroughly analysed and more likely to be supported by vendors.
4. Provides cryptographic agility, giving more flexibility for migrating/upgrading to new algorithms (for the public/private key pair), if needed.


#### Disadvantages

1. Specific care needs to be taken to ensure every implementation does not introduce potential for exposing the material key. As an example, in case of an HSM vendor based on PKCS#11 that would imply careful use of the related PKCS attributes (such as `EXTRACTABLE` and `SENSITIVE`) 

### B. Derived keys approach

Using this approach, a master key pair is generated and stored in the HSM (using elliptic curves).
Every time a new public/private key pair is needed for a confidential identity, the node provides the alias of the master key to the HSM and a numeric index.
The HSM uses the master key and the provided index to generate a (child) public/private key pair, referred to as the *derived* key pair.
The node stores in the database the public key along with the associated index. When a signature is required for a public key, the node retrieves the index associated with the public key and provides it to the HSM along with the master key alias.
The HSM re-generates the same public/private key pair and uses the private key to sign the provided payload, which is returned to the node.

#### Advantages

1. ​Reduced data transfer between the node and the HSM, since the encrypted key material is now replaced with the index.

#### Disadvantages

1. Need to introduce mutual exclusion on the index used to create a new confidential identity by concurrent flows, resulting in increased complexity and potentially decreased performance.
2. Reduced cryptographic agility due to the coupling on elliptic curves.
3. Harder to reason about the threat model, because of intricate vulnerabilities of elliptic curves. For instance, ​an attacker in possession of a child private key and the master public key can easily recover the master private key, as demonstrated in [this report](https://eprint.iacr.org/2014/998).
4. Lack of standardisation.

## Recommendation and justification

Proceed with option A given its simplicity and increased likelihood of support from HSM vendors.

Option B can always remain an alternative in the long-term, in case there is an HSM vendor that cannot provide an API for option A for some very specific reason. The proposed overall solution can easily be adapted to integrate with Option B as well.