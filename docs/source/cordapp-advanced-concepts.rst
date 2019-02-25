.. Intended reader of this document is a CorDapp developer who wants to understand how to write production-ready CorDapp kernels.
 - Introduce the basic building blocks of transaction verification and how they fit together.
 - Gradually introduce more advanced requirements like CorDapp dependencies, evolution rules.
 - Present the limitations of Corda 3 and Corda 4.
 - Proposed solutions and troubleshooting.


Advanced CorDapp Concepts
=========================

.. Preamble.

At the heart of the Corda design and security model is the idea that a transaction is valid if and only if all the `verify()` functions in
the contract code associated with each state in the transaction succeed. And the contract constraints features in Corda provide a rich set
of tools for specifying and constraining which verify functions out of the universe of possibilities can legitimately be used in (attached to) a transaction.

In simple scenarios, this works as you would expect and Corda's in-built security controls ensure that your applications work as you expect them too.
However, if you move to more advanced scenarios, especially ones where your verify function depends on code from other non-Corda libraries,
especially code that other people's verify functions may also depend on, you need to start thinking about what happens if and when states
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
this code in the transaction he created. If it were possible to do this without any restrictions, an attacker seeking to steal your money,
for example, might create a transaction that transitions a `Cash` contract owned by you to one owned by the attacker.
The only thing that is protecting your `Cash` is the contract verification code, so all the attacker has to do is attach a version of the
`net.corda.finance.contracts.asset.Cash` contract class that permits this transition to occur.
So we clearly need a way to ensure that the actual code attached to a transaction purporting to implement any given contract is constrained in some way.
For example, perhaps we wish to ensure that only the specific implementation of `net.corda.finance.contracts.asset.Cash` that was specified by the initial issuer of the cash is used.
Or perhaps we wish to constrain it in some other way. To prevent the types of attacks that can arise if there were no restrictions on which
implementations of Contract classes were attached to transactions, we provide the contract constraints mechanism to complement the class name,
that allows the State to specify exactly what code can be attached.
In Corda 4, for example, the state can say: "I'm ok to be spent if the transaction is verified by a class: `com.megacorp.megacontract.MegaContract` as
long as the JAR containing this contract is signed by `Mega Corp`".
This approach combines security and usability, allowing the code to evolve and developers to fix bugs. It also gives confidence when verifying
a transaction chain because the code that verifies it is signed by the same entity.

.. Introduce the `LedgerTransaction` abstraction and how it relates to the transaction chain. Introduce the state serialization/deserialization and Classloaders.

Another relevant aspect is that, as mentioned above, states are serialised Java objects. To perform any useful operation on them they need to
be deserialized into instances of java objects. All these instances are made available to the contract code as the `LedgerTransaction` parameter
passed to the `verify` method. The `LedgerTransaction` class abstracts away a lot of complexity and offers contracts a usable data structure where
all objects are loaded in the same classloader and can be freely used and filtered by class. This way, the contract developer can focus on the business logic.

Behind the scenes, thing are more complex. As can be seen in this illustration:

.. image:: resources/tx-chain.png
   :scale: 20%
   :align: center

.. How The UTxO model is applied.

.. note:: Corda's design is based on the UTxO model. In a serialized transaction the input and reference states are `StateRef`s - only references
          to output states from previous transactions (see :doc:`api-transactions`).
          When building the `LedgerTransaction`, the `inputs` and `references` are resolved to Java objects created by deserialising blobs of data
          fetched from previous transactions, that were serialized in that context - within the classloader of that transaction.
          This model has consequences when it comes to how states can be evolved. Removing a field from a newer version of a state would mean
          that when deserialising that state in the context of a transaction using the more recent code, that field could just disappear.
          To prevent this, in Corda 4 we implemented the no-data loss rule, which prevents this to happen. See :doc:`serialization-default-evolution`

.. Go through a very basic example of transaction verification.

Let's consider a very simple case, a transaction swapping `Apples` for `Oranges`. Each of the states that need to be swapped is the output of a previous transaction.
Similar to the above image the `Apples` state is the output of some previous transaction, through which it came to be possessed by the party now paying it away in return for some oranges.
The `Apples` and `Oranges` states that will be consumed in this new transaction exist as serialised `TransactionState`s.
It is these `TransactionState`s that specify the fully qualified names of the contract code that should be run to verify their consumption as well as,
importantly, the governing `constraint`s on which specific implementations of that class name can be used.
The swap transaction would contain the two input states, the two output states with the new owners of the fruit and the code to be used to deserialize and
verify the transaction as two attachment IDs - which are SHA256 of the apples and oranges CorDapps (more specifically, the CorDapp kernels).

.. note:: The attachment ID is a cryptographic hash of a file. Any node calculates this hash when it downloads the file from a peer (during transaction resolution) or from
          another source, and thus knows that it is the exact file that any other party verifying this transaction will use. In the current version of
          Corda - v4 -, nodes won't load JARs downloaded from a peer into a classloader. This is a temporary security measure until we integrate the
          Deterministic JVM Sandbox, which will be able to isolate network loaded code from sensitive data.

This combination of fully qualified contract class name and constraint ensures that, when a state is spent, the contract code attached to the transaction
(that will ultimately determine whether the transaction is considered valid or not) meets the criteria laid down in the transaction that created that state.
For example, if a state is created with a constraint that says its consumption can only be verified by code signed by MegaCorp,
then the Corda consensus rules mean that any transaction attaching an implementation of the class that is _not_ signed by MegaCorp will not be considered valid.

.. Verify attachment constraints. Introduce constraints propagation.

The previous discussion explained the construction of a transaction that consumes one or more states. Now let's consider this from the perspective
of somebody verifying a transaction they are presented with.

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

We mentioned previously that Corda operates in a decentralised system. Nothing stops an adversary to attach a JAR he just created to a transaction.
This JAR could contain some of the same classes that are also available in a legitimate library or in the contract JAR itself. Due to how Java classloaders work,
this would cause ambiguity as to what code will be executed, so an attacker could attempt to exploit this and trick other nodes that a transaction that
should be invalid is actually valid. To address this vulnerability, Corda introduces the `no-overlap` rule:

.. note:: The `no-overlap rule` is applied to the `AttachmentsClassloader` that is build for each transaction. If a file with the same path but different content exists
          in multiple attachments, the transaction is considered invalid. The reason for this is that these files can provide different implementations
          of the same class and which one is loaded might depend on the implementation of the underlying JVM. This would break the determinism, and
          would also open subtle security problems.

.. Why does this need to be so complicated? Cross contract references, Class identity crisis.
   Here we explain why all the attachments need to be combined.

The process described above may appear surprising. Nodes have cordapps installed anyway. Why does the code need to be attached to the transaction?
The design of Corda is that the validity of a transaction should not depend on any node specific setup and should always return the same result,
even if the transaction is verified in 20 years, when the current version of the CorDapps will not be installed on any node.
This mechanism ensures that given the same input (the binary representation of a transaction), any node is able to load the same code and calculate
the exact same result.

If every state has its own governing code then why can't we just verify individual transitions independently? This would simplify a lot of things.
The answer is that for a trivial case like swapping `Apples` for `Oranges` where the two contracts might not care about the other states in the
transaction, this could be a solution. But Corda is designed to support complex business scenarios where the `Apples` contract could check
that Pink Lady apples can only be traded against Valencia oranges. If apples and oranges were loaded in separate classloaders then the
contract code would hit the java `Class identity crisis <https://www.ibm.com/developerworks/java/library/j-dyn0429/>`_ issue and would get
lots of `ClassCastExceptions`.


.. Now we introduce a simple dependency. And the problems that come with this. We already established that all attachments are combined.

Exchanging Apples for Oranges is a contrived example, of course, but this pattern is not uncommon. And a common scenario is one where code
that is common to a collection of state types is abstracted into a common library.
For example, imagine Apples and Oranges both depended on a `Fruit` library developed by a third party as part of their verification logic.

This library must obviously be available to execute, since the verification logic depends on it, which in turn means it must be loaded by the Attachments Classloader.
Since the classloader is constructed solely from code attached to the transaction, it means the library must be attached to the transaction.
The question to consider as a developer of CorDapps is: where and how should it be attached?

There are 2 options to achieve this:

 1. Imagine only the Apples code has been refactored to depend on the Fruit library. In which case, you could bundle the external library with the `Apples` code.
    Basically create a fat-JAR that includes all dependencies. In the general case, where you are using signature constraints, you will sign over this fat JAR file.
 2. Add the dependency as another attachment to the transaction.

These options have pros and cons, which are now discussed:

Approach one is fairly straight forward and does not require any additional setup. Just declaring a `compile` dependency to a cordapp
will by default bundle the dependency with the cordapp. One obvious drawback is that CorDapp JARs can grow quite large in case they depend on
large libraries. Other drawbacks will be discussed below.

Approach two can be attractive in cases where multiple applications depend on the same library but it currently requires an additional security
check to be included in contract code. As stated previously anyone can create a JAR containing a class your CorDapp depends on, so a malicious actor
could just create his own version of the library and attach that to the transaction instead of the legitimate one your code expects. This would allow
the attacker to change the intended behavior of the contract that depends on this code to his advantage.

There are ways to make this option secure and future versions of Corda will explore and implement them.
Currently, if a CorDapp developer decides to go for this approach they can write custom contract code to perform dependency validity checks as the `verify` method
has access to the `LedgerTransaction`. As soon as support is added at the platform level this code can be removed. See below for example code.
What this manual check does is extend the security umbrella provided by the constraint of the state to its dependencies.

.. warning:: In Corda 4, it is the responsibility of the CorDapp developer to ensure that dependencies are added in a secure way.
             Fat-JARing the dependency is secure, but adding the attachment to the transaction is not enough. The contract code (that is guaranteed to be correct by the constraints mechanism),
             must verify that all dependencies are available and are not malicious.


If the library you depend on is unique to your application then bundling it in your fat JAR probably makes sense.

However, if it is a library that other contracts (eg `Oranges`) may plausibly depend on, then the node building the transaction might face a problem
when trying to build an `Apples` for `Oranges` transaction. If `Apples` bundles `guava-v23` and `Oranges` bundles `guava-v27` then the transaction
will break the `no-overlap` rule and could never validate.

A simple way to fix this problem is for cordapps to shade this popular dependency under their own namespace. This would avoid breaking the `no-overlap rule`.
The primary downside is that multiple apps using (and shading) this dependency may lose the ability in other contexts to do things like cast to some common superclass.
Also, currently, the Corda gradle plugin does not provide any tooling for shading.

.. note:: A very important point to remember as a CorDapp developer when you prepare for release is that states created with your CorDapp can in theory
          be used in transactions with any other states that are governed by CorDapps that might not exist for the next 10 years. In order to
          maximise the usefulness of your CorDapp you have to ensure that the overlap footprint is as low as possible.


The alternative approach is to attach the library as a separate attachment (approach 2). This opens the door to multiple contracts depending on the same
library without having to include it in their JAR, but it requires them to depend on the same major version of the library (assuming semantic versioning).
The downside to this, besides the security concern expressed earlier, is that the flow building the transaction needs to decide which version of the library is
compatible with all the contracts that it must use.
To handle this complexity in a standard way will require platform support so we recommend to start with the bundling approach and, as soon as the platform
adds support for secure and usable dependency management, it will be easy to upgrade the contract to use it.

.. note:: Currently the `cordapp` gradle plugin that ships with Corda only supports bundling a dependency fully unshaded, by declaring it as a `compile` dependency.
        It also supports `cordaCompile`, which assumes the dependency is available so it does not bundle it. There is no current support for shading or partial bundling.


.. Introduce the most complex case.

CorDapp depending on other CorDapp(s)
-------------------------------------

.. Present some reasonable examples.

We presented the "complex" business requirement earlier where the `Apples` contract has to check that it can't allow swapping Pink Lady apples for anything
but Valencia Oranges. This actually means that the library that the `Apples` CorDapp depends on is itself a CorDapp.

Or, we can use as an example the `finance` CorDapp that is shipped with Corda as a sample.

