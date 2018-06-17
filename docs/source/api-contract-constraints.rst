API: Contract Constraints
=========================

.. note:: Before reading this page, you should be familiar with the key concepts of :doc:`key-concepts-contracts`.

.. contents::

Contract constraints
--------------------

Corda separates verification of states from their definition. Whilst you might have expected the ``ContractState``
interface to define a verify method, or perhaps to do verification logic in the constructor, instead it is primarily
done by a method on a ``Contract`` class. This is because what we're actually checking is the
validity of a *transaction*, which is more than just whether the individual states are internally consistent.
The transition between two valid states may be invalid, if the rules of the application are not being respected.
For instance, two cash states of $100 and $200 may both be internally valid, but replacing the first with the second
isn't allowed unless you're a cash issuer - otherwise you could print money for free.

For a transaction to be valid, the ``verify`` function associated with each state must run successfully. However,
for this to be secure, it is not sufficient to specify the ``verify`` function by name as there may exist multiple
different implementations with the same method signature and enclosing class. This normally will happen as applications
evolve, but could also happen maliciously.

Contract constraints solve this problem by allowing a contract developer to constrain which ``verify`` functions out of
the universe of implementations can be used (i.e. the universe is everything that matches the signature and contract
constraints restrict this universe to a subset). Constraints are satisfied by attachments (JARs). You are not allowed to
attach two JARs that both define the same application due to the *no overlap rule*. This rule specifies that two
attachment JARs may not provide the same file path. If they do, the transaction is considered invalid. Because each
state specifies both a constraint over attachments *and* a Contract class name to use, the specified class must appear
in only one attachment.

So who picks the attachment to use? It is chosen by the creator of the transaction that has to satisfy input constraints.
The transaction creator also gets to pick the constraints used by any output states, but the contract logic itself may
have opinions about what those constraints are - a typical contract would require that the constraints are propagated,
that is, the contract will not just enforce the validity of the next transaction that uses a state, but *all successive
transactions as well*. The constraints mechanism creates a balance of power between the creator of data on
the ledger and the user who is trying to edit it, which can be very useful when managing upgrades to Corda applications.

There are two ways of handling upgrades to a smart contract in Corda:

1. *Implicit:* By allowing multiple implementations of the contract ahead of time, using constraints.
2. *Explicit:* By creating a special *contract upgrade transaction* and getting all participants of a state to sign it using the
   contract upgrade flows.

This article focuses on the first approach. To learn about the second please see :doc:`upgrading-cordapps`.

The advantage of pre-authorising upgrades using constraints is that you don't need the heavyweight process of creating
upgrade transactions for every state on the ledger. The disadvantage is that you place more faith in third parties,
who could potentially change the app in ways you did not expect or agree with. The advantage of using the explicit
upgrade approach is that you can upgrade states regardless of their constraint, including in cases where you didn't
anticipate a need to do so. But it requires everyone to sign, requires everyone to manually authorise the upgrade,
consumes notary and ledger resources, and is just in general more complex.

How constraints work
--------------------

Starting from Corda 3 there are two types of constraint that can be used: hash and zone whitelist. In future
releases a third type will be added, the signature constraint.

**Hash constraints.** The behaviour provided by public blockchain systems like Bitcoin and Ethereum is that once data is placed on the ledger,
the program that controls it is fixed and cannot be changed. There is no support for upgrades at all. This implements a
form of "code is law", assuming you trust the community of that blockchain to not release a new version of the platform
that invalidates or changes the meaning of your program.

This is supported by Corda using a hash constraint. This specifies exactly one hash of a CorDapp JAR that contains the
contract and states any consuming transaction is allowed to use. Once such a state is created, other nodes will only
accept a transaction if it uses that exact JAR file as an attachment. By implication, any bugs in the contract code
or state definitions cannot be fixed except by using an explicit upgrade process via ``ContractUpgradeFlow``.

.. note:: Corda does not support any way to create states that can never be upgraded at all, but the same effect can be
   obtained by using a hash constraint and then simply refusing to agree to any explicit upgrades. Hash
   constraints put you in control by requiring an explicit agreement to any upgrade.

