Data model
==========

This article covers the data model: how *states*, *transactions* and *code contracts* interact with each other and
how they are represented in software.

Overview
--------

We begin with the idea of a global ledger. In our model although the ledger is shared, it is not always the case that
transactions and ledger entries are globally visible. In cases where a set of transactions stays within a small subgroup of
users it should be possible to keep the relevant data purely within that group.

To ensure consistency in a global, shared system where not all data may be visible to all participants, we rely
heavily on secure hashes like SHA-256 to identify things. The ledger is defined as a set of immutable **states**, which
are created and destroyed by digitally signed **transactions**. Each transaction points to a set of states that it will
consume/destroy, these are called **inputs**, and contains a set of new states that it will create, these are called
**outputs**.

States contain arbitrary data, but they always contain at minimum a hash of the bytecode of a
**contract code** file, which is a program expressed in JVM byte code that runs sandboxed inside a Java virtual machine.
Contract code (or just "contracts" in the rest of this document) are globally shared pieces of business logic.

.. note:: In the current code dynamic loading of contracts is not implemented, so states currently point at
   statically created object instances. This will change in the near future.

Contracts define a **verify function**, which is a pure function given the entire transaction as input. To be considered
valid, the transaction must be **accepted** by the verify function of every contract pointed to by the input and output
states.

Beyond inputs and outputs, transactions may also contain **commands**, small data packets that
the platform does not interpret itself but which can parameterise execution of the contracts. They can be thought of as
arguments to the verify function. Each command has a list of **public keys** associated with it. The platform ensures
that the transaction is signed by every key listed in the commands before the contracts start to execute. Thus, a verify
function can trust that all listed keys have signed the transaction but is responsible for verifying that any keys required
for the transaction to be valid from the verify function's perspective are included in the list. Public keys
may be random/identityless for privacy, or linked to a well known legal identity, for example via a
*public key infrastructure* (PKI).

.. note:: Linkage of keys with identities via a PKI is only partially implemented in the current code.

Commands are always embedded inside a transaction. Sometimes, there's a larger piece of data that can be reused across
many different transactions. For this use case, we have **attachments**. Every transaction can refer to zero or more
attachments by hash. Attachments are always ZIP/JAR files, which may contain arbitrary content. These files are
then exposed on the classpath and so can be opened by contract code in the same manner as any JAR resources
would be loaded.

.. note:: Attachments must be opened explicitly in the current code.

Note that there is nothing that explicitly binds together specific inputs, outputs, commands or attachments. Instead
it's up to the contract code to interpret the pieces inside the transaction and ensure they fit together correctly. This
is done to maximise flexibility for the contract developer.

Transactions may sometimes need to provide a contract with data from the outside world. Examples may include stock
prices, facts about events or the statuses of legal entities (e.g. bankruptcy), and so on. The providers of such
facts are called **oracles** and they provide facts to the ledger by signing transactions that contain commands they
recognise, or by creating signed attachments. The commands contain the fact and the signature shows agreement to that fact.

Time is also modelled as a fact, with the signature of a special kind of service called a **notary**. A notary is
a (very likely) decentralised service which fulfils the role that miners play in other blockchain systems:
notaries ensure only one transaction can consume any given output. Additionally they may verify a **timestamping
command** placed inside the transaction, which specifies a time window in which the transaction is considered
valid for notarisation. The time window can be open ended (i.e. with a start but no end or vice versa). In this
way transactions can be linked to the notary's clock.

It is possible for a single Corda network to have multiple competing notaries. Each state points to the notary that
controls it. Whilst a single transaction may only consume states if they are all controlled by the same notary,
a special type of transaction is provided that moves a state (or set of states) from one notary to another.

.. note:: Currently the platform code will not re-assign states to a single notary as needed for you, in case of
   a mismatch. This is a future planned feature.

As the same terminology often crops up in different distributed ledger designs, let's compare this to other
systems you may be familiar with. The key differences are:

* Improved contract flexibility vs Bitcoin
* Improved scalability vs Ethereum, as well as ability to keep parts of the transaction graph private (yet still uniquely addressable)
* No reliance on proof of work
* Re-use of existing sandboxing virtual machines
* Use of type safe GCd implementation languages
* Simplified auditing

Comparison with Bitcoin
-----------------------

Similarities:

* The basic notion of immutable states that are consumed and created by transactions is the same.
* The notion of transactions having multiple inputs and outputs is the same. Bitcoin sometimes refers to the ledger
  as the unspent transaction output set (UTXO set) as a result.
* Like in Bitcoin, a contract is pure function. Contracts do not have storage or the ability to interact with anything.
  Given the same transaction, a contract's accept function always yields exactly the same result.
* Bitcoin output scripts are parameterised by the input scripts in the spending transaction. This is somewhat similar
  to our notion of a *command*.
* Bitcoin has a global distributed notary service; that's the famous block chain. However, there is only one. Whilst
  there is a notion of a "side chain", this isn't integrated with the core Bitcoin data model and thus adds large
  amounts of additional complexity meaning in practice side chains are not used.
