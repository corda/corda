A bit of context around how transactions are verified and what changed from Corda 3 to 4
-----------------------------------------------------------------------------------------

Corda transactions evolve input states into output states. A state is a data structure containing: the actual fact (that is expressed as a strong typed serialized java object) and the logic (contract) that needs to verify a transition to and from this state. The logic is expressed as a Java class name and one (or multiple) attachment jars that contain that class and all it's dependencies.
Given that anyone can create `.java` files, compile and bundle them in a jar, we have a mechanism in place to complement the class name that allows the State to specify exactly what code is valid.
In Corda 4, for example, the state can say: "I'm ok to be spent if the transaction is verified by 'com.mycompany.mycontract.MyContract' as long as the jar containing this contract is signed by companyA".
This approach combines security and usability allowing the code to evolve and developers to fix bugs, but also gives confidence when verifying a transaction chain as the code that verifies it is signed by the same entity.

Implementation details:
 - Transactions reference the code that verifies them as a list of attachments. Each attachment is a SHA256 of a jar file. These jar files can be downloaded from peers as part of tx resolution, or directly installed locally.
 - Before actually running any verification, the platform checks that for each state there is one attachment in the list that contains the contract class name, and also that this `Contract Attachment` satisfies the constraint of that state. ( E.g.: in case of the signature constraint - the JAR is signed by the key specified in the state)
 For this mechanism to work effectively, it's necessary that each state is able to identify the attachment that is controlling it.
 - Now that it's validated that the transaction was correctly formed, the smart contract code for each state can be executed to validate the transitions. This is done by creating an `AttachmentsClassloader` from all the attachments listed by the transaction, deserialising and running the contract verification code against the transaction in that classloader. (In he future, we will execute this into a deterministic sandbox - the DJVM)


In theory a simple transaction swapping `Apples` for `Oranges` would contain 2 attachments: one for the Apples contract and one for the Oranges contract.

Things get more complicated if the `Apples` contract uses an external library which gets called during verification. 

There are 2 options to achieve this:
 1. Bundle the external library with the `Apples` code. Basically create a fat-jar that includes all dependencies (and sign over it). 
 2. Add the dependency as another attachment to the transaction.

The problem with approach 2 is that it is insecure. As stated previously anyone can create a jar, so a malicious actor could just create his own version of some dependency code. This would allow him to change the intended behavior of the contract that depends on this code to his advantage. 
There are ways to make this option secure and future versions of Corda may explore them and implement them. But, today, in Corda 4, such an approach is not supported

The only approach that is available currently is 1 - Bundling dependencies.

There are a couple of caveats to this approach as well:

a. If multiple CorDapp developers independently bundle some popular library (like guava) into their contract, this can cause problems when building the `AttachmentsClassloader`. (see no-overlap rule doc)
The workaround is for developers to shade dependencies under their own namespace. This would avoid clashes.
The problem could be mitigated if collectively developers agree on bundling some popular version of guava, and then releasing a new version of their CorDapp from time to time.  The node will be then able to select compatible versions of "apples" and "oranges". This works because the no-overlap rule allows the same file to be in multiple attachments if it is the same file.

b. CorDapp depending on other CorDapps. 

This is where it gets really tricky.

Let's take as an example the finance CorDapp that is shipped with Corda as a sample. 
(Note: As it is just a sample, it is signed by R3's _development key_, which the node is explicitly configured - but overridable - to blacklist by default in production in order to avoid you inadvertently going live without having first determined the right approach for your solution. But it is illustrative to other reusable CorDapps that might get developed.)

The finance CorDapp comes with some handy utilities that can be used by code in some other CorDapps, some abstract base types like `OnLedgerAsset`, but also comes with it's own ready-to-use Contracts like: Cash, Obligation and Commercial Paper.

This creates a tension as it gives the finance CorDapp a dual role: "library" AND "normal CorDapp".
If it were just a library it could be fat-jarred as a normal dependency (and all the caveats - due to the no-overlap rule described above - would apply).
If it were a CorDapp that is used as a main `Contract attachment` to verify transitions it would have to be attached to the transaction and checked against the constraints of the states that it controls.

It can't be both in a single transaction though.

Why?

Imagine you are swapping `Apples` for `Cash` this time, but the `Apples` contract depends on the finance CorDapp - for example it extends `OnLedgerAsset`.
A transaction is formed and 2 attachments are added: the finance jar signed by R3's key and the apples jar signed by `CompanyA` (that bundles finance).  ( let's ignore the fact that it's R3's development key for the purpose of this exercise.)
When the transaction is verified and the platform has to decide which attachment jar to verify against which state constraint there is an ambiguity as both jars could be candidates for the constraint of the `Cash` states. 
To avoid any ambiguity we have specifically enforced that there can be only 1 attachment for the relevant contracts of the transaction. (The reason for this is to avoid a number of subtle attacks.)
	