**Zone constraints.** Often a hash constraint will be too restrictive. You do want the ability to upgrade an app,
and you don't mind the upgrade taking effect "just in time" when a transaction happens to be required for other business
reasons. In this case you can use a zone constraint. This specifies that the network parameters of a compatibility zone
(see :doc:`network-map`) is expected to contain a map of class name to hashes of JARs that are allowed to provide that
class. The process for upgrading an app then involves asking the zone operator to add the hash of your new JAR to the
parameters file, and trigger the network parameters upgrade process. This involves each node operator running a shell
command to accept the new parameters file and then restarting the node. Node owners who do not restart their node in
time effectively stop being a part of the network.

**Signature constraints.** These are not yet supported, but once implemented they will allow a state to require a JAR
signed by a specified identity, via the regular Java jarsigner tool. This will be the most flexible type
and the smoothest to deploy: no restarts or contract upgrade transactions are needed.

**Defaults.** The default constraint type is either a zone constraint, if the network parameters in effect when the
transaction is built contain an entry for that contract class, or a hash constraint if not.

A ``TransactionState`` has a ``constraint`` field that represents that state's attachment constraint. When a party
constructs a ``TransactionState``, or adds a state using ``TransactionBuilder.addOutput(ContractState)`` without
specifying the constraint parameter, a default value (``AutomaticHashConstraint``) is used. This default will be
automatically resolved to a specific ``HashAttachmentConstraint`` or a ``WhitelistedByZoneAttachmentConstraint``.
This automatic resolution occurs when a ``TransactionBuilder`` is converted to a ``WireTransaction``. This reduces
the boilerplate that would otherwise be involved.

Finally, an ``AlwaysAcceptAttachmentConstraint`` can be used which accepts anything, though this is intended for
testing only.

Please note that the ``AttachmentConstraint`` interface is marked as ``@DoNotImplement``. You are not allowed to write
new constraint types. Only the platform may implement this interface. If you tried, other nodes would not understand
your constraint type and your transaction would not verify.

.. warning:: An AlwaysAccept constraint is effectively the same as disabling security for those states entirely.
   Nothing stops you using this constraint in production, but that degrades Corda to being effectively a form
   of distributed messaging with optional contract logic being useful only to catch mistakes, rather than potentially
   malicious action. If you are deploying an app for which malicious actors aren't in your threat model, using an
   AlwaysAccept constraint might simplify things operationally.

An example below shows how to construct a ``TransactionState`` with an explicitly specified hash constraint from within
a flow:

.. sourcecode:: java

   // Constructing a transaction with a custom hash constraint on a state
   TransactionBuilder tx = new TransactionBuilder();

   Party notaryParty = ... // a notary party
   DummyState contractState = new DummyState();

   SecureHash myAttachmentHash = SecureHash.parse("2b4042aed7e0e39d312c4c477dca1d96ec5a878ddcfd5583251a8367edbd4a5f");
   TransactionState transactionState = new TransactionState(contractState, DummyContract.Companion.getPROGRAMID(), notaryParty, new AttachmentHashConstraint(myAttachmentHash));

   tx.addOutputState(transactionState);
   WireTransaction wtx = tx.toWireTransaction(serviceHub);  // This is where an automatic constraint would be resolved.
   LedgerTransaction ltx = wtx.toLedgerTransaction(serviceHub);
   ltx.verify(); // Verifies both the attachment constraints and contracts

Hard-coding the hash of your app in the code itself can be pretty awkward, so the API also offers the ``AutomaticHashConstraint``.
This isn't a real constraint that will appear in a transaction: it acts as a marker to the ``TransactionBuilder`` that
you require the hash of the node's installed app which supplies the specified contract to be used. In practice, when using
hash constraints, you almost always want "whatever the current code is" and not a hard-coded hash. So this automatic
constraint placeholder is useful.

FinalityFlow
------------

It's possible to encounter contract contraint issues when notarising transactions with the ``FinalityFlow`` on a network
containing multiple versions of the same CorDapp. This will happen when using hash contraints or with zone contraints
if the zone whitelist has missing CorDapp versions. If a participating party fails to validate the **notarised** transaction
then we have a scenerio where the members of the network do not have a consistent view of the ledger.

Therfore, if the finality handler flow (which is run on the counterparty) errors for any reason it will always be sent to
the flow hospital. From there it's suspended waiting to be retried on node restart. This gives the node operator the opportunity
to recover from those errors, which in the case of contract constraint voilations means either updating the CorDapp or
adding its hash to the zone whitelist.

