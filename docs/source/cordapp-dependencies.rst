CorDapp dependency handling
===========================


Some context around how corda transactions are verified
-------------------------------------------------------

Corda transactions evolve input states into output states. A state is a data structure containing: the actual data fact (that is expressed as a
strong typed serialized java object) and a reference to the logic (contract) that needs to verify a transition to and from this state. The logic is expressed
as a Java class name and a contract constraint. (read more in: :doc:`api-contract-constraints`)

Very briefly, given that anyone can create `.java` files, compile and bundle them in a JAR, we have a mechanism in place to complement the class name, that
allows the State to specify exactly what code is valid.
In Corda 4, for example, the state can say: "I'm ok to be spent if the transaction is verified by `com.mycompany.mycontract.MyContract` as
long as the JAR containing this contract is signed by companyA".
This approach combines security and usability allowing the code to evolve and developers to fix bugs. It also gives confidence when verifying
a transaction chain because the code that verifies it is signed by the same entity.

Implementation details:

* Transactions reference the code that verifies them as a list of attachment ids. Each attachment id is a SHA256 of a JAR file.
  These JAR files can be downloaded from peers as part of tx resolution, or directly installed locally.
* Before actually running any verification, the platform checks that for each state there is one attachment in the list that contains the contract class name,
  and also that this `Contract Attachment` satisfies the constraint of that state. ( E.g.: in case of the signature constraint - the JAR is
  signed by the key specified in the state).
  For this mechanism to work effectively, it is necessary that each state is able to identify the attachment that is controlling it.
* Now that it's validated that the transaction was correctly formed, the smart contract code for each state can be executed to validate the transitions.
  This is done by creating an `AttachmentsClassloader` from all the attachments listed by the transaction, deserialising the binary representation of the transaction,
  and running the contract verification code against the transaction in that classloader. (In the future, this step will run in a deterministic sandbox - the DJVM)


In the simplest case, a transaction swapping `Apples` for `Oranges` would contain 2 attachments: one for the Apples contract and one for the Oranges contract.

Things get more complicated though if the `Apples` contract uses an external library which gets called during verification.

There are 2 options to achieve this:

 1. Bundle the external library with the `Apples` code. Basically create a fat-JAR that includes all dependencies (and optionally sign over it).
 2. Add the dependency as another attachment to the transaction.

The problem with approach 2 is that it is insecure without adding additional security checks. As stated previously anyone can create a JAR,
so a malicious actor could just create his own version of some dependency code. This would allow him to change the intended behavior of the
contract that depends on this code to his advantage.
There are ways to make this option secure and future versions of Corda may explore them and implement them.
If a CorDapp developer decides to go for this approach they can write custom contract code to perform dependency validity checks as the contract has access to the `LedgerTransaction`.

The approach that we recommend is bundling dependencies - if possible with shading.
But it is really up to the CorDapp developers who can choose what they prefer.

There are a couple of caveats to this approach as well:

* If multiple CorDapp developers independently bundle some popular library (like `guava`) into their contract, this can cause problems when building
  the `AttachmentsClassloader` (see no-overlap rule doc -once created -TODO).
  The obvious workaround is for developers to shade dependencies under their own namespace, which would avoid clashes. This comes with some drawbacks though,
  because sometimes it's desired to be able to cast to some common superclass.
  The problem could also be mitigated if collectively developers agree that when bundling some popular library they need to release a new version of their
  CorDapp from time to time to keep up with each other.  The node will then be able to select compatible versions of "apples" and "oranges".
  This works because the no-overlap rule allows a class file to live in multiple attachments if all the versions are equal.

* CorDapp depending on other CorDapps. This is a more advanced scenario and requires care.

.. note:: Currently the `cordapp` gradle plugin that ships with Corda only supports bundling a dependency fully unshaded, by declaring it as a `compile` dependency.


CorDapp depending on other CorDapp(s)
-------------------------------------

Let's take as an example the `finance` CorDapp that is shipped with Corda as a sample.

.. note:: As it is just a sample, it is signed by R3's development key, which the node is explicitly configured - but overridable - to blacklist
  by default in production in order to avoid you inadvertently going live without having first determined the right approach for your solution.
  But it is illustrative to other reusable CorDapps that might get developed.

The finance CorDapp brings some handy utilities that can be used by code in other CorDapps, some abstract base types like `OnLedgerAsset`,
but also comes with its own ready-to-use contracts like: `Cash`, `Obligation` and `Commercial Paper`.

This creates a tension as it gives the finance CorDapp a dual role: `reusable library` AND `normal CorDapp that can be used directly to issue and consume states`.

If it were just a library it could be bundled as a normal dependency (and all the caveats - due to the no-overlap rule described above - would apply).

If it were a CorDapp that was used as a main `Contract attachment` to verify transitions it would have to be attached to the transaction and checked against
the constraints of the states that it controls.

It can't be both in a single transaction though.

Why?

Imagine you are selling `Apples` for `Cash` this time, but the `Apples` contract depends on the finance CorDapp - for example it extends `OnLedgerAsset`.
A transaction is formed and 2 attachments are added: the finance JAR signed by R3's key and the apples JAR signed by `CompanyA` (that bundles finance).

