.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Writing a contract
==================

This tutorial will take you through how the commercial paper contract works.

The code in this tutorial is available in both Kotlin and Java. You can quickly switch between them to get a feeling
for how Kotlin syntax works.

Starting the commercial paper class
-----------------------------------

A smart contract is a class that implements the ``Contract`` interface. For now, they have to be a part of the main
codebase, as dynamic loading of contract code is not yet implemented. Therefore, we start by creating a file named
either ``CommercialPaper.kt`` or ``CommercialPaper.java`` in the src/contracts directory with the following contents:

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
isn't shown in the code snippet but is called ``CP_PROGRAM_ID``.

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

We define a class that implements the ``ContractState`` and ``SerializableWithKryo`` interfaces. The
latter is an artifact of how the prototype implements serialization and can be ignored for now: it wouldn't work
like this in any final product.

The ``ContractState`` interface requires us to provide a ``getProgramRef`` method that is supposed to return a hash of
the bytecode of the contract itself. For now this is a dummy value and isn't used: later on, this mechanism will change.
Beyond that it's a freeform object into which we can put anything which can be serialized.

We have four fields in our state:

* ``issuance``: a reference to a specific piece of commercial paper at a party
* ``owner``: the public key of the current owner. This is the same concept as seen in Bitcoin: the public key has no
  attached identity and is expected to be one-time-use for privacy reasons. However, unlike in Bitcoin, we model
  ownership at the level of individual contracts rather than as a platform-level concept as we envisage many
  (possibly most) contracts on the platform will not represent "owner/issuer" relationships, but "party/party"
  relationships such as a derivative contract.
* ``faceValue``: an ``Amount``, which wraps an integer number of pennies and a currency.
* ``maturityDate``: an `Instant <https://docs.oracle.com/javase/8/docs/api/java/time/Instant.html>`_, which is a type
  from the Java 8 standard time library. It defines a point on the timeline.

States are immutable, and thus the class is defined as immutable as well. The ``data`` modifier in the Kotlin version
causes the compiler to generate the equals/hashCode/toString methods automatically, along with a copy method that can
be used to create variants of the original object. Data classes are similar to case classes in Scala, if you are
familiar with that language. The ``withoutOwner`` method uses the auto-generated copy method to return a version of
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

Let's define a few commands now:

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
                  return obj instanceof Issue;
              }
          }
      }

