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

Corda faces an extension of the Java namespace problem - we have a global namespace in which malicious adversaries might be choosing names to be deliberately confusing. Nothing forces an app developer to follow the standard conventions for Java package or class names - someone could make an app that uses the same class name as one of your own apps. Corda needs to keep these two different classes, from different origins, separated.

On the core ledger this is done by associating each state with an _attachment_. The attachment is the JAR file that contains the class files used by states. To load a state, a classloader is defined that uses the attachments on a transaction, and then the state class is loaded via that classloader.

With this infrastructure in place, the Corda node and JVM can internally keep two classes that share the same name separated. The name of the state is, in effect, a list of attachments (hashes of JAR files) combined with a regular class name.



#### Namespaces and versioning

Names and namespaces are a critical part of how platforms of any kind handle software evolution. If component A is verifying the precise content of component B, e.g. by hashing it, then there can be no agility - component B can never be upgraded. Sometimes this is what's wanted. But usually you want the indirection of a name or set of names that stands in for some behaviour. Exactly how that behaviour is provided is abstracted away behind the mapping of the namespace to concrete artifacts.

Versioning and resistance to malicious attack are likewise heavily interrelated, because given two different codebases that export the same names, it's possible that one is a legitimate upgrade which changes the logic behind the names in beneficial ways, and the other is an imposter that changes the logic in malicious ways. It's important to keep the differences straight, which can be hard because by their very nature, two versions of the same app tend to be nearly identical.



#### Namespace complexity

Reasoning about namespaces is hard and has historically led to security flaws in many platforms.

Although the Corda namespace system _can_ keep overlapping but distinct apps separated, that unfortunately doesn't mean that everywhere it actually does. In a few places Corda does not currently provide all the data needed to work with full state names, although we are adding this data to RPC in Corda 4. 

Even if Corda was sure to get every detail of this right in every area, a full ecosystem consists of many programs written by app developers - not just contracts and flows, but also RPC clients, bridges from internal systems and so on. It is unreasonable to expect developers to fully keep track of Corda compound names everywhere throughout the entire pipeline of tools and processes that may surround the node: some of them will lose track of the attachments list and end up with only a class name, and others will do things like serialise to JSON in which even type names go missing.

Although we can work on improving our support and APIs for working with sophisticated compound names, we should also allow people to work with simpler namespaces again - like just Java class names. This involves a small sacrifice of decentralisation but the increase in security is probably worth it for most developers.

## Goals

* Provide a way to reduce the complexity of naming and working with names in Corda by allowing for a small amount of centralisation, balanced by a reduction in developer mental load.
* Keep it optional for both zones and developers.
* Allow most developers to work just with ordinary Java class names, without needing to consider the complexities of a decentralised namespace.

## Non-goals

* Directly make it easier to work with "decentralised names". This can be a project that comes later.

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