* Bitcoin transactions, like ours, refer to the states they consume by using a (txhash, index) pair. The Bitcoin
  protocol calls these "outpoints". In our code they are known as ``StateRefs`` but the concept is identical.
* Bitcoin transactions have an associated timestamp (the time at which they are mined).

Differences:

* A Bitcoin transaction has a single, rigid data format. A "state" in Bitcoin is always a (quantity of bitcoin, script)
  pair and cannot hold any other data. Some people have been known to try and hack around this limitation by embedding
  data in semi-standardised places in the contract code so the data can be extracted through pattern matching, but this
  is a poor approach. Our states can include arbitrary typed data.
* A Bitcoin transaction's acceptance is controlled only by the contract code in the consumed input states. In practice
  this has proved limiting. Our transactions invoke not only input contracts but also the contracts of the outputs.
* A Bitcoin script can only be given a fixed set of byte arrays as the input. This means there's no way for a contract
  to examine the structure of the entire transaction, which severely limits what contracts can do.
* Our contracts are Turing-complete and can be written in any ordinary programming language that targets the JVM.
* Our transactions and contracts get their time from an attached timestamp rather than a block. This is
  important given that we use block-free conflict resolution algorithms. The timestamp can be arbitrarily precise.
* We use the term "contract" to refer to a bundle of business logic that may handle various different tasks, beyond
  transaction verification. For instance, currently our contracts also include code for creating valid transactions
  (this is often called "wallet code" in Bitcoin).

Comparison with Ethereum
------------------------

Similarities:

* Like Ethereum, code runs inside a relatively powerful virtual machine and can contain complex logic. Non-assembly
  based programming languages can be used for contract programming.
* They are both intended for the modelling of many different kinds of financial contract.

Differences:

* The term "contract" in Ethereum refers to an *instantiation* of a program that is replicated and maintained by
  every participating node. This instantiation is very much like an object in an OO program: it can receive and send
  messages, update local storage and so on. In contrast, we use the term "contract" to refer to a set of functions, only
  one of which is a part of keeping the system synchronised (the verify function). That function is pure and
  stateless i.e. it may not interact with any other part of the system whilst executing.
* There is no notion of an "account", as there is in Ethereum.
* As contracts don't have any kind of mutable storage, there is no notion of a "message" as in Ethereum.
* Ethereum claims to be a platform not only for financial logic, but literally any kind of application at all. Our
  platform considers non-financial applications to be out of scope.

Rationale for and tradeoffs in adopting a UTXO-style model
----------------------------------------------------------

As discussed above, Corda uses the so-called "UTXO set" model (unspent transaction output). In this model, the database
does not track accounts or balances. Instead all database entries are immutable. An entry is either spent or not spent
but it cannot be changed. In Bitcoin, spentness is implemented simply as deletion – the inputs of an accepted transaction
are deleted and the outputs created.

This approach has some advantages and some disadvantages, which is why some platforms like Ethereum have tried
(or are trying) to abstract this choice away and support a more traditional account-like model.  We have explicitly
chosen *not* to do this and our decision to adopt a UTXO-style model is a deliberate one.  In the section below,
the rationale for this decision and its pros and cons of this choice are outlined.

Rationale
---------

Corda, in common with other blockchain-like platforms, is designed to bring parties to shared sets of data into
consensus as to the existence, content and allowable evolutions of those data sets. However, Corda is designed with the
explicit aim of avoiding, to the extent possible, the scalability and privacy implications that arise from those platforms'
decisions to adopt a global broadcast model.

Whilst the privacy implications of a global consensus model are easy to understand, the scalability implications are
perhaps more subtle, yet serious. In a consensus system, it is critical that all processors of a transaction reach
precisely the same conclusion as to its effects.  In situations where two transactions may act on the same data set,
it means that the two transactions must be processed in the same *order* by all nodes. If this were not the case then it
would be possible to devise situations where nodes processed transactions in different orders and reached different
conclusions as to the state of the system.  It is for this reason that systems like Ethereum effectively run
single-threaded, meaning the speed of the system is limited by the single-threaded performance of the slowest
machine on the network.

In Corda, we assume the data being processed represents financial agreements between identifiable parties and that these
institutions will adopt the system only if a significant number of such agreements can be managed by the platform.
As such, the system has to be able to support parallelisation of execution to the greatest extent possible,
whilst ensuring correct transaction ordering when two transactions seek to act on the same piece of shared state.

To achieve this, we must minimise the number of parties who need to receive and process copies of any given
transaction and we must minimise the extent to which two transactions seek to mutate (or supersede) any given piece
of shared state.

A key design decision, therefore, is what should be the most atomic unit of shared data in the system.  This decision
also has profound privacy implications: the more coarsely defined the shared data units, the larger the set of
actors who will likely have a stake in its accuracy and who must process and observe any update to it.

This becomes most obvious when we consider two models for representing cash balances and payments.

