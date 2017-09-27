Contract Constraints
====================

A basic understanding of contract key concepts, which can be found :doc:`here </key-concepts-contracts>`
is required reading for this page.

Transaction states specify a constraint over the contract that will be used to verify it.  For a transaction to be
valid, the verify() function associated with each states must run successfully. However, for this to be secure, it is
not sufficient to specify the verify() function by name as there may exist multiple different implementations with the
same method signature and enclosing class. Contract constraints solve this problem by allowing a contract developer to
constrain which verify() functions out of the universe of implementations which match the signature can be executed.

A typical constraint is the hash of the CorDapp JAR that contains the contract and states but will in future releases
include constraints that require specific signers of the JAR, or both the signer and the hash. Custom constraints are
specified when constructing a transaction, if unspecified an automatic constraint is used.

Constraints are specified for each ``Transactionstate``. If unspecified the ``TransactionState`` has a default
``AutomaticHashConstraint``. This default will be automatically resolved to a specific ``HashAttachmentConstraint`` that
contains the hash of the attachment which contrains the contract of that ``TransactionState``. This automatic resolution
occurs when a ``TransactionBuilder`` is converted to a ``WireTransaction``. This reduces boilerplate of finding a
specific hash constraints when building a transaction.

It is possible to specify the constraint manually with any other class that implements the ``AttachmentConstraint``
interface. To specify a hash manually the ``HashAttachmentConstraint`` can be used and to not provide any constraint
the ``AlwaysAcceptAttachmentConstraint`` can be used - though this is intended for testing only.

This mechanism exists both for integrity and security reasons. It is important not to verify against the wrong contract,
which could happen if the wrong version of the contract is attached. More importantly when resolving transaction chains
there will, in a future release, be attachments loaded from the network into the an attachment sandbox that are used
to verify the transaction chain. Ensuring the attachment used is the correct one ensures that the verification will
not be tamperable by providing a fake contract.

CorDapps as attachments
-----------------------

CorDapps JARs (:doc:`cordapp-overview`) that are installed to the node and contain classes implementing the ``Contract``
interface are automatically loaded into the ``AttachmentStorage`` of a node at startup.

When CorDapp JARs are loaded any contract classes contained within are associated with it so that it is possible to
find the attachment for any given contract. This is how the automatic resolution of attachments is done by the
TransactionBuilder and how, when verifying the constraints and contracts, attachments are associated with their
respective contracts.

Implementations
---------------

There are currently three implemented ``AttachmentConstraints`` provided by default in Corda with one more planned.

``AlwaysAcceptAttachmentConstraint``: Any attachment (except a missing one) will satisfy this constraint.

``AutomaticHashConstraint``: This will be resolved in ``TransactionBuilder`` to a ``HashAttachmentConstraint``. The
result will be the attachment hash of the CorDapp that contains the ``ContractState`` on the
``TransactionState.contract`` field.

``HashAttachmentConstraint``: Will require that the hash of the attachment containing the contract matches the hash
stored in the constraint.

There will be a future constraint to accept the contract if it is signed by a certain party, and one to require both a
specific signature and hash.

Testing
-------

Since all tests involving transactions now require attachments it is also required to load the correct attachments
for tests. Of course this isn't possible or convenient so the test suite has a set of functions to generate CorDapps
from package names or to specify JAR URLs.

MockNetwork/MockNode
********************

The most simple way to ensure that a vanilla instance of a MockNode generates the correct CorDapps is to make a call
to ``setCordappPackages`` before the MockNetwork/Node are created and then ``unsetCordappPackages`` after the test
has finished. These calls will cause the ``AbstractNode`` to use the named packages as sources for CorDapps. All files
within those packages will be zipped into a JAR and added to the attachment store and loaded as CorDapps by the
``CordappLoader``.

MockServices
************

If your test uses a ``MockServices`` directly you can instantiate it using a constructor that takes a list of packages
to use as CorDapps using the ``cordappPackages`` parameter.

Driver
******

The driver takes a parameter called ``extraCordappPackagesToScan`` which is a list of packages to use as CorDapps.

Full Nodes
**********

When testing against full nodes simply place your CorDapp into the plugins directory of the node.
