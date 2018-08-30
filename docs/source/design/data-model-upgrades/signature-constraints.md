# Signature constraints

This design document outlines an additional kind of *contract constraint*, used for specifying inside a transaction what the set of allowable attached contract JARs can be for each state.

## Background

Contract constraints are a part of how Corda manages application upgrades. There are two kinds of upgrade that can be applied to the ledger:

* Explicit
* Implicit

An *explicit* upgrade is when a special kind of transaction is used, the *contract upgrade transaction*, which has the power to suspend normal contract execution and validity checking. The new contract being upgraded-to must be willing to accept the old state and can replace it with a new one. Because this can cause arbitrary edits to the ledger, every participant in a state must sign the contract upgrade transaction for it to be considered valid. 

Note that in the case of single-participant states whilst you could unilaterally replace a token state with a different state, this would be a state controlled by an application that other users wouldn't recognise, so you cannot transmute a token into a private contract with yourself then transmute it back, because contracts will only upgrade states they created themselves.

An *implicit* upgrade is when the creator of a state has pre-authorised upgrades, quite possibly including versions of the app that didn't exist when the state was first authored. Implicit upgrades don't require a manual approval step - the new code can start being used whenever the next transaction for a state is needed, as long as it meets the state's constraint.

Our current set of constraints is quite small. We support:

* `AlwaysAcceptContractConstraint` - any attachment can be used, effectively this disables ledger security.
* `HashAttachmentContractConstraint` - only an attachment of the specified hash can be used. This is the same as Bitcoin or Ethereum and means once the state is created, the code is locked in permanently.
* `WhitelistedByZoneContractConstraint` - the network parameters contains a map of state class name to allowable hashes for the attachments. 

The last constraint allows upgrades 'from the future' to be applied, without disabling ledger security. However it is awkward to use, because any new version of any app requires a new set of network parameters to be signed by the zone operator and accepted by all participants, which in turn requires a node restart.

The problems of `WhitelistedByZone` were known at the time it was developed, however, the feature was implemented anyway to reduce schedule slip for the Corda 3.0 release, whilst still allowing some form of application upgrade.

We would like a new kind of constraint that is more convenient and decentralised whilst still being secure.


## Goals

* Improve usability by eliminating the need to change the network parameters.

* Improve decentralisation by allowing apps to be developed and upgraded without the zone operator knowing or being able to influence it.

* Eventually, phase out zone whitelisting constraints.


## Non-goals

* Preventing downgrade attacks. Downgrade attack prevention will be tackled in a different design effort.
* Phase out of hash constraints. If malicious app creators are in the users threat model then hash constraints are the way to go.
* Handling the case where third parties re-sign app jars.

## Design details

We propose being able to constrain to any attachments signed by a specified set of keys.