A simple account model for cash would define a data structure that maintained a balance at a particular bank for each
"account holder". Every holder of a balance would need a copy of this structure and would thus need to process and
validate every payment transaction, learning about everybody else's payments and balances in the process.
All payments across that set of accounts would have to be single-threaded across the platform, limiting maximum
throughput.

A more sophisticated example might create a data structure per account holder.
But, even here, I would leak my account balance to anybody to whom I ever made
a payment and I could only ever make one payment at a time, for the same reasons above.

A UTXO model would define a data structure that represented an *instance* of a claim against the bank. An account
holder could hold *many* such instances, the aggregate of which would reveal their balance at that institution.  However,
the account holder now only needs to reveal to their payee those instances consumed in making a payment to that payee.
This also means the payer could make several payments in parallel.   A downside is that the model is harder to understand.
However, we consider the privacy and scalability advantages to overwhelm the modest additional cognitive load this places
on those attempting to learn the system.

In what follows, further advantages and disadvantages of this design decision are explored.

Pros
----

The UTXO model has these advantages:

* Immutable ledger entries gives the usual advantages that a more functional approach brings: it's easy to do analysis
  on a static snapshot of the data and reason about the contents.
* Because there are no accounts, it's very easy to apply transactions in parallel even for high traffic legal entities
  assuming sufficiently granular entries.
* Transaction ordering becomes trivial: it is impossible to mis-order transactions due to the reliance on hash functions
  to identify previous states. There is no need for sequence numbers or other things that are hard to provide in a
  fully distributed system.
* Conflict resolution boils down to the double spending problem, which places extremely minimal demands on consensus
  algorithms (as the variable you're trying to reach consensus on is a set of booleans).

Cons
----

It also comes with some pretty serious complexities that in practice must be abstracted from developers:

* Representing numeric amounts using immutable entries is unnatural. For instance, if you receive $1000 and wish
  to send someone $100, you have to consume the $1000 output and then create two more: a $100 for the recipient and
  $900 back to yourself as change. The fact that this happens can leak private information to an observer.
* Because users do need to think in terms of balances and statements, you have to layer this on top of the
  underlying ledger: you can't just read someone's balance out of the system. Hence, the "wallet" / position manager.
  Experience from those who have developed wallets for Bitcoin and other systems is that they can be complex pieces of code,
  although the bulk of wallets' complexity in public systems is handling the lack of finality (and key management).
* Whilst transactions can be applied in parallel, it is much harder to create them in parallel due to the need to
  strictly enforce a total ordering.

With respect to parallel creation, if the user is single threaded this is fine, but in a more complex situation
where you might want to be preparing multiple transactions in flight this can prove a limitation – in
the worst case where you have a single output that represents all your value, this forces you to serialise
the creation of every transaction. If transactions can be created and signed very fast that's not a concern.
If there's only a single user, that's not a concern.

Both cases are typically true in the Bitcoin world, so users don't suffer from this much. In the context of a
complex business with a large pool of shared funds, in which creation of transactions may be very slow due to the
need to get different humans to approve a tx using a signing device, this could quickly lead to frustrating
conflicts where someone approves a transaction and then discovers that it has become a double spend and
they must sign again. In the absolute worst case you could get a form of human livelock.

The tricky part about solving these problems is that the simplest way to express a payment request
("send me $1000 to public key X") inherently results in you receiving a single output, which then can
prove insufficiently granular to be convenient. In the Bitcoin space Mike Hearn and Gavin Andresen designed "BIP 70"
to solve this: it's a simple binary format for requesting a payment and specifying exactly how you'd like to get paid,
including things like the shape of the transaction. It may seem that it's an over complex approach: could you not
just immediately respend the big output back to yourself in order to split it? And yes, you could, until you hit
scenarios like "the machine requesting the payment doesn't have the keys needed to spend it",
which turn out to be very common. So it's really more effective for a recipient to be able to say to the
sender, "here's the kind of transaction I want you to send me".  The :doc:`flow framework <flow-state-machines>`
may provide a vehicle to make such negotiations simpler.

A further challenge is privacy. Whilst our goal of not sending transactions to nodes that don't "need to know"
helps, to verify a transaction you still need to verify all its dependencies and that can result in you receiving
lots of transactions that involve random third parties. The problems start when you have received lots of separate
payments and been careful not to make them linkable to your identity, but then you need to combine them all in a
single transaction to make a payment.

Mike Hearn wrote an article about this problem and techniques to minimise it in
`this article <https://medium.com/@octskyward/merge-avoidance-7f95a386692f>`_ from 2013. This article
coined the term "merge avoidance", which has never been implemented in the Bitcoin space,
although not due to lack of practicality.

A piece of future work for the wallet implementation will be to implement automated "grooming" of the wallet
to "reshape" outputs to useful/standardised sizes, for example, and to send outputs of complex transactions
back to their issuers for reissuance to "sever" long privacy-breaching chains.

Finally, it should be noted that some of the issues described here are not really "cons" of
the UTXO model; they're just fundamental. If you used many different anonymous accounts to preserve some privacy
and then needed to spend the contents of them all simultaneously, you'd hit the same problem, so it's not
something that can be trivially fixed with data model changes.