.. note:: As it is just a sample, it is signed by R3's development key, which the node is explicitly configured - but overridable - to blacklist
  by default in production in order to avoid you inadvertently going live without having first determined the right approach for your solution.
  But it is illustrative to other reusable CorDapps that might get developed.

The finance CorDapp brings some handy utilities that can be used by code in other CorDapps, some abstract base types like `OnLedgerAsset`,
but also comes with its own ready-to-use contracts like: `Cash`, `Obligation` and `Commercial Paper`.

Imagine you are selling `Bonds` for `Cash`, and the `Bonds` contract depends on the finance CorDapp - for example it extends `OnLedgerAsset`.
At compile time the `Bonds` CorDapp needs a dependency on finance. But how can that be expressed when building transactions?

.. Why is FatJar not an option?

In the `Bonds` for `Cash` example the transaction needs two attachments: the `finance` JAR signed by R3's key and the `bonds` JAR signed by `CompanyA`.
(For the purpose of this exercise let's ignore the fact that the JAR is signed by R3's development key).
The reason for this, as explained above, is that each state must check that its contract constraints are satisfied.

Let's imagine that the `Bonds` kernel JAR bundled the finance JAR. This means that when verifying the above transaction,
there would be ambiguity as to which JAR to apply the contract constraint rule of the `Cash` state. The reason for this is that both JARs
would contain an implementation for `net.corda.finance.contracts.asset.Cash`. This would break the transaction verification rule that states:
"There can be only one and precisely one attachment that is identified as the contract code that controls each state"

.. warning:: If, as a CorDapp developer you bundle a third party CorDapp that you depend upon, it will become impossible for anyone to build
             valid transactions that contain both your states and third party states. This would severely limit the usefulness of your CorDapp.

.. The suggested solution.

Now, let's imagine that someone wants to trade `Bonds` for `Apples`. In this case, given that the `Bonds` Cordapp can't bundle `finance`,
the creator of the transaction needs to add the `finance` JAR to the transaction attachments. As described above, this solution needs additional
checks added to the `Bonds`.

.. note:: The preferred solution for CorDapp to CorDapp dependency is to just add checks in your contract that the transaction contains a valid
          version of the dependency. Also, in the flow code, make sure to attach the dependant CorDapp to the transaction. See below for example code.

.. Other options.

Same as for normal dependencies, CorDapp developers can use shading or partial bundling if they really want to bundle the code. All the drawbacks
described above will apply.

.. TODO - package ownership
Another problem with this approach is that it introduces namespace confusion. If someone decides to issue `net.corda.finance.contracts.asset.Cash`
using the `apples` contract that bundles the finance app it would be a completely different state from one that was issued with the R3 controlled contract.
This is because the code could evolve in completely different directions and users of that state who don't check the constraint would be misled.
In Corda 4, to help avoid this type of confusion, we introduced the concept of Package Namespace Ownership (see ":doc:`design/data-model-upgrades/package-namespace-ownership`").
Briefly, it allows companies to claim namespaces and anyone who encounters a class in that package that is not signed by the registered key knows is invalid.
 3. Package ownership: `net.corda.finance.contracts.asset` would be claimed by R3. This would give confidence to all participants that if a JAR
    with this package is attached to a transaction it must be created by the original developer which was deemed as trustworthy by the zone operator.


Changes between version 3 to version 4 of Corda
-----------------------------------------------

In Corda v3 transactions were verified inside the System Classloader that contained all the installed CorDapps.
This was a temporary simplification and we explained above why it could only be short-lived.

If we consider the example from above with the `Bonds` contract that depends on finance, the bonds contract developer could have just released
the `Bonds` specific code (without bundling in the dependency on finance or attaching it to the transaction ) and rely on the fact that
finance would be on the classpath during verification.

This means that in Corda 3 nodes could have formed `valid` transactions that were not entirely self-contained. In Corda 4, because we
moved transaction verification inside the `AttachmentsClassloader` these transactions would fail with `ClassNotFound` exceptions.

These incomplete transactions need to be considered valid in Corda 4 and beyond though, so the fix we added for this was to look for a `trusted` attachment
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