This satisfies the usability requirement because the creation of a new application is as simple as invoking the `jarsigner` tool that comes with the JDK. This can be integrated with the build system via a Gradle or Maven task. For example, Gradle can use jarsigner via [the signjar task](https://ant.apache.org/manual/Tasks/signjar.html) ([example](https://gist.github.com/Lien/7150434)). 

This also satisfies the decentralisation requirement, because app developers can sign apps without the zone operator's involvement or knowledge.

Using JDK style JAR code signing has several advantages over rolling our own:

* Although a signing key is required, this can be set up once. It can be protected by a password, or Windows/Mac built in keychain security, a token that supports PIN /biometrics or an HSM. All these options are supported out of the box by the Java security architecture.
* JARs can be signed multiple times by different entities. The nature of this process means the signatures can be combined easily - there is no ordering requirement or complex collaboration tools needed. By implication this means that a signature constraint can use a composite key.
* APIs for verifying JAR signatures are included in the platform already.
* File hashes can be checked file-at-a-time, so random access is made easier e.g. from inside an SGX enclave.
* Although Gradle can make reproducible JARs quite easily, JAR signatures do not include irrelevant metadata like file ordering or timestamps, so they are robust to being unpacked and repacked.
* The signature can be timestamped using an RFC compliant timestamping server. Our notaries do not currently implement this protocol, but they could.
* JAR signatures are in-lined to the JAR itself and do not ride alongside it. This is a good fit for our current attachments capabilities.

There are also some disadvantages:

* JAR signatures do *not* have to cover every file in the JAR. It is possible to add files to the JAR later that are unsigned, and for the verification process to still pass, as verification is done on a per-file basis. This is unintuitive and requires special care.
* The JAR verification APIs do not validate that the certificate chain in the JAR is meaningful. Therefore you must validate the certificate chain yourself in every case where a JAR is being verified.
* JAR signing does not cover the MANIFEST.MF file or files that start with SIG- (case INsensitive). Storing sensitive data in the manifest could be a problem as a result.

### Data structures

The proposed data structure for the new constraint type is as follows:

```kotlin
data class SignatureAttachmentConstraint(
    val key: PublicKey
) : AttachmentConstraint
```

Therefore if a state advertises this constraint, along with a class name of `com.foo.Bar` then the definition of Bar must reside in an attachment with signatures sufficient to meet the given public key. Note that the `key` may be a `CompositeKey` which is fulfilled by multiple signers. Multiple signers of a JAR is useful for decentralised administration of an app that wishes to have a threat model in which one of the app developers may go bad, but not a majority of them. For example there could be a 2-of-3 threshold of {app developer, auditor, R3} in which R3 is legally bound to only sign an upgrade if the auditor is unavailable e.g. has gone bankrupt. However, we anticipate that most constraints will be one-of-one for now.

We will add a `signers` field to the `ContractAttachment` class that will be filled out at load time if the JAR is signed. The signers will be computed by checking the certificate chain for every file in the JAR, and any unsigned files will cause an exception to be thrown.

### Transaction building

The `TransactionBuilder` class can select the right constraint given what it already knows. If it locates the attachment JAR and discovers it has signatures in it, it can automatically set an N-of-N constraint that requires all of them on any states that don't already have a constraint specified. If the developer wants a more sophisticated constraint, it is up to them to set that explicitly in the usual manner.

### Tooling and workflow

The primary tool required is of course `jarsigner`. In dev and integration test modes, the node will ignore missing signatures in attachment JARs and will simply log a warning if no signature is present.

To verify and print information about the signatures on a JAR, the `jarsigner` tool can be used again. In addition, we should add some new shell commands that do the same thing, but for a given attachment hash or transaction hash - these may be useful for debugging and analysis. Actually a new shell command should cover all aspects of inspecting attachments - not just signatures but what's inside them, simple way to save them to local disk etc.

### Key structure

There are no requirements placed on the keys used to sign JARs. In particular they do not have to be keys used on the Corda ledger, and they do not need a certificate chain that chains to the zone root. This is to ensure that app JARs are not specific to any particular zone. Otherwise app developers would need to go through the on-boarding process for a zone and that may not always be necessary or appropriate.

The certificate hierarchy for the JAR signature can be a single self-signed cert. There is no need for the key to present a valid certificate chain.

### Third party signing of JARs

Consider an app written and signed by the hypothetical company MiniCorp™. It allows users to issue tokens of some sort. An issuer called MegaCorp™ decides that they do not completely trust MiniCorp to create new versions of the app, and they would like to retain some control, so they take the app jar and sign it themselves. Thus there are now two JARs in circulation for the same app.

Out of the box, this situation will break when combining tokens using the original JAR and tokens from MegaCorp into a single transaction. The `TransactionBuilder` class will fail because it'll try to attach both JARs to satisfy both constraints, yet the JARs define classes with the same name. This violates the no-overlap rule (the no-overlap rule doesn't check for whether the files are actually identical in content).

For now we will make this problem out of scope. It can be resolved in a future version of the platform.

There are a couple of ways this could be addressed:

1. Teach the node how to create a new JAR by combining two separately signed versions of the same JAR into a third.
2. Alter the no-overlap rule so when two files in two different attachments are identical they are not considered to overlap.

### Upgrading from other constraints

We anticipate that signature constraints will probably become the standard type of constraint, as it strikes a good balance between security and rigidity. 

The "explicit upgrade" mechanism using dedicated upgrade transactions already exists and can be used to move data from old constraints to new constraints, but this approach suffers from the usual problems associated with this form of upgrade (requires signatures from every participant, creating a new tx, manual approval of states to be upgraded etc).