Another problem with this approach is that it introduces namespace confusion. If someone decides to issue `net.corda.finance.contracts.asset.Cash` using the `apples` contract that bundled the finance app it would be a completely different state from one that was issued with the R3 controlled contract. This is because the code could evolve in completely different directions and users of that state who don't check the constraint would be misled.

In Corda 4, to help avoid this type of confusion, we introduced the concept of Package Namespace Ownership (see doc). It allows companies to claim different namespaces, and everyone on the network, if they encounter a class in that package that is not signed by the registered key, know it is invalid.

Given the above there are 4 possible solutions for reusable CorDapps.

1. Partial bundling:  Only bundle the exact classes you need in your contract. Basically leave out the ready-to-use contracts. This would reduce the problem of a reusable CorDapp (described above) to that of a normal library ( with all the caveats around the no-overlap rule)
2. Shading. This means that there would be no namespace collision, but the downside is that when extending some base interface the contract that extends would lose the relation with other implementations. 
3. Package ownership: `net.corda.finance.contracts.asset` would be claimed by R3. This would give confidence to all participants that if a jar with this package is attached to a transaction it must be created by the original developer which was deemed as trustworthy by the zone operator.
4. Manually attaching the actual library-Cordapp to the transaction. The contract that uses it is responsible to perform an equivalent of an Attachment constraint to make sure that a malicious party did not attach a "customized" jar that alters the intended verification logic.


What changed from Corda 3 to Corda 4
-------------------------------------

In Corda 3 transactions were verified inside the System Classloader that contained all the installed CorDapps.
If we consider the example from above with the `Apples` contract that depends on finance, the `Apples` developer could have just released the apple specific code ( without bundling in the dependency on finance or attaching it to the transaction ) and rely on the fact that finance would be on the classpath during verification.

This means that in Corda 3 nodes could have formed `valid` transactions that were not entirely self-contained. In Corda 4 where we have moved the transaction verification inside the `AttachmentsClassloader` these transactions would fail with ClassNotFound exceptions (e.g.: as the finance jar would not be available).

These transactions need to be considered valid in Corda 4 and beyond though, so the fix we added for this was to look for a `trusted` attachment in our storage that contains the missing code and use that for validation.
This fix is in the spirit of the original transaction and is secure because the chosen code must have been vetted and whitelisted first.

Going forward, the upgrade path for developers using reusable CorDapps (like finance or tokens sdk) is to attach the dependency to the transaction and in their contract verify that the correct contract is attached ( which is basically a cascade of the attachment constraint check).
 



TLDR:
-----

Q: Will my transactions created in Corda V3 still verify in Corda V4 even if my cordapp depends on another cordapp?
A: Yes. Corda 4 maintains backwards compatibility for existing data. There should be no special steps that node operators need to make.

Q: If my cordapp depends on the finance app how should I proceed when I release a new version of my code and want to benefit from all the Corda 4 features.
A: Make sure that your users install or whitelist the finance contracts jar.  (If they install the contracts they also need to install the finance workflows.)
In your build file, you need to depend on finance as a `cordapp` dependency. 
In your flow, when building the transaction, just add this line: `builder.addAttachment(hash_of_finance_v4_contracts_jar)` .
And in your contract just verify that: 
```
    requireThat {
        "the correct finance jar was attached to the transaction" using (tx.attachments.find {it.id == hash_of_finance_v4_contracts_jar} !=null)
    }
```

Q: If I am developing a reusable cordapp that contains both contracts and utilities, how would my clients use it?
A: Same as for finance. 
Or, if you sign your cordapp, you can distribute your public key, which users would embed in their contract and then check the attachment like this:
```
    requireThat {
        "the correct my_reusable_cordapp jar was attached to the transaction" using (tx.attachments.find {SignatureAttachmentConstraint(my_public_key).isSatisfiedBy(it)} !=null)
    }
```

Q: If I am developing a cordapp that depends on an external library do I need to do anything special?
A: Same as before just add a `compile` dependency to the library, which will bundle it with your cordapp.