.. note:: This is a temporary issue in the current version of Corda, until we implement some missing features which will
   enable a seemless handling of differences in CorDapp versions.

CorDapps as attachments
-----------------------

CorDapp JARs (see :doc:`cordapp-overview`) that are installed to the node and contain classes implementing the ``Contract``
interface are automatically loaded into the ``AttachmentStorage`` of a node at startup.

After CorDapps are loaded into the attachment store the node creates a link between contract classes and the attachment
that they were loaded from. This makes it possible to find the attachment for any given contract. This is how the
automatic resolution of attachments is done by the ``TransactionBuilder`` and how, when verifying the constraints and
contracts, attachments are associated with their respective contracts.

.. note:: The obvious way to write a CorDapp is to put all you states, contracts, flows and support code into a single
   Java module. This will work but it will effectively publish your entire app onto the ledger. That has two problems:
   (1) it is inefficient, and (2) it means changes to your flows or other parts of the app will be seen by the ledger
   as a "new app", which may end up requiring essentially unnecessary upgrade procedures. It's better to split your
   app into multiple modules: one which contains just states, contracts and core data types. And another which contains
   the rest of the app. See :ref:`cordapp-structure`.

Testing
-------

Since all tests involving transactions now require attachments it is also required to load the correct attachments
for tests. Unit test environments in JVM ecosystems tend to use class directories rather than JARs, and so CorDapp JARs
typically aren't built for testing. Requiring this would add significant complexity to the build systems of Corda
and CorDapps, so the test suite has a set of convenient functions to generate CorDapps from package names or
to specify JAR URLs in the case that the CorDapp(s) involved in testing already exist. You can also just use
``AlwaysAcceptAttachmentConstraint`` in your tests to disable the constraints mechanism.

MockNetwork/MockNode
********************

The simplest way to ensure that a vanilla instance of a MockNode generates the correct CorDapps is to use the
``cordappPackages`` constructor parameter (Kotlin) or the ``setCordappPackages`` method on ``MockNetworkParameters`` (Java)
when creating the MockNetwork. This will cause the ``AbstractNode`` to use the named packages as sources for CorDapps. All files
within those packages will be zipped into a JAR and added to the attachment store and loaded as CorDapps by the
``CordappLoader``.

An example of this usage would be:

.. sourcecode:: java

    class SomeTestClass {
         MockNetwork network = null;

         @Before
         void setup() {
             network = new MockNetwork(new MockNetworkParameters().setCordappPackages(Arrays.asList("com.domain.cordapp")))
         }

         ... // Your tests go here
    }


MockServices
************

If your test uses a ``MockServices`` directly you can instantiate it using a constructor that takes a list of packages
to use as CorDapps using the ``cordappPackages`` parameter.

.. sourcecode:: java

    MockServices mockServices = new MockServices(Arrays.asList("com.domain.cordapp"))

However - there is an easier way! If your unit tests are in the same package as the contract code itself, then you
can use the no-args constructor of ``MockServices``. The package to be scanned for CorDapps will be the same as the
the package of the class that constructed the object. This is a convenient default.

Driver
******

The driver takes a parameter called ``extraCordappPackagesToScan`` which is a list of packages to use as CorDapps.

.. sourcecode:: java

   driver(new DriverParameters().setExtraCordappPackagesToScan(Arrays.asList("com.domain.cordapp"))) ...

Full Nodes
**********

When testing against full nodes simply place your CorDapp into the cordapps directory of the node.

Debugging
---------
If an attachment constraint cannot be resolved, a ``MissingContractAttachments`` exception is thrown. There are two
common sources of ``MissingContractAttachments`` exceptions:

Not setting CorDapp packages in tests
*************************************
You are running a test and have not specified the CorDapp packages to scan. See the instructions above.

Wrong fully-qualified contract name
***********************************
You are specifying the fully-qualified name of the contract incorrectly. For example, you've defined ``MyContract`` in
the package ``com.mycompany.myapp.contracts``, but the fully-qualified contract name you pass to the
``TransactionBuilder`` is ``com.mycompany.myapp.MyContract`` (instead of ``com.mycompany.myapp.contracts.MyContract``).