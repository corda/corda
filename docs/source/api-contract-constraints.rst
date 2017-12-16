API: Contract Constraints
=========================

.. note:: Before reading this page, you should be familiar with the key concepts of :doc:`key-concepts-contracts`.

.. contents::

Contract constraints
--------------------
Transaction states specify a constraint over the contract that will be used to verify it.  For a transaction to be
valid, the ``verify`` function associated with each state must run successfully. However, for this to be secure, it is
not sufficient to specify the ``verify`` function by name as there may exist multiple different implementations with
the same method signature and enclosing class. Contract constraints solve this problem by allowing a contract developer
to constrain which ``verify`` functions out of the universe of implementations can be used (i.e. the universe is
everything that matches the signature and contract constraints restrict this universe to a subset).

A typical constraint is the hash of the CorDapp JAR that contains the contract and states but will in future releases
include constraints that require specific signers of the JAR, or both the signer and the hash. Constraints can be
specified when constructing a transaction; if unspecified, an automatic constraint is used.

A ``TransactionState`` has a ``constraint`` field that represents that state's attachment constraint. When a party
constructs a ``TransactionState`` without specifying the constraint parameter a default value
(``AutomaticHashConstraint``) is used. This default will be automatically resolved to a specific
``HashAttachmentConstraint`` that contains the hash of the attachment which contains the contract of that
``TransactionState``. This automatic resolution occurs when a ``TransactionBuilder`` is converted to a
``WireTransaction``. This reduces the boilerplate involved in finding a specific hash constraint when building a
transaction.

It is possible to specify the constraint explicitly with any other class that implements the ``AttachmentConstraint``
interface. To specify a hash manually the ``HashAttachmentConstraint`` can be used and to not provide any constraint
the ``AlwaysAcceptAttachmentConstraint`` can be used - though this is intended for testing only. An example below
shows how to construct a ``TransactionState`` with an explicitly specified hash constraint from within a flow:

.. sourcecode:: java

     // Constructing a transaction with a custom hash constraint on a state
     TransactionBuilder tx = new TransactionBuilder()

     Party notaryParty = ... // a notary party
     DummyState contractState = new DummyState()
     SecureHash myAttachmentsHash = serviceHub.cordappProvider.getContractAttachmentID(DummyContract.PROGRAM_ID)
     TransactionState transactionState = new TransactionState(contractState, DummyContract.Companion.getPROGRAMID(), notaryParty, new AttachmentHashConstraint(myAttachmentsHash))

     tx.addOutputState(transactionState)
     WireTransaction wtx = tx.toWireTransaction(serviceHub) // This is where an automatic constraint would be resolved
     LedgerTransaction ltx = wtx.toLedgerTransaction(serviceHub)
     ltx.verify() // Verifies both the attachment constraints and contracts

This mechanism exists both for integrity and security reasons. It is important not to verify against the wrong contract,
which could happen if the wrong version of the contract is attached. More importantly when resolving transaction chains
there will, in a future release, be attachments loaded from the network into the attachment sandbox that are used
to verify the transaction chain. Ensuring the attachment used is the correct one ensures that the verification is
tamper-proof by providing a fake contract.

CorDapps as attachments
-----------------------

CorDapp JARs (:doc:`cordapp-overview`) that are installed to the node and contain classes implementing the ``Contract``
interface are automatically loaded into the ``AttachmentStorage`` of a node at startup.

After CorDapps are loaded into the attachment store the node creates a link between contract classes and the attachment
that they were loaded from. This makes it possible to find the attachment for any given contract. This is how the
automatic resolution of attachments is done by the ``TransactionBuilder`` and how, when verifying the constraints and
contracts, attachments are associated with their respective contracts.

Implementations of AttachmentConstraint
---------------------------------------

There are three implementations of ``AttachmentConstraint`` with more planned in the future.

``AlwaysAcceptAttachmentConstraint``: Any attachment (except a missing one) will satisfy this constraint.

``AutomaticHashConstraint``: This will be resolved to a ``HashAttachmentConstraint`` when a ``TransactionBuilder`` is
converted to a ``WireTransaction``. The ``HashAttachmentConstraint`` will include the attachment hash of the CorDapp
that contains the ``ContractState`` on the ``TransactionState.contract`` field.

``HashAttachmentConstraint``: Will require that the hash of the attachment containing the contract matches the hash
stored in the constraint.

We plan to add a future ``AttachmentConstraint`` that will only be satisfied by the presence of signatures on the
attachment JAR. This allows for trusting of attachments from trusted entities.

Limitations
-----------

An ``AttachmentConstraint`` is verified by running the ``AttachmentConstraint.isSatisfiedBy`` method. When this is called
it is provided only the relevant attachment by the transaction that is verifying it.

Testing
-------

Since all tests involving transactions now require attachments it is also required to load the correct attachments
for tests. Unit test environments in JVM ecosystems tend to use class directories rather than JARs, and so CorDapp JARs
typically aren't built for testing. Requiring this would add significant complexity to the build systems of Corda
and CorDapps, so the test suite has a set of convenient functions to generate CorDapps from package names or
to specify JAR URLs in the case that the CorDapp(s) involved in testing already exist.

MockNetwork/MockNode
********************

The simplest way to ensure that a vanilla instance of a MockNode generates the correct CorDapps is to use the
``cordappPackages`` constructor parameter (Kotlin) or the ``setCordappPackages`` method on ``MockNetworkParameters`` (Java)
when creating the MockNetwork. This will cause the ``AbstractNode`` to use the named packages as sources for CorDapps. All files
within those packages will be zipped into a JAR and added to the attachment store and loaded as CorDapps by the
``CordappLoader``. An example of this usage would be:

.. sourcecode:: java

    class SomeTestClass {
         MockNetwork network = null

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