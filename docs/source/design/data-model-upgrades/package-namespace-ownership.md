# Package namespace ownership

This design document outlines a new Corda feature that allows a compatibility zone to give ownership of parts of the Java package namespace to certain users.

"*There are only two hard problems in computer science: 1. Cache invalidation, 2. Naming things, 3. Off by one errors*"



## Background

Corda implements a decentralised database that can be unilaterally extended with new data types and logic by its users, without any involvement by the closest equivalent we have to administrators (the "zone operator"). Even informing them is not required.

This design minimises the power zone operators have and ensures deploying new apps can be fast and cheap - it's limited only by the speed with which the users themselves can move. But it introduces problematic levels of namespace complexity which can make programming securely harder than in regular non-decentralised programming. 

#### Java namespaces

A typical Java application, seen from the JVM level, has a flat namespace in which a single string name binds to a single class. In object oriented programming a class defines both a data structure and the code used to enforce various invariants like "a person's age may not be negative", so this allows a developer to reason about what the identifier `com.example.Person` really means throughout the lifetime of his program. 

More complex Java applications may have a nested namespace using classloaders, thus inside a JVM a class is actually a pair of (classloader pointer, class name) and this can be used to support tricks like having two different versions of the same class in use simultaneously. The downside is more complexity for the developer to deal with. When things get mixed up this can surface (in Java 8) as nonsensical error messages like "com.example.Person cannot be casted to com.example.Person". In Java 9 classloaders were finally given names so these errors make more sense.

#### Corda namespaces

Corda has an even more complex namespace. States are identified using ordinary Java class names because programmers have to be able to write source code that works with them. But because there's no centralised coordination point and Java package namespacing is just a convention, the true name of a class when loaded and run includes the hash of the JAR that defines it. These "attached JARs" are a property of a *transaction* not a state, thus a state only has specific meaning within the context of a transaction. In this way the code that defines a state/contract can evolve over time without needing to constantly edit the ledger when new versions are released. We call this type of just-in-time selection of final logic when building a transaction as "implicit upgrades". The assumption is that data may live for much longer than code.

The node maps attachments to class paths to classloaders, which ensures that the JVM's namespace is synced with the ledger during verification and it will interpret names relative to the transaction being processed. States outside of a transaction are bound to these true "decentralised names" using an indirection intended to allow for smooth migration between versions called *contract constraints*. 

A constraint specifies what attachments can be used to implement the state's class name, with differing levels of ambiguity. Therefore a transaction's attachments must satisfy the included state's constraints. This scheme is somewhat complex, but gives developers freedom to combine multiple applications together in an agile environment where software is constantly changing, might be malicious and where trust relationships can be complex.

The problem is that working with these sorts of sophisticated namespaces is hard, including for platform developers. 

As the Java 8 error message example shows, even implementors of complex namespaces often don't get every detail right. Corda expects developers to understand that a `com.megacorp.token.MegaToken` class they find in a transaction or deserialise out of the vault might *not* have been the same as the `com.megacorp.token.MegaToken` class they had in mind when writing a program. It might be a legitimate later version, but it may also be a totally different class in the worst case written by an adversary e.g. one that gives the adversary the right to spend the token.

## Goals

* Provide a way to reduce the complexity of naming and working with names in Corda by allowing for a small amount of centralisation, balanced by a reduction in developer mental load.
* Keep it optional for both zones and developers.
* Allow most developers to work just with ordinary Java class names, without needing to consider the complexities of a decentralised namespace.

## Non-goals

* Directly make it easier to work with "decentralised names". This might come later.

## Design

To make it harder to accidentally write insecure code, we would like to support a compromise configuration in which a compatibility zone can publish a map of Java package namespaces to public keys. An app/attachment JAR may only define a class in that namespace if it is signed by the given public key. Using this feature would make a zone  slightly less decentralised, in order to obtain a significant reduction in mental overhead for developers.

Example of how the network parameters would be extended, in pseudo-code:

```kotlin
data class JavaPackageName(name: String) {
	init { /* verify 'name' is a valid Java package name */ }    
}

data class NetworkParameters(
    ...
    val packageOwnership: Map<JavaPackageName, PublicKey>
)
```

Where the `PublicKey` object can be any of the algorithms supported by signature constraints. The map defines a set of dotted package names like `com.foo.bar` where any class in that package or any sub-package of that package is considered to match (so `com.foo.bar.baz.boz.Bish` is a match but `com.foo.barrier` does not).

When a class is loaded from an attachment or application JAR signature checking is enabled. If the package of the class matches one of the owned namespaces, the JAR must be have enough signatures to satisfy the PublicKey (there may need to be more than one if the PublicKey is composite).

Please note the following:

* It's OK to have unsigned JARs.
* It's OK to have JARs that are signed, but for which there are no claims in the network parameters.
* It's OK if entries in the map are removed (system becomes more open). If entries in the map are added, this could cause consensus failures if people are still using old unsigned versions of the app.
* The map specifies keys not certificate chains, therefore, the keys do not have to chain off the identity key of a zone member. App developers do not need to be members of a zone for their app to be used there.

From a privacy and decentralisation perspective, the zone operator *may* learn who is developing apps in their zone or (in cases where a vendor makes a single app and thus it's obvious) which apps are being used. This is not ideal, but there are mitigations:

* The privacy leak is optional.
* The zone operator still doesn't learn who is using which apps.
* There is no obligation for Java package namespaces to correlate obviously to real world identities or products. For example you could register a trivial "front" domain and claim ownership of that, then use it for your apps. The zone operator would see only a codename.

#### Claiming a namespace

The exact mechanism used to claim a namespace is up to the zone operator. A typical approach would be to accept an SSL certificate with the domain in it as proof of domain ownership, or to accept an email from that domain as long as the domain is using DKIM to prevent from header spoofing.

#### The vault API

The vault query API is an example of how tricky it can be to manage truly decentralised namespaces. The `Vault.Page` class does not include constraint information for a state. Therefore, if a generic app were to be storing states of many different types to the vault without having the specific apps installed, it might be possible for someone to create a confusing name e.g. an app created by MiniCorp could export a class named `com.megacorp.example.Token` and this would be mapped by the RPC deserialisation logic to the actual MegaCorp app - the RPC client would have no way to know this had happened, even if the user was correctly checking, which it's unlikely they would.

The `StateMetadata` class can be easily extended to include constraint information, to make safely programming against a decentralised namespace possible. As part of this work this extension will be made.

But the new field would still need to be used - a subtle detail that would be easy to overlook. Package namespace ownership ensures that if you have an app installed locally on the client side that implements `com.megacorp.example` , then that code is likely to match closely enough with the version that was verified by the node.