Alternatively, network parameters can be extended to support selective overrides of constraints to allow such upgrades in an announced and opt-in way. Designing such a mechanism is out of scope for the first cut of this feature however.

## Alternatives considered

### Out-of-line / external JAR signatures

One obvious alternative is to sign the entire JAR instead of using the Java approach of signing a manifest file that in turn contains hashes of each file. The resulting signature would then ride alongside the JAR in a new set of transaction fields.

The Java approach of signing a manifest in-line with the JAR itself is more complex, and complexity in cryptographic operations is rarely a good thing. In particular the Java approach means it's possible to have files in the JAR that aren't signed mixed with files that are. This could potentially be a useful source of flexibility but is more likely to be a foot-gun: we should reject attachments that contain a mix of signed and unsigned files. 

However, signing a full JAR as a raw byte stream has other downsides:

* Would require a custom tool to create the detached signatures. Then it'd require new RPCs and more tools to upload and download the signatures separately from the JARs, and yet more tools to check the signatures. By bundling the signature inside the JAR, we preserve the single-artifact property of the current system, which is quite nice.
* Would require more fields to be added to the WireTransaction format, although we'll probably have to bite this bullet as part of adding attachment metadata eventually anyway.
* The signature ends up covering irrelevant metadata like file modification timestamps, file ordering, compression levels and so on. However, we need to move the ecosystem to producing reproducible JARs anyway for other reasons.
* JAR signature metadata is already exposed via the Java API, so attachments that are not covered by a constraint e.g. an attachment with holiday calendar text files in it, can also be signed, and contract code could check those signatures in the usual documented way. With out-of-line signatures there'd need to be custom APIs to do this.
* Inline JAR signatures have the property that they can be checked on a per file basis. This is potentially useful later for SGX enclaves, if they wish to do random access to JAR files too large to reasonably fit inside the rather restricted enclave memory environment.

### Package name constraints

Our goal is to communicate "I want an attachment created by party/parties $FOO". The obvious way to do this is specify the party in the constraint. But as part of other work we are considering introducing the concept of package hierarchy ownership - so `com.foobar.*` would be owned by the Foo Corporation of London and this ownership link between namespace glob and `Party` would be specified in the network parameters. 

If such an indirection were to be introduced then you could make the constraint simpler - it wouldn't need any contents at all. Rather, it would indicate that any attachment that legitimately exported the package name of the contract classname would be accepted. It'd be up to the platform to check that the signature on the JAR was by the same party that is listed in the network parameters as owning that package namespace.

There are some further issues to think through here:

1. Is this a fourth type of constraint (package name constraint) that we should support along with the other three? Or is it actually just a better design and should subsume this work?
2. Should it always be the package name of the contract class, or should it specify a package glob specifically? If so what happens if the package name of the contract class and the package name of the constraint don't match - is it OK if the latter is a subset of the former?
3. Indirecting through package names increases centralisation somewhat, because now the zone operator has to agree to you taking ownership of a part of the namespace. This is also a privacy leak, it may expose what apps are being used on the network. *However* what it really exposes is application *developers* and not actual apps, and the zone op doesn't get to veto specific apps once they approved an app developer. More problematically unless an additional indirection is added to the network parameters, every change to the package ownership list requires a "hard fork" acceptance of new parameters.


### Using X.500 names in the constraint instead of PublicKey

We advertise a `PublicKey` (which may be a `CompositeKey`) in the constraint and *not* a set of `CordaX500Name` objects. This means that apps can be developed by entities that aren't in the network map (i.e. not a part of your zone), and it enables threshold keys, *but* the downside is there's no way to rotate or revoke a compromised key beyond adjusting the states themselves. We lose the indirection-through-identity.

We could introduce such an indirection. This would disconnect the constraint from a particular public key. However then each zone an app is deployed to requires a new JAR signature by the creator, using a certificate issued by the zone operator. Because JARs can be signed by multiple certificates, this is OK, a JAR can be resigned N times if it's to be used in N zones. But it means that effectively zone operators get a power of veto over application developers, increasing centralisation and it increases required logistical efforts. 

In practice, as revoking on-ledger keys is not possible at the moment in Corda, changing a code signing key would require an explicit upgrade or the app to have a command that allows the constraint to be changed.