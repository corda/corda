.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Tutorial
========

This tutorial will take you through how the commercial paper contract works, and then teaches you how to define your own
"hello world" contract for managing the ownership of an office building.

The code in this tutorial is available in both Kotlin and Java. You can quickly switch between them to get a feeling
for how Kotlin syntax works.

Starting the commercial paper class
-----------------------------------

A smart contract is a class that implements the ``Contract`` interface. For now, they have to be a part of the main
codebase, as dynamic loading of contract code is not yet implemented. Therefore, we start by creating a file named
either `CommercialPaper.kt` or `CommercialPaper.java` in the src/contracts directory with the following contents:

.. container:: codeset

   .. sourcecode:: kotlin

      class CommercialPaper : Contract {
          override val legalContractReference: SecureHash = SecureHash.sha256("https://en.wikipedia.org/wiki/Commercial_paper");

          override fun verify(tx: TransactionForVerification) {
              TODO()
          }
      }

   .. sourcecode:: java

      public class Cash implements Contract {
          @Override
          public SecureHash getLegalContractReference() {
              return SecureHash.Companion.sha256("https://en.wikipedia.org/wiki/Commercial_paper");
          }

          @Override
          public void verify(TransactionForVerification tx) {
              throw new UnsupportedOperationException();
          }
      }

Every contract must have at least a ``getLegalContractReference()`` and a ``verify()`` method. In Kotlin we express
a getter without a setter as an immutable property (val). The *legal contract reference* is supposed to be a hash
of a document that describes the legal contract and may take precedence over the code, in case of a dispute.

The verify method returns nothing. This is intentional: the function either completes correctly, or throws an exception,
in which case the transaction is rejected.

We also need to define a constant hash that would, in a real system, be the hash of the program bytecode. For now
we just set it to a dummy value as dynamic loading and sandboxing of bytecode is not implemented. This constant
isn't shown in the code snippet but is called `CP_PROGRAM_ID`.

So far, so simple. Now we need to define the commercial paper *state*, which represents the fact of ownership of a
piece of issued paper.

States
------

A state is a class that stores data that is checked by the contract.

.. container:: codeset

   .. sourcecode:: kotlin

      data class State(
        val issuance: InstitutionReference,
        val owner: PublicKey,
        val faceValue: Amount,
        val maturityDate: Instant
      ) : ContractState {
        override val programRef = CP_PROGRAM_ID

        fun withoutOwner() = copy(owner = NullPublicKey)
      }

   .. sourcecode:: java

      public static class State implements ContractState, SerializeableWithKryo {
        private InstitutionReference issuance;
        private PublicKey owner;
        private Amount faceValue;
        private Instant maturityDate;

        public State() {}  // For serialization

        public State(InstitutionReference issuance, PublicKey owner, Amount faceValue, Instant maturityDate) {
            this.issuance = issuance;
            this.owner = owner;
            this.faceValue = faceValue;
            this.maturityDate = maturityDate;
        }

        public InstitutionReference getIssuance() {
            return issuance;
        }

        public PublicKey getOwner() {
            return owner;
        }

        public Amount getFaceValue() {
            return faceValue;
        }

        public Instant getMaturityDate() {
            return maturityDate;
        }

        @NotNull
        @Override
        public SecureHash getProgramRef() {
            return SecureHash.Companion.sha256("java commercial paper (this should be a bytecode hash)");
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            State state = (State) o;

            if (issuance != null ? !issuance.equals(state.issuance) : state.issuance != null) return false;
            if (owner != null ? !owner.equals(state.owner) : state.owner != null) return false;
            if (faceValue != null ? !faceValue.equals(state.faceValue) : state.faceValue != null) return false;
            return !(maturityDate != null ? !maturityDate.equals(state.maturityDate) : state.maturityDate != null);

        }

        @Override
        public int hashCode() {
            int result = issuance != null ? issuance.hashCode() : 0;
            result = 31 * result + (owner != null ? owner.hashCode() : 0);
            result = 31 * result + (faceValue != null ? faceValue.hashCode() : 0);
            result = 31 * result + (maturityDate != null ? maturityDate.hashCode() : 0);
            return result;
        }

        public State withoutOwner() {
            return new State(issuance, NullPublicKey.INSTANCE, faceValue, maturityDate);
        }
      }