For the purpose of this exercise let's ignore the fact that the JAR is signed by R3's development key.

When this transaction is verified and the platform has to decide which attachment JAR to verify against which state constraint there is an ambiguity
as both JARs could be candidates for the constraint of the `Cash` states.
To avoid any ambiguity we have specifically enforced that there can be only 1 attachment for the relevant contracts of the transaction.

The main problem we have in this case is that a node would not be able to create a valid `Apples` for `Cash` transaction.
	
Another problem with this approach is that it introduces namespace confusion. If someone decides to issue `net.corda.finance.contracts.asset.Cash`
using the `apples` contract that bundles the finance app it would be a completely different state from one that was issued with the R3 controlled contract.
This is because the code could evolve in completely different directions and users of that state who don't check the constraint would be misled.

In Corda 4, to help avoid this type of confusion, we introduced the concept of Package Namespace Ownership (see ":doc:`design/data-model-upgrades/package-namespace-ownership`").
It allows companies to claim different namespaces, and everyone on the network, if they encounter a class in that package that is not signed by the registered key, know it is invalid.


Given the above there are 4 possible solutions for reusable CorDapps:

 1. Partial bundling:  Only bundle the exact classes you need in your contract. Basically leave out the ready-to-use contracts. This would reduce
    the problem of a reusable CorDapp (described above) to that of a normal library ( with all the caveats around the no-overlap rule).

 2. Shading: This means that there would be no namespace collision, but the downside is that when extending some base interface the contract that
    extends would lose the relation with other implementations.

 3. Package ownership: `net.corda.finance.contracts.asset` would be claimed by R3. This would give confidence to all participants that if a JAR
    with this package is attached to a transaction it must be created by the original developer which was deemed as trustworthy by the zone operator.

 4. Manually attaching the actual library-Cordapp to the transaction. The contract that uses it is responsible to perform an equivalent of an
    Attachment constraint to make sure that a malicious party did not attach a "customized" JAR that alters the intended verification logic.


The preferred approach can be selected by the developers of the CorDapp, but the recommended to go for 4 - manually attaching and checking.

We also recommend that companies claim their package so the best approach is to combine 3 and 4. By actually checking in the contract code that
the expected dependency is present there is no possibility for unexpected behaviour.


Changes between version 3 to version 4 of Corda
-----------------------------------------------

In Corda 3 transactions were verified inside the System Classloader that contained all the installed CorDapps.
If we consider the example from above with the `Apples` contract that depends on finance, the `Apples` developer could have just released
the `Apples` specific code ( without bundling in the dependency on finance or attaching it to the transaction ) and rely on the fact that
finance would be on the classpath during verification.

This means that in Corda 3 nodes could have formed `valid` transactions that were not entirely self-contained. In Corda 4, because we
moved transaction verification inside the `AttachmentsClassloader` these transactions would fail with ClassNotFound exceptions
(in the example above the finance jar would not be available as it wasn't explicitly added).

These transactions need to be considered valid in Corda 4 and beyond though, so the fix we added for this was to look for a `trusted` attachment
in the current node storage that contains the missing code and use that for validation.
This fix is in the spirit of the original transaction and is secure because the chosen code must have been vetted and whitelisted first by the node operator.

The transition to the `AttachmentsClassloader` is one more step towards the full design of Corda.

This change also affects testing as the test classloader no longer contains the CorDapps.


FAQ
---

Q: Will my transactions created in Corda V3 still verify in Corda V4 even if my CorDapp depends on another CorDapp and I haven't bundled it nor added it to the attachments?

* A: Yes. Corda 4 maintains backwards compatibility for existing data. There should be no special steps that node operators need to make.


Q: If my CorDapp depends on the finance app how should I proceed when I release a new version of my code and want to benefit from all the Corda 4 features?

* A: Make sure that your users install or whitelist the unsigned finance contracts JAR.  (If they actually install the contracts JAR they also need to install the workflows JAR.)
 In your build file, you need to depend on finance contracts as a `cordapp` dependency.
 In your flow, when building the transaction, just add this line: `builder.addAttachment(hash_of_finance_v4_contracts_jar)`.
 And in your contract just verify that:

.. sourcecode:: kotlin

    requireThat {
        "the correct finance jar was attached to the transaction" using (tx.attachments.find {it.id == hash_of_finance_v4_contracts_jar} !=null)
    }


Q: If I am developing a reusable CorDapp that contains both contracts and utilities, how would my clients use it?

* A: Same as for finance ( see previous question)
Or, even better, if you sign your CorDapp, you can distribute your public key, which users would embed in their contract and then check the attachment like this:

.. sourcecode:: kotlin

    requireThat {
        "the correct my_reusable_cordapp jar was attached to the transaction" using (tx.attachments.find {SignatureAttachmentConstraint(my_public_key).isSatisfiedBy(it)} !=null)
    }



Q: If I am developing a CorDapp that depends on an external library do I need to do anything special?

* A: Same as before just add a `compile` dependency to the library, which will bundle it with your cordapp.



Troubleshooting
---------------