The ``object`` keyword in Kotlin just defines a singleton object. As the commands don't need any additional data in our
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
          val command = tx.commands.requireSingleCommand<CommercialPaper.Commands>()

   .. sourcecode:: java

      @Override
      public void verify(@NotNull TransactionForVerification tx) {
          List<InOutGroup<State>> groups = tx.groupStates(State.class, State::withoutOwner);
          AuthenticatedObject<Command> cmd = requireSingleCommand(tx.getCommands(), Commands.class);

We start by using the ``groupStates`` method, which takes a type and a function (in functional programming a function
that takes another function as an argument is called a *higher order function*). State grouping is a way of handling
*fungibility* in a contract, which is explained next. The second line does what the code suggests: it searches for
a command object that inherits from the ``CommercialPaper.Commands`` supertype, and either returns it, or throws an
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

To make all this easier the contract API provides a notion of groups. A group is a set of input states and output states
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

The ``TransactionForVerification.groupStates`` method handles this logic for us: firstly, it selects only states of the
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

      val time = tx.time
      for (group in groups) {
          when (command.value) {
              is Commands.Move -> {
                  val input = group.inputs.single()
                  requireThat {
                      "the transaction is signed by the owner of the CP" by (command.signers.contains(input.owner))
                      "the state is propagated" by (group.outputs.size == 1)
                  }
              }

              is Commands.Redeem -> {
                  val input = group.inputs.single()
                  val received = tx.outStates.sumCashBy(input.owner)
                  if (time == null) throw IllegalArgumentException("Redemption transactions must be timestamped")
                  requireThat {
                      "the paper must have matured" by (time > input.maturityDate)
                      "the received amount equals the face value" by (received == input.faceValue)
                      "the paper must be destroyed" by group.outputs.isEmpty()
                      "the transaction is signed by the owner of the CP" by (command.signers.contains(input.owner))
                  }
              }

              is Commands.Issue -> {
                  val output = group.outputs.single()
                  if (time == null) throw IllegalArgumentException("Issuance transactions must be timestamped")
                  requireThat {
                      // Don't allow people to issue commercial paper under other entities identities.
                      "the issuance is signed by the claimed issuer of the paper" by
                              (command.signers.contains(output.issuance.party.owningKey))
                      "the face value is not zero" by (output.faceValue.pennies > 0)
                      "the maturity date is not in the past" by (time < output.maturityDate )
                      // Don't allow an existing CP state to be replaced by this issuance.
                      "there is no input state" by group.inputs.isEmpty()
                  }
              }

              // TODO: Think about how to evolve contracts over time with new commands.
              else -> throw IllegalArgumentException("Unrecognised command")
          }
      }

   .. sourcecode:: java

      Instant time = tx.getTime();   // Can be null/missing.
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
              if (time == null)
                  throw new IllegalArgumentException("Redemption transactions must be timestamped");
              if (received == null)
                  throw new IllegalStateException("Failed requirement: no cash being redeemed");
              if (input.getMaturityDate().isAfter(time))
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

The first line simply gets the timestamp out of the transaction. Timestamping of transactions is optional, so a time
may be missing here. We check for it being null later.

.. note:: In the Kotlin version, as long as we write a comparison with the transaction time first, the compiler will
   verify we didn't forget to check if it's missing. Unfortunately due to the need for smooth Java interop, this
   check won't happen if we write e.g. ``someDate > time``, it has to be ``time < someDate``. So it's good practice to
   always write the transaction timestamp first.

The first line (first three lines in Java) impose a requirement that there be a single piece of commercial paper in
this group. We do not allow multiple units of CP to be split or merged even if they are owned by the same owner. The
``single()`` method is a static *extension method* defined by the Kotlin standard library: given a list, it throws an
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
Each ``"string" by (expression)`` statement inside a ``requireThat`` turns into an assertion that the given expression is
true, with an exception being thrown that contains the string if not. It's just another way to write out a regular
assertion, but with the English-language requirement being put front and center.

Next, we take one of two paths, depending on what the type of the command object is.

If the command is a ``Move`` command, then we simply verify that the output state is actually present: a move is not
allowed to delete the CP from the ledger. The grouping logic already ensured that the details are identical and haven't
been changed, save for the public key of the owner.

If the command is a ``Redeem`` command, then the requirements are more complex:

1. We want to see that the face value of the CP is being moved as a cash claim against some party, that is, the
   issuer of the CP is really paying back the face value.
2. The transaction must be happening after the maturity date.
3. The commercial paper must *not* be propagated by this transaction: it must be deleted, by the group having no
   output state. This prevents the same CP being considered redeemable multiple times.

To calculate how much cash is moving, we use the ``sumCashOrNull`` utility method. Again, this is an extension method,
so in Kotlin code it appears as if it was a method on the ``List<Cash.State>`` type even though JDK provides no such
method. In Java we see its true nature: it is actually a static method named ``CashKt.sumCashOrNull``. This method simply
returns an ``Amount`` object containing the sum of all the cash states in the transaction output, or null if there were
no such states *or* if there were different currencies represented in the outputs! So we can see that this contract
imposes a limitation on the structure of a redemption transaction: you are not allowed to move currencies in the same
transaction that the CP does not involve. This limitation could be addressed with better APIs, if it were to be a
real limitation.

Finally, we support an ``Issue`` command, to create new instances of commercial paper on the ledger. It likewise
enforces various invariants upon the issuance.

This contract is extremely simple and does not implement all the business logic a real commercial paper lifecycle
management program would. For instance, there is no logic requiring a signature from the issuer for redemption:
it is assumed that any transfer of money that takes place at the same time as redemption is good enough. Perhaps
that is something that should be tightened. Likewise, there is no logic handling what happens if the issuer has gone
bankrupt, if there is a dispute, and so on.

As the prototype evolves, these requirements will be explored and this tutorial updated to reflect improvements in the
contracts API.

How to test your contract
-------------------------

Of course, it is essential to unit test your new nugget of business logic to ensure that it behaves as you expect.
Although you can write traditional unit tests in Java, the platform also provides a *domain specific language*
(DSL) for writing contract unit tests that automates many of the common patterns. This DSL builds on top of JUnit yet
is a Kotlin DSL, and therefore this section will not show Java equivalent code (for Java unit tests you would not
benefit from the DSL and would write them by hand).

We start by defining a new test class, with a basic CP state:

.. container:: codeset

   .. sourcecode:: kotlin

      class CommercialPaperTests {
          val PAPER_1 = CommercialPaper.State(
                  issuance = InstitutionReference(MEGA_CORP, OpaqueBytes.of(123)),
                  owner = MEGA_CORP_KEY,
                  faceValue = 1000.DOLLARS,
                  maturityDate = TEST_TX_TIME + 7.days
          )

          @Test
          fun key_mismatch_at_issue() {
              transactionGroup {
                  transaction {
                      output { PAPER_1 }
                      arg(DUMMY_PUBKEY_1) { CommercialPaper.Commands.Issue() }
                  }

                  expectFailureOfTx(1, "signed by the claimed issuer")
              }
          }
      }

We start by defining a commercial paper state. It will be owned by a pre-defined unit test party, affectionately
called ``MEGA_CORP`` (this constant, along with many others, is defined in ``TestUtils.kt``). Due to Kotin's extensive
type inference, many types are not written out explicitly in this code and it has the feel of a scripting language.
But the types are there, and you can ask IntelliJ to reveal them by pressing Alt-Enter on a "val" or "var" and selecting
"Specify type explicitly".

There are a few things that are unusual here:

* We can specify quantities of money by writing 1000.DOLLARS or 1000.POUNDS
* We can specify quantities of time by writing 7.days
* We can add quantities of time to the TEST_TX_TIME constant, which merely defines an arbitrary java.time.Instant

If you examine the code in the actual repository, you will also notice that it makes use of method names with spaces
in them by surrounding the name with backticks, rather than using underscores. We don't show this here as it breaks the
doc website's syntax highlighting engine.

The ``1000.DOLLARS`` construct is quite simple: Kotlin allows you to define extension functions on primitive types like
Int or Double. So by writing 7.days, for instance, the compiler will emit a call to a static method that takes an int
and returns a ``java.time.Duration``.

As this is JUnit, we must remember to annotate each test method with @Test. Let's examine the contents of the first test.
We are trying to check that it's not possible for just anyone to issue commercial paper in MegaCorp's name. That would
be bad!

The ``transactionGroup`` function works the same way as the ``requireThat`` construct above.

.. note:: This DSL is an example of what Kotlin calls a type safe builder, which you can read about in `the
   documentation for builders <https://kotlinlang.org/docs/reference/type-safe-builders.html>`_. You can mix and match
   ordinary code inside such DSLs so please read the linked page to make sure you fully understand what they are capable
   of.

The code block that follows it is run in the scope of a freshly created ``TransactionGroupForTest`` object, which assists
you with building little transaction graphs and verifying them as a whole. Here, our "group" only actually has a
single transaction in it, with a single output, no inputs, and an Issue command signed by ``DUMMY_PUBKEY_1`` which is just
an arbitrary public key. As the paper claims to be issued by ``MEGA_CORP``, this doesn't match and should cause a
failure. The ``expectFailureOfTx`` method takes a 1-based index (in this case we expect the first transaction to fail)
and a string that should appear in the exception message. Then it runs the ``TransactionGroup.verify()`` method to
invoke all the involved contracts.

It's worth bearing in mind that even though this code may look like a totally different language to normal Kotlin or
Java, it's actually not, and so you can embed arbitrary code anywhere inside any of these blocks.

Let's set up a full trade and ensure it works:

.. container:: codeset

   .. sourcecode:: kotlin

      // Generate a trade lifecycle with various parameters.
      private fun trade(redemptionTime: Instant = TEST_TX_TIME + 8.days,
                        aliceGetsBack: Amount = 1000.DOLLARS,
                        destroyPaperAtRedemption: Boolean = true): TransactionGroupForTest {
        val someProfits = 1200.DOLLARS
        return transactionGroup {
            roots {
                transaction(900.DOLLARS.CASH owned_by ALICE label "alice's $900")
                transaction(someProfits.CASH owned_by MEGA_CORP_KEY label "some profits")
            }

            // Some CP is issued onto the ledger by MegaCorp.
            transaction {
                output("paper") { PAPER_1 }
                arg(MEGA_CORP_KEY) { CommercialPaper.Commands.Issue() }
            }

            // The CP is sold to alice for her $900, $100 less than the face value. At 10% interest after only 7 days,
            // that sounds a bit too good to be true!
            transaction {
                input("paper")
                input("alice's $900")
                output { 900.DOLLARS.CASH owned_by MEGA_CORP_KEY }
                output("alice's paper") { PAPER_1 owned_by ALICE }
                arg(ALICE) { Cash.Commands.Move }
                arg(MEGA_CORP_KEY) { CommercialPaper.Commands.Move }
            }

            // Time passes, and Alice redeem's her CP for $1000, netting a $100 profit. MegaCorp has received $1200
            // as a single payment from somewhere and uses it to pay Alice off, keeping the remaining $200 as change.
            transaction(time = redemptionTime) {
                input("alice's paper")
                input("some profits")

                output { aliceGetsBack.CASH owned_by ALICE }
                output { (someProfits - aliceGetsBack).CASH owned_by MEGA_CORP_KEY }
                if (!destroyPaperAtRedemption)
                    output { PAPER_1 owned_by ALICE }

                arg(MEGA_CORP_KEY) { Cash.Commands.Move }
                arg(ALICE) { CommercialPaper.Commands.Redeem }
            }
        }
    }

In this example we see some new features of the DSL:

* The ``roots`` construct. Sometimes you don't want to write transactions that laboriously issue everything you need
  in a formally correct way. Inside ``roots`` you can create a bunch of states without any contract checking what you're
  doing. As states may not exist outside of transactions, each line inside defines a fake/invalid transaction with the
  given output states, which may be *labelled* with a short string. Those labels can be used later to join transactions
  together.
* The ``.CASH`` suffix. This is a part of the unit test DSL specific to the cash contract. It takes a monetary amount
  like 1000.DOLLARS and then wraps it in a cash ledger state, with some fake data.
* The owned_by `infix function <https://kotlinlang.org/docs/reference/functions.html#infix-notation>`_. This is just
  a normal function that we're allowed to write in a slightly different way, which returns a copy of the cash state
  with the owner field altered to be the given public key. ``ALICE`` is a constant defined by the test utilities that
  is, like ``DUMMY_PUBKEY_1``, just an arbitrary keypair.
* We are now defining several transactions that chain together. We can optionally label any output we create. Obviously
  then, the ``input`` method requires us to give the label of some other output that it connects to.
* The ``transaction`` function can also be given a time, to override the default timestamp on a transaction.

The ``trade`` function is not itself a unit test. Instead it builds up a trade/transaction group, with some slight
differences depending on the parameters provided (Kotlin allows parameters to have default valus). Then it returns
it, unexecuted.

We use it like this:

.. container:: codeset

   .. sourcecode:: kotlin

      @Test
      fun ok() {
          trade().verify()
      }

      @Test
      fun not_matured_at_redemption() {
          trade(redemptionTime = TEST_TX_TIME + 2.days).expectFailureOfTx(3, "must have matured")
      }

That's pretty simple: we just call ``verify`` in order to check all the transactions in the group. If any are invalid,
an exception will be thrown indicating which transaction failed and why. In the second case, we call ``expectFailureOfTx``
again to ensure the third transaction fails with a message that contains "must have matured" (it doesn't have to be
the exact message).


Adding a generation API to your contract
--------------------------------------

Contract classes **must** provide a verify function, but they may optionally also provide helper functions to simplify
their usage. A simple class of functions most contracts provide are *generation functions*, which either create or
modify a transaction to perform certain actions (an action is normally mappable 1:1 to a command, but doesn't have to
be so).

Generation may involve complex logic. For example, the cash contract has a ``generateSpend`` method that is given a set of
cash states and chooses a way to combine them together to satisfy the amount of money that is being sent. In the
immutable-state model that we are using ledger entries (states) can only be created and deleted, but never modified.
Therefore to send $1200 when we have only $900 and $500 requires combining both states together, and then creating
two new output states of $1200 and $200 back to ourselves. This latter state is called the *change* and is a concept
that should be familiar to anyone who has worked with Bitcoin.

As another example, we can imagine code that implements a netting algorithm may generate complex transactions that must
be signed by many people. Whilst such code might be too big for a single utility method (it'd probably be sized more
like a module), the basic concept is the same: preparation of a transaction using complex logic.

For our commercial paper contract however, the things that can be done with it are quite simple. Let's start with
a method to wrap up the issuance process:

.. container:: codeset

   .. sourcecode:: kotlin

      fun generateIssue(issuance: InstitutionReference, faceValue: Amount, maturityDate: Instant): TransactionBuilder {
          val state = State(issuance, issuance.party.owningKey, faceValue, maturityDate)
          return TransactionBuilder(state, WireCommand(Commands.Issue, issuance.party.owningKey))
      }

We take a reference that points to the issuing party (i.e. the caller) and which can contain any internal
bookkeeping/reference numbers that we may require. Then the face value of the paper, and the maturity date. It
returns a ``TransactionBuilder``. A ``TransactionBuilder`` is one of the few mutable classes the platform provides.
It allows you to add inputs, outputs and commands to it and is designed to be passed around, potentially between
multiple contracts.

.. note:: Generation methods should ideally be written to compose with each other, that is, they should take a
   ``TransactionBuilder`` as an argument instead of returning one, unless you are sure it doesn't make sense to
   combine this type of transaction with others. In this case, issuing CP at the same time as doing other things
   would just introduce complexity that isn't likely to be worth it, so we return a fresh object each time: instead,
   an issuer should issue the CP (starting out owned by themselves), and then sell it in a separate transaction.

The function we define creates a ``CommercialPaper.State`` object that mostly just uses the arguments we were given,
but it fills out the owner field of the state to be the same public key as the issuing party. If the caller wants
to issue CP onto the ledger that's immediately owned by someone else, they'll have to create the state themselves.

The returned partial transaction has a ``WireCommand`` object as a parameter. This is a container for any object
that implements the ``Command`` interface, along with a key that is expected to sign this transaction. In this case,
issuance requires that the issuing party sign, so we put the key of the party there.

The ``TransactionBuilder`` constructor we used above takes a variable argument list for convenience. You can pass in
any ``ContractStateRef`` (input), ``ContractState`` (output) or ``Command`` objects and it'll build up the transaction
for you.

What about moving the paper, i.e. reassigning ownership to someone else?

.. container:: codeset

   .. sourcecode:: kotlin

      fun generateMove(tx: TransactionBuilder, paper: StateAndRef<State>, newOwner: PublicKey) {
          tx.addInputState(paper.ref)
          tx.addOutputState(paper.state.copy(owner = newOwner))
          tx.addArg(WireCommand(Commands.Move, paper.state.owner))
      }

Here, the method takes a pre-existing ``TransactionBuilder`` and adds to it. This is correct because typically
you will want to combine a sale of CP atomically with the movement of some other asset, such as cash. So both
generate methods should operate on the same transaction. You can see an example of this being done in the unit tests
for the commercial paper contract.

The paper is given to us as a ``StateAndRef<CommercialPaper.State>`` object. This is exactly what it sounds like:
a small object that has a (copy of) a state object, and also the (txhash, index) that indicates the location of this
state on the ledger.

Finally, we can do redemption.

.. container:: codeset

   .. sourcecode:: kotlin

      @Throws(InsufficientBalanceException::class)
      fun generateRedeem(tx: TransactionBuilder, paper: StateAndRef<State>, wallet: List<StateAndRef<Cash.State>>) {
          // Add the cash movement using the states in our wallet.
          Cash().generateSpend(tx, paper.state.faceValue, paper.state.owner, wallet)
          tx.addInputState(paper.ref)
          tx.addArg(WireCommand(CommercialPaper.Commands.Redeem, paper.state.owner))
      }

Here we can see an example of composing contracts together. When an owner wishes to redeem the commercial paper, the
issuer (i.e. the caller) must gather cash from its wallet and send the face value to the owner of the paper.

.. note:: **Exercise for the reader**: In this early, simplified model of CP there is no built in support
   for rollover. Extend the contract code to support rollover as well as redemption (reissuance of the paper with a
   higher face value without any transfer of cash)

The *wallet* is a concept that may be familiar from Bitcoin and Ethereum. It is simply a set of cash states that are
owned by the caller. Here, we use the wallet to update the partial transaction we are handed with a movement of cash
from the issuer of the commercial paper to the current owner. If we don't have enough quantity of cash in our wallet,
an exception is thrown. And then we add the paper itself as an input, but, not an output (as we wish to delete it
from the ledger permanently). Finally, we add a Redeem command that should be signed by the owner of the commercial
paper.

A ``TransactionBuilder`` is not by itself ready to be used anywhere, so first, we must convert it to something that
is recognised by the network. The most important next step is for the participating entities to sign it using the
``signWith()`` method. This takes a keypair, serialises the transaction, signs the serialised form and then stores the
signature inside the ``TransactionBuilder``. Once all parties have signed, you can call ``TransactionBuilder.toSignedTransaction()``
to get a ``SignedTransaction`` object. This is an immutable form of the transaction that's ready for *timestamping*,
which can be done using a ``TimestamperClient``. To learn more about that, please refer to the
:doc:`protocol-state-machines` document.

You can see how transactions flow through the different stages of construction by examining the commercial paper
unit tests.

Non-asset-oriented based smart contracts
----------------------------------------

It is important to distinguish between the idea of a legal contract vs a code contract. In this document we use the
term *contract* as a shorthand for code contract: a small module of widely shared, simultaneously executed business
logic that uses standardised APIs and runs in a sandbox.

Although this tutorial covers how to implement an owned asset, there is no requirement that states and code contracts
*must* be concerned with ownership of an asset. It is better to think of states as representing useful facts about the
world, and (code) contracts as imposing logical relations on how facts combine to produce new facts.

For example, in the case that the transfer of an asset cannot be performed entirely on-ledger, one possible usage of
the model is to implement a delivery-vs-payment lifecycle in which there is a state representing an intention to trade
and two other states that can be interpreted by off-ledger platforms as firm instructions to move the respective asset
or cash - and a final state in which the exchange is marked as complete. The key point here is that the two off-platform
instructions form pa rt of the same Transaction and so either both are signed (and can be processed by the off-ledger
systems) or neither are.

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