Glossary
========

Artemis
    The message queuing middleware used within Corda
Attachment
    An attachment is a piece of data that can be referred to within a transaction but is never marked as used, i.e. can be referred to multiple times.
Clause
    A clause is a reusable piece of code that performs transaction verification
Command
    Used for directing a transaction, sometimes containing command data. For example, a Cash contract may include an Issue command, which signals that one of the purposes of the transaction is to issue cash on to the ledger (i.e. by creating one or more Cash outputs, without any corresponding inputs.)
Composite Key
    A tree data structure containing regular cryptographic public keys. It allows expressing threshold signature requirements, e.g. “either Alice or Bob” needs to sign.
Contract
    A contract is code that specifies how states are to be created and used within Corda.
Corda
    A Distributed Ledger for recording and managing financial agreements
CorDapp
    A Corda Distributed Application. A shared ledger application on Corda consisting of components from: State objects (data), Contract Code (allowable operations),  Flows (aka Transaction Flows, the business logic choreography), any necessary APIs, wallet plugins, and UI components.
Cordformation
    A gradle plugin that can be configured via your gradle buildscripts to locally deploy a set of Corda nodes
Counterparty
    The other party in a financial or contract transaction
DSL
    Domain Specific Language - a language specifically designed for a particular domain. Kotlin allows the definition of DSLs and they are used within the Corda framework.
Flow
    The set of instructions which determines how nodes communicate over Corda with the goal of arriving at consensus.
Fungible
    An item that can be exchanged or interchanged for another identical item, e.g. Cash (as a $10 note can be exchanged for two $5 notes), but not diamonds (as they tend to have very identifying characteristics).
Gradle
    Industry standard build and deployment tool. Used extensively within Corda.
Kotlin
    The language used to code Corda. Fully compatible with any JVM language, including (obviously) Java.
Kryo
    The serialisation mechanism used within Corda - which is subject to change in a future release.
Input
    In Corda terms, an input state is one that is used and consumed within a transaction. Once consumed, it cannot be re-used.
JVM
    The Java Virtual Machine. The "computing machine" that Corda is executed within.
Lizard People
    I would put their true identity in here but I fear the overlords may banish me.
Merkle Tree
    A tree where each non leaf node is tagged with a hash of the data within that node and also the nodes beneath it. This ensures that the data in any node cannot be modified without causing hash verification failures in the parent node, and therefore all subsequent parents.
Network Map Service
    A network service that maintains a map of node names and their network locations. Used by nodes such that they can communicate with other parties directly (after locating).
Node
    A communication point on the Corda network and also the provider of the virtual machine in which Corda runs.
Notary Service
    A network service that guarantees that it will only add its signature to transactions if all input states have not been consumed
Oracle
    An oracle is a well known service that signs transactions if they state a fact and that fact is considered to be true. They may also optionally also provide the facts.
Output
    In the Corda model, an output is a state generated from a transaction (note that multiple outputs can be generated from one transaction). They are then used as inputs for subsequent transactions.
Protocol
    The old name for a Corda "Flow"
Quasar
    A library that provides performant lightweight threads that can be suspended and restored extremely quickly.
R3
    The consortium behind Corda
SIMM
    Standard Initial Margin Model. A way of determining a counterparty's margin payment to another counterparty based on a collection of trades such that, in the event of default, the receiving counterparty has limited exposure.
Serialization
    Object serialization is the process of converting objects into a stream of bytes and, deserialization, the reverse process.
Service Hub
    A hub in each Corda node that manages the services upon which other components of the node depend. Services may include facilities for identity management, storage management, network map management etc.
Signed Transaction
    A signed transaction is a transaction that has been agreed by all parties relevant to that transaction as well as optionally a notary if relevant.
State
    An element of data that is output from one transaction and then used / consumed in another transaction. States can only be consumed once and this confirmation is performed by the Notary service.
Transaction
    A transaction is the means by which states are both created and consumed. They can be designed to accept between zero and any number of input states, and then generate between zero and any number of output states.
UTXO
    Unspent Transaction Output. First introduced by the bitcoin model, an unspent transaction is data that has been output from a transaction but not yet used in another transaction.
Verify
    To confirm that the transaction is valid by ensuring the the outputs are correctly derived from the inputs combined with the command of the transaction.
Whitelisting
    To indicate that a class is intended to be passed between nodes or between a node and an RPC client, it is added to a whitelist.  This prevents the node presenting a large surface area of all classes in all dependencies of the node as containing possible vulnerabilities.