We define a class that implements the `ContractState` and `SerializableWithKryo` interfaces. The
latter is an artifact of how the prototype implements serialization and can be ignored for now: it wouldn't work
like this in any final product.

The `ContractState` interface requires us to provide a `getProgramRef` method that is supposed to return a hash of
the bytecode of the contract itself. For now this is a dummy value and isn't used: later on, this mechanism will change.
Beyond that it's a freeform object into which we can put anything which can be serialized.

We have four fields in our state:

* `issuance`: a reference to a specific piece of commercial paper at an institution
* `owner`: the public key of the current owner. This is the same concept as seen in Bitcoin: the public key has no
  attached identity and is expected to be one-time-use for privacy reasons. However, unlike in Bitcoin, we model
  ownership at the level of individual contracts rather than as a platform-level concept as we envisage many
  (possibly most) contracts on the platform will not represent "owner/issuer" relationships, but "party/party"
  relationships such as a derivative contract.
* `faceValue`: an `Amount`, which wraps an integer number of pennies and a currency.
* `maturityDate`: an `Instant <https://docs.oracle.com/javase/8/docs/api/java/time/Instant.html>`, which is a type
  from the Java 8 standard time library. It defines a point on the timeline.

States are immutable, and thus the class is defined as immutable as well. The `data` modifier in the Kotlin version
causes the compiler to generate the equals/hashCode/toString methods automatically, along with a copy method that can
be used to create variants of the original object. Data classes are similar to case classes in Scala, if you are
familiar with that language. The `withoutOwner` method uses the auto-generated copy method to return a version of
the state with the owner public key blanked out: this will prove useful later.

The Java code compiles to the same bytecode as the Kotlin version, but as you can see, is much more verbose.

Commands
--------

The logic for a contract may vary depending on what stage of a lifecycle it is automating. So it can be useful to
pass additional data into the contract code that isn't represented by the states which exist permanently in the ledger.

For this purpose we have commands. Often, they don't need to contain any data at all, they just need to exist. A command
is a piece of data associated with some *signatures*. By the time the contract runs the signatures have already been
checked, so from the contract code's perspective, a command is simply a data structure with a list of attached
public keys. Each key had a signature proving that the corresponding private key was used to sign.

Let's define a couple of commands now:

.. container:: codeset

   .. sourcecode:: kotlin

      interface Commands : Command {
          object Move : Commands
          object Redeem : Commands
          object Issue : Commands
      }


   .. sourcecode:: java

      public static class Commands implements core.Command {
          public static class Move extends Commands {
              @Override
              public boolean equals(Object obj) {
                  return obj instanceof Move;
              }
          }

          public static class Redeem extends Commands {
              @Override
              public boolean equals(Object obj) {
                  return obj instanceof Redeem;
              }
          }

          public static class Issue extends Commands {
              @Override
              public boolean equals(Object obj) {
                  return obj instanceof Redeem;
              }
          }
      }

The `object` keyword in Kotlin just defines a singleton object. As the commands don't need any additional data in our
case, they can be empty and we just use their type as the important information. Java has no syntax for declaring
singletons, so we just define a class that considers any other instance to be equal and that's good enough.

The verify function
-------------------

The heart of a smart contract is the code that verifies a set of state transitions (a *transaction*). The function is
simple: it's given a class representing the transaction, and if the function returns then the transaction is considered
acceptable. If it throws an exception, the transaction is rejected.

Each transaction can have multiple input and output states of different types. The set of contracts to run is decided
by taking the code references inside each state. Each contract is run only once. As an example, a contract that includes
2 cash states and 1 commercial paper state as input, and has as output 1 cash state and 1 commercial paper state, will
run two contracts one time each: Cash and CommercialPaper.

