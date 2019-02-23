.. Intended reader of this document is a CorDapp developer who will be hitting issues when transitioning to Corda 4.
 - Introduce the basing building blocks of transaction verification and how they fit together to achieve the final goal.
 - Gradually introduce more advanced scenarios and present the limitations of Corda 3 and Corda 4.
 - Proposed solutions and troubleshooting.

Advanced CorDapp Concepts
=========================

.. Preamble.

At the heart of the Corda design and security model is the idea that a transaction is valid if and only if all the `verify()` functions in
the contract code associated with each state in the transaction succeed. And the contract constraints features in Corda provide a rich set
of tools for specifying and constraining which verify functions out of the universe of possibilities can legitimately be used in (attached to) a transaction.

In simple scenarios, this works as you would expect and Corda's in-built security controls ensure that your applications work as you expect them too.
However, if you move to more advanced scenarios, especially ones where your verify function depends on code from other non-Corda libraries,
especially code that _other_ people's verify functions may also depend on, you need to start thinking about what happens if and when states
governed by these different pieces of code are brought together. If they both depend on a library, which common version should be used?
How do you avoid your verify function's behaviour changing unexpectedly if the wrong version of the library is used? Are you at risk of subtle attacks?
The good news is that Corda is designed to deal with these situations but the flip side is that you need to understand how this is done,
and the implications for how you package, distribute and attach your contract code to transactions.

This document provides the information you need in order to understand what happens behind the scenes and how it affects the CorDapp you are working on.


How transactions are verified in Corda
--------------------------------------

.. Recap: basic transaction structure.

Corda transactions evolve input states into output states. A state is a data structure containing: the actual data fact (that is expressed as a
strongly typed serialized java object) and a reference to the logic (contract) that needs to verify a transition to and from this state.
Corda does not embed the actual verification bytecode in transactions. The logic is expressed as a Java class name and a contract constraint
(read more in: :doc:`api-contract-constraints`), and the actual code lives in a JAR file that is referenced by the transaction.

.. The basic threat model and security requirement.

Being a decentralized system, anyone who can build transactions can create `.java` files, compile and bundle them in a JAR, and then reference
this code in the transaction he created. To prevent this type of attack, we provide the contract constraints mechanism to complement the class name,
that allows the State to specify exactly what code can be attached.
In Corda 4, for example, the state can say: "I'm ok to be spent if the transaction is verified by a class: `com.megacorp.megacontract.MegaContract` as
long as the JAR containing this contract is signed by `Mega Corp`".
This approach combines security and usability, allowing the code to evolve and developers to fix bugs. It also gives confidence when verifying
a transaction chain because the code that verifies it is signed by the same entity.

.. Introduce the `LedgerTransaction` abstraction and how it relates to the transaction chain. Introduce the state serialization/deserialization and Classloaders.

Another relevant aspect is that, as mentioned above, states are serialised java objects. To perform any useful operation on them they need to
be deserialized into instances of java objects. All these instances are made available to the contract code as the `LedgerTransaction` parameter
passed to the `verify` method. The `LedgerTransaction` class abstracts away a lot of complexity and offers contracts a usable data structure where
all objects are loaded in the same classloader and can be accessed and filtered by class name, so that the contract developer can focus on
the business logic.

Behind the scenes, thing are more complex. As can be seen in this illustration:

.. image:: resources/tx-chain.png
   :scale: 20%
   :align: center


The `LedgerTransaction.inputs` and `LedgerTransaction.references` are actually blobs of data fetched from previous transactions,
that were serialized in that context - within the classloader of that transaction.
This is because in the vault the input and reference states are actually `StateRefs` - pairs of Transaction Id and index.
But they need to be verified together with the output states that were created in a different context.


.. Go through a very basic example of transaction verification.

.. How The UTxO model is applied.

Let's consider a very simple case, a transaction swapping `Apples` for `Oranges`. Each of the states that need to be swapped is the output of a previous transaction.
Similar to the above image the `Apples` state can be the output of a transaction where the apples could have been swapped for grapes.
The `Apples` state that will be referenced as an input state is actually a serialized `TransactionState` that is stored within the
Merkle Tree structure of the transaction that created it. (Read more in: :doc:`key-concepts-tearoffs`)
Both the `Apples` and the `Oranges` states contain the usual data characteristics that you would expect from fresh fruit, found in the `TransactionState.data` field ,
but also the rules around the governing contract that ensures that they can be safely traded - the `contractClassName` and `constraint`. ( Read more in: :ref:`transaction_state` )

The swap transaction would contain the 2 input states and 2 output states with the new owners of the fruit.
The code to be used to deserialize and verify the transaction would be added to the transaction as 2 attachment IDs - which are SHA256 of the apples and oranges CorDapps.

.. note:: The attachment ID is a cryptographic hash of a file. Any node calculates this hash when it downloads the file from a peer (during transaction resolution) or from
          another source, and thus knows that it is the exact file that any other party verifying this transaction will use. In the current version of
          Corda - v4 -, nodes won't load JARs downloaded from a peer into a classloader. This is a temporary security measure until we integrate the
          Deterministic JVM Sandbox, which will be able to isolate network loaded code from sensitive data.

.. Verify attachment constraints. Introduce constraints propagation.

When a node needs to verify this transaction the first thing it has to do is to ensure that the transaction was formed correctly. Given that the input states
are already agreed to be valid facts, the creator of the current transaction has to attach code that is compliant with their constraints.
The output states are also objects created by a node so they must be created with a valid constraint, to ensure the validity of the future chain (:ref:`constraints_propagation`).
The rule is that for each state there must be one and only one attachment that contains the fully qualified contract class name. This attachment will
be identified as the CorDapp JAR corresponding to that state and thus it must satisfy the constraint of that state.
For example, if the state is signature constrained, the attachment must be signed by the key specified in the state.
If this rule is breached the transaction is considered invalid even if it is signed by all the required parties, and any compliant node will refuse to execute
the verification code.

This rule, together with the no-overlap rule - which we'll introduce below - ensure that the code used to deserialize and verify the transaction is
legitimate and that there is no ambiguity when it comes to what code to execute. This is critical to achieving the determinism property.

.. Contract execution and the AttachmentsClassloader.

To verify the business rules of the transaction, the smart contract code for each state will be executed.
This is done by creating an `AttachmentsClassloader` from all the attachments listed by the transaction, then deserialising the binary
representation of the transaction inside this classloader, create the `LedgerTransaction` and then running the contract verification code
in this classloader.

.. Why does this need to be so complicated? Cross contract references, Class identity crisis.
   Here we explain why all the attachments need to be combined.

The process described above may appear surprising. Nodes have cordapps installed anyway. Why does the code need to be attached to the transaction?
The design of Corda is that the validity of a transaction should not depend on any node specific setup and should always return the same result,
even if the transaction is verified in 20 years, when the current version of the CorDapps will not be installed on any node.
This mechanism ensures that given the same input (the binary representation of a transaction), any node is able to load the same code and calculate
the exact same result.

If every state has it's own governing code then why can't we just verify individual transitions independently? This would simplify a lot of things.
The answer is that for a trivial case like swapping `Apples` for `Oranges` where the 2 contracts might not care about the other states in the
transaction, this could be a solution. But Corda is designed to support complex business scenarios where the `Apples` contract could check
that Pink lady apples can only be traded against Valencia oranges. If apples and oranges were loaded in separate classloaders then the
contract code would hit the java `Class identity crisis <https://www.ibm.com/developerworks/java/library/j-dyn0429/>`_ issue and would get
lots of `ClassCastExceptions`.


.. Now we introduce a simple dependency. And the problems that come with this. We already established that all attachments are combined.

Things get more complicated though if the `Apples` contract uses an external library which gets called during verification.

This library must obviously be available to execute, since the verification logic depends on it, which in turn means it must be loaded by the Attachments Classloader.
Since it is constructed solely from code attached to the transaction, it means the library must be attached to the transaction.
The question to consider as a developer of CorDapps is: where and how should it be attached?

There are 2 options to achieve this:

 1. Bundle the external library with the `Apples` code. Basically create a fat-JAR that includes all dependencies.
    In the general case, where you are using signature constraints, you will sign over this fat JAR file.
 2. Add the dependency as another attachment to the transaction.

These options have pros and cons, which are now discussed:

Approach 1 is fairly straight forward and does not require any additional setup. Just declaring a `compile` dependency to a cordapp
will by default bundle the dependency with the cordapp. One obvious drawback is that CorDapp JARs can grow quite large in case they depend on
large libraries. Other drawbacks will be discussed below.

Approach 2 is insecure without manually adding additional security checks. As stated previously anyone can create a JAR, so a malicious actor
could just create his own version of the library and attach that. This would allow the attacker to change the intended behavior of the
contract that depends on this code to his advantage.

There are ways to make this option secure and future versions of Corda will explore and implement them.
If a CorDapp developer decides to go for this approach they can write custom contract code to perform dependency validity checks as the `verify` method
has access to the `LedgerTransaction`. As soon as support is added at the platform level this code can be removed.

.. warning:: In Corda 4, it is the responsibility of the CorDapp developer to ensure that dependencies are added in a secure way.
             Fat-JARing the dependency is secure, but adding the attachment to the transaction is not enough. The contract code (that is guaranteed to be correct by the constraints mechanism),
             must verify that all dependencies are available and are not malicious.

.. All pieces are in place now to introduce the no-overlap rule and the consequences for dependencies.

If the library you depend on is unique to your application then bundling it in your fat JAR probably makes sense.

However, if it is a library that other contracts (eg `Oranges`) may plausibly depend on, then the node building the transaction might face a problem
when trying to build an `Apples` for `Oranges` transaction. If `Apples` bundles `guava-v23` and `Oranges` bundles `guava-v27` then the transaction
will break the `no-overlap` rule and could never validate.

.. note:: The `no-overlap rule` is applied to the `AttachmentsClassloader` that is build for each transaction. If a file with the same path but different content exists
          in multiple attachments, the transaction is considered invalid. The reason for this is that these files can provide different implementations
          of the same class and which one is loaded might depend on the implementation of the underlying JVM. This would break the determinism, and
          would also open subtle security problems.

A simple way to fix this problem is for cordapps to shade this popular dependency under their own namespace. This would avoid breaking the `no-overlap rule`.
The primary downside is that multiple apps using (and shading) this dependency may lose the ability in other contexts to do things like cast to some common superclass.
Also, currently, the Corda gradle plugin does not provide any tooling for shading.

The alternative approach is to attach the library as a separate attachment (approach 2). This opens the door to multiple contracts depending on the same
library without having to include it in their JAR, but it requires them to depend on the same major version of the library (assuming semantic versioning).
The downside to this, besides the security concern expressed earlier, is that the flow building the transaction needs to decide which version of the library is
compatible with all the contracts that it must use.
To handle this complexity in a standard way will require platform support so we recommend to start with the bundling approach and, as soon as the platform
adds support for secure and usable dependency management, it will be easy to upgrade the contract to use it.

.. note:: Currently the `cordapp` gradle plugin that ships with Corda only supports bundling a dependency fully unshaded, by declaring it as a `compile` dependency.
        It also supports `cordaCompile`, which assumes the dependency is available so it does not bundle it. There is no current support for shading or partial bundling.

.. todo introduce the case where you just depend for cross contract checks

.. ONLY refactored up to here.

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
Briefly, it allows companies to claim namespaces and anyone who encounters a class in that package that is not signed by the registered key knows is invalid.

Given the above there are 4 possible solutions for reusable CorDapps:

 1. Partial bundling:  Only bundle the exact classes you need in your contract. Basically leave out the ready-to-use contracts. This would reduce
    the problem of a reusable CorDapp (described above) to that of a normal library ( with all the caveats around the no-overlap rule).

 2. Shading: This means that there would be no namespace collision, but the downside is that when extending some base interface the contract that
    extends would lose the relation with other implementations.

 3. Package ownership: `net.corda.finance.contracts.asset` would be claimed by R3. This would give confidence to all participants that if a JAR
    with this package is attached to a transaction it must be created by the original developer which was deemed as trustworthy by the zone operator.

 4. Manually attaching the actual library-Cordapp to the transaction. The contract that uses it is responsible to perform an equivalent of an
    Attachment constraint to make sure that a malicious party did not attach a "customized" JAR that alters the intended verification logic.


The preferred approach can be selected by the developers of the CorDapp, but the recommended approach is to go for 4 - manually attaching and checking.

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

.. note:: The transition to the `AttachmentsClassloader` is one more step towards the intended design of Corda. Next step is to integrate the DJVM and
         nodes will be able to execute any code downloaded from peers without any manual whitelisting step. Also it will ensure that the validation
         will return the exact same result no matter on what node or when it is run.

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

