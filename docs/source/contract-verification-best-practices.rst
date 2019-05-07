Writing smart contracts - Best practices
========================================

The contract verification logic is likely the most security sensitive code that CorDapp developers will write.
This code will be run by all nodes that must verify transactions with no restrictions to the inputs other that was is coded in the contract.

An important thing to remember when writing this code is that transactions can be handcrafted by colluding malicious participants that will try to
exploit any vulnerability in the contract logic to their advantage.

Also, remember that this code will eventually run in a sandboxed deterministic environment (the Deterministic JVM), which will prevent access to any source of randomness.
When writing a contract, there is no way to access the database, the local file system, the network, the time, etc..


General guidelines
------------------

Given the nature of this code, the usual guidelines for writing secure software apply. To enumerate just a few of them:

    * Structure the contract and commands logic to avoid any ambiguity. Remember that transactions can be composed of transitions of multiple types of states.
    It should be simple and non-ambiguous to identify which states correspond to which transition.

    * The logic should be written clearly and explicitly to make it easy to peer review. Avoid clever optimisations.

    * Use only dependencies written by reputable sources. Ideally the code should be audited and written to the same standards.

    * As a general rule, it's better to add redundant checks when unsure to achieve defense in depth.

    * Sanity check all inputs to all method calls to dependencies. As another defense in depth check.
    E.g: A dependency method calculates the maximum of 2 values. But has a bug that if a value is negative it returns it. An attacker might
    try to exploit this by engineering a state that will end up calling this method with a negative value.

    * Avoid, if possible, text parsing and regex. Or dependencies that rely on that. This means that creating a JSON type field should be avoided.

    * Prefer un-ambiguous types for State fields. Avoid ``Map<String,String>`` type objects.



Step 1: Implement business logic
--------------------------------

First step when writing a contract is to define and implement the main business logic checks.

A few example of business checks:

    * For ownable assets added as input states make sure the owner is among the signers.
    * For a Fungible Cash-like token, make sure that the sum of inputs equals the sum of outputs. This prevents "printing Cash".
    * A Commercial Paper must be redeemed for the full faceValue.


An important step is to identify the relevant transitions for the current contract based on the Commands.
As mentioned above, ideally there should be no ambiguity.

There are two possible approaches for how this can be modelled:

    * Use functions like ``tx.groupStates`` which create logical transitions based on the content of the states.
    * Or, more explicitly by adding and using metadata in the actual Commands. See: ``sample/CommercialPaper.kt`` for a full example.
    The big advantage of this approach is that it simplifies the security reasoning. Basically if each transition is valid and there are no duplicates
    or free floating states then the contract can safely pass.

After identifying the individual transitions, they should be verified independently.
E.g.: for a ``REDEEM`` transition, verify there are no output states. Or for ``ISSUE`` that the actual issuer has signed.

After making sure that each transition is valid, verify interactions with other transitions.
E.g: When redeeming a commercial paper, ensure that there exists a transition moving enough Cash.

Another possible check is if two Commands are mutually exclusive when used in the same transaction.



Step 2: Make sure that nothing unexpected is present in the transaction
-----------------------------------------------------------------------

The contract must also perform negative checks to make sure nothing unexpected or ambiguous was sneaked in.

The implementation of this step depends on how the CorDapp was structured.

Generally, after unambiguously identifying all transitions (commands and states) the contract must make sure there's no extra commands and states in the transaction.

Another mandatory check for each transition is to reject a command if it is not expected. Remember, an attacker can extend your base ``Command`` class and attach that
JAR to the transaction, so it is not enough to check the super class.

The contract should fail verification if anything unexpected is encountered.
As a general guideline there should be an ``else`` branch to all checks which should fail.



Step 3: Purely technical decentralized specific concerns
--------------------------------------------------------

Split up the flows code from the contracts code, and keep contracts as small as possible. The reason for this is that the contracts JAR is code
that lives on the ledger and will travel together with he transactions which use it.

Given that transactions are assembled from multiple contracts by transacting nodes, it is good practice to minimize the use of dependencies (even if the source is reputable).
.. TODO - point to the dependencies doc.

Only write the contract code in statically typed JVM languages, ideally Kotlin or Java. The runtime meta-programming features of dynamic languages like Groovy
or Clojure can be exploited by malicious code and should be avoided. Also avoid using dependencies written in dynamic languages.

Another thing to keep in mind is to mark all classes, fields and methods as ``final`` when using Java. In Kotlin this is the default behaviour,
so make sure to only use ``open`` when there is a good reason. This is necessary to close the possibility of a an attacker to find an exploit
by extending classes and overriding behaviour. ( More defense in depth)

Always target the latest platform version on which the CorDapp was tested. This will ensure it will be able to benefit from the latest security fixes
and optimisations.

Make sure the ContractState class is annotated with ``@BelongsToContract``. This metadata will instruct the platform to check that a malicious
actor does not attempt to create invalid states by referring to an invalid contract.

Whenever your contract code depends on some external library, the flow must explicitly attach that dependency to the transaction, and there must
be a check in the contract code that ensures that a malicious party didn't attach an invalid dependency JAR that could compromise the verification logic.
.. TODO - point to the dependencies doc.

If your contract depends on an attachment with reference data. For example a csv file containing exchange rates. Before opening and parsing the content
make sure the file is authentic. Maybe verify if the JAR was signed, or some other attribute of the file.

By default, contract JARs will be signed and the package sealed. Unless there is a good reason to change this, leave the default secure behaviour.