.. container:: codeset

   .. sourcecode:: kotlin

      override fun verify(tx: TransactionForVerification) {
          // Group by everything except owner: any modification to the CP at all is considered changing it fundamentally.
          val groups = tx.groupStates<State>() { it.withoutOwner() }

          // There are two possible things that can be done with this CP. The first is trading it. The second is redeeming
          // it for cash on or after the maturity date.
          val command = tx.commands.requireSingleCommand<CommercialPaper.Commands>()

   .. sourcecode:: java

      @Override
      public void verify(@NotNull TransactionForVerification tx) {
          // There are two possible things that can be done with CP. The first is trading it. The second is redeeming it
          // for cash on or after the maturity date.
          List<InOutGroup<State>> groups = tx.groupStates(State.class, State::withoutOwner);

          // Find the command that instructs us what to do and check there's exactly one.
          AuthenticatedObject<Command> cmd = requireSingleCommand(tx.getCommands(), Commands.class);

We start by using the `groupStates` method, which takes a type and a function (in functional programming a function
that takes another function as an argument is called a *higher order function*). State grouping is a way of handling
*fungibility* in a contract, which is explained next. The second line does what the code suggests: it searches for
a command object that inherits from the `CommercialPaper.Commands` supertype, and either returns it, or throws an
exception if there's zero or more than one such command.

Understanding fungibility
-------------------------

We say states are *fungible* if they are treated identically to each other by the recipient, despite the fact that they
aren't quite identical. Dollar bills are fungible because even though one may be worn/a bit dirty and another may
be crisp and new, they are still both worth exactly $1. Likewise, ten $1 bills are almost exactly equivalent to
one $10 bill. On the other hand, $10 and £10 are not fungible: if you tried to pay for something that cost £20 with
$10+£10 notes your trade would not be accepted.

So whilst our ledger could represent every monetary amount with a collection of states worth one penny, this would become
extremely unwieldy. It's better to allow states to represent varying amounts and then define rules for merging them
and splitting them. Similarly, we could also have considered modelling cash as a single contract that records the
ownership of all holders of a given currency from a given issuer. Whilst this is possible, and is effectively how
some other platforms work, this prototype favours a design that doesn't necessarily require state to be shared between
multiple actors if they don't have a direct relationship with each other (as would implicitly be required if we had a
single state representing multiple people's ownership). Keeping the states separated also has scalability benefits, as
different parts of the global transaction graph can be updated in parallel.

To make this easier the contract API provides a notion of groups. A group is a set of input states and output states
that should be checked for validity independently. It solves the following problem: because every contract sees every
input and output state in a transaction, it would easy to accidentally write a contract that disallows useful
combinations of states. For example, our cash contract might end up lazily assuming there's only one currency involved
in a transaction, whereas in reality we would like the ability to model a currency trade in which two parties contribute
inputs of different currencies, and both parties get outputs of the opposite currency.

Consider the following simplified currency trade transaction:

* **Input**:  $12,000 owned by Alice   (A)
* **Input**:   $3,000 owned by Alice   (A)
* **Input**:  £10,000 owned by Bob     (B)
* **Output**: £10,000 owned by Alice   (B)
* **Output**: $15,000 owned by Bob     (A)

In this transaction Alice and Bob are trading $15,000 for £10,000. Alice has her money in the form of two different
inputs e.g. because she received the dollars in two payments. The input and output amounts do balance correctly, but
the cash smart contract must consider the pounds and the dollars separately because they are not fungible: they cannot
be merged together. So we have two groups: A and B.

The `TransactionForVerification.groupStates` method handles this logic for us: firstly, it selects only states of the
given type (as the transaction may include other types of state, such as states representing bond ownership, or a
multi-sig state) and then it takes a function that maps a state to a grouping key. All states that share the same key are
grouped together. In the case of the cash example above, the grouping key would be the currency.

In our commercial paper contract, we don't want CP to be fungible: merging and splitting is (in our example) not allowed.
So we just use a copy of the state minus the owner field as the grouping key. As a result, a single transaction can
trade many different pieces of commercial paper in a single atomic step.

A group may have zero inputs or zero outputs: this can occur when issuing assets onto the ledger, or removing them.

Checking the requirements
-------------------------

After extracting the command and the groups, we then iterate over each group and verify it meets the required business
logic.

.. container:: codeset

   .. sourcecode:: kotlin

      for (group in groups) {
         val input = group.inputs.single()
         requireThat {
             "the transaction is signed by the owner of the CP" by (command.signers.contains(input.owner))
         }

         val output = group.outputs.singleOrNull()
         when (command.value) {
             is Commands.Move -> requireThat { "the output state is present" by (output != null) }

             is Commands.Redeem -> {
                 val received = tx.outStates.sumCashOrNull() ?: throw IllegalStateException("no cash being redeemed")
                 requireThat {
                     "the paper must have matured" by (input.maturityDate < tx.time)
                     "the received amount equals the face value" by (received == input.faceValue)
                     "the paper must be destroyed" by (output == null)
                 }
             }

             is Commands.Issue -> {
                    val output = group.outputs.single()
                    requireThat {
                        // Don't allow people to issue commercial paper under other entities identities.
                        "the issuance is signed by the claimed issuer of the paper" by
                                (command.signers.contains(output.issuance.institution.owningKey))
                        "the face value is not zero" by (output.faceValue.pennies > 0)
                        "the maturity date is not in the past" by (output.maturityDate > tx.time)
                        // Don't allow an existing CP state to be replaced by this issuance.
                        "there is no input state" by group.inputs.isEmpty()
                    }
             }

             else -> throw IllegalArgumentException("Unrecognised command")
         }
      }

   .. sourcecode:: java

      for (InOutGroup<State> group : groups) {
          List<State> inputs = group.getInputs();
          List<State> outputs = group.getOutputs();

          // For now do not allow multiple pieces of CP to trade in a single transaction. Study this more!
          State input = single(filterIsInstance(inputs, State.class));

          if (!cmd.getSigners().contains(input.getOwner()))
              throw new IllegalStateException("Failed requirement: the transaction is signed by the owner of the CP");

          if (cmd.getValue() instanceof JavaCommercialPaper.Commands.Move) {
              // Check the output CP state is the same as the input state, ignoring the owner field.
              State output = single(outputs);

              if (!output.getFaceValue().equals(input.getFaceValue()) ||
                      !output.getIssuance().equals(input.getIssuance()) ||
                      !output.getMaturityDate().equals(input.getMaturityDate()))
                  throw new IllegalStateException("Failed requirement: the output state is the same as the input state except for owner");
          } else if (cmd.getValue() instanceof JavaCommercialPaper.Commands.Redeem) {
              Amount received = CashKt.sumCashOrNull(inputs);
              if (received == null)
                  throw new IllegalStateException("Failed requirement: no cash being redeemed");
              if (input.getMaturityDate().isAfter(tx.getTime()))
                  throw new IllegalStateException("Failed requirement: the paper must have matured");
              if (!input.getFaceValue().equals(received))
                  throw new IllegalStateException("Failed requirement: the received amount equals the face value");
              if (!outputs.isEmpty())
                  throw new IllegalStateException("Failed requirement: the paper must be destroyed");
          } else if (cmd.getValue() instanceof JavaCommercialPaper.Commands.Issue) {
              // .. etc .. (see Kotlin for full definition)
          }
      }

This loop is the core logic of the contract.

The first line (first three lines in Java) impose a requirement that there be a single piece of commercial paper in
this group. We do not allow multiple units of CP to be split or merged even if they are owned by the same owner. The
`single()` method is a static *extension method* defined by the Kotlin standard library: given a list, it throws an
exception if the list size is not 1, otherwise it returns the single item in that list. In Java, this appears as a
regular static method of the type familiar from many FooUtils type singleton classes. In Kotlin, it appears as a
method that can be called on any JDK list. The syntax is slightly different but behind the scenes, the code compiles
to the same bytecodes.

Next, we check that the transaction was signed by the public key that's marked as the current owner of the commercial
paper. Because the platform has already verified all the digital signatures before the contract begins execution,
all we have to do is verify that the owner's public key was one of the keys that signed the transaction. The Java code
is straightforward. The Kotlin version looks a little odd: we have a *requireThat* construct that looks like it's
built into the language. In fact *requireThat* is an ordinary function provided by the platform's contract API. Kotlin
supports the creation of *domain specific languages* through the intersection of several features of the language, and
we use it here to support the natural listing of requirements. To see what it compiles down to, look at the Java version.
Each `"string" by (expression)` statement inside a `requireThat` turns into an assertion that the given expression is
true, with an exception being thrown that contains the string if not. It's just another way to write out a regular
assertion, but with the English-language requirement being put front and center.

Next, we take one of two paths, depending on what the type of the command object is.

If the command is a `Move` command, then we simply verify that the output state is actually present: a move is not
allowed to delete the CP from the ledger. The grouping logic already ensured that the details are identical and haven't
been changed, save for the public key of the owner.

If the command is a `Redeem` command, then the requirements are more complex:

1. We want to see that the face value of the CP is being moved as a cash claim against some institution, that is, the
   issuer of the CP is really paying back the face value.
2. The transaction must be happening after the maturity date.
3. The commercial paper must *not* be propagated by this transaction: it must be deleted, by the group having no
   output state. This prevents the same CP being considered redeemable multiple times.

To calculate how much cash is moving, we use the `sumCashOrNull` utility method. Again, this is an extension method,
so in Kotlin code it appears as if it was a method on the `List<Cash.State>` type even though JDK provides no such
method. In Java we see its true nature: it is actually a static method named `CashKt.sumCashOrNull`. This method simply
returns an `Amount` object containing the sum of all the cash states in the transaction output, or null if there were
no such states *or* if there were different currencies represented in the outputs! So we can see that this contract
imposes a limitation on the structure of a redemption transaction: you are not allowed to move currencies in the same
transaction that the CP does not involve. This limitation could be addressed with better APIs, if it were to be a
real limitation.

Finally, we support an `Issue` command, to create new instances of commercial paper on the ledger. It likewise
enforces various invariants upon the issuance.

This contract is extremely simple and does not implement all the business logic a real commercial paper lifecycle
management program would. For instance, there is no logic requiring a signature from the issuer for redemption:
it is assumed that any transfer of money that takes place at the same time as redemption is good enough. Perhaps
that is something that should be tightened. Likewise, there is no logic handling what happens if the issuer has gone
bankrupt, if there is a dispute, and so on.

As the prototype evolves, these requirements will be explored and this tutorial updated to reflect improvements in the
contracts API.

Adding a crafting API to your contract
--------------------------------------

TODO: Write this after the CP contract has had a crafting API actually added.

How to test your contract
-------------------------

TODO: Write this next

Non-asset-oriented based smart contracts
----------------------------------------

It is important to distinguish between the idea of a legal contract vs a code contract. In this document we use the
term *contract* as a shorthand for code contract: a small module of widely shared, simultaneously executed business
logic that uses standardised APIs and runs in a sandbox.

Although this tutorial covers how to implement an owned asset, there is no requirement that states and code contracts
*must* be concerned with ownership of an asset. It is better to think of states as representing useful facts about the
world, and (code) contracts as imposing logical relations on how facts combine to produce new facts.

For example, in the case that the transfer of an asset cannot be performed entirely on-ledger, one possible usage of
the model is to implement a delivery-vs-payment lifecycle in which there is a state representing an intention to trade,
another state representing an in-progress delivery, and a final state in which the delivery is marked as complete and
payment is being awaited.

As another example, consider multi-signature transactions, a feature which is commonly used in Bitcoin to implement
various kinds of useful protocols. This technique allows you to lock an asset to ownership of a group, in which a
threshold of signers (e.g. 3 out of 4) must all sign simultaneously to enable the asset to move. It is initially
tempting to simply add this as another feature to each existing contract which someone might want to treat in this way.
But that could lead to unnecessary duplication of work.

A better approach is to model the fact of joint ownership as a new contract with its own state. In this approach, to
lock up your commercial paper under multi-signature ownership you would make a transaction that looks like this:

* **Input**: the CP state
* **Output**: a multi-sig state that contains the list of keys and the signing threshold desired (e.g. 3 of 4). The state has a hash of H.
* **Output**: the same CP state, with a marker that says a state with hash H must exist in any transaction that spends it.

The CP contract then needs to be extended only to verify that a state with the required hash is present as an input.
The logic that implements measurement of the threshold, different signing combinations that may be allowed etc can then
be implemented once in a separate contract, with the controlling data being held in the named state.

Future versions of the prototype will explore these concepts in more depth.