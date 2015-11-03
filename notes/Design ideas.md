General design scratchpad

Do we need blocks at all? Blocks are an artifact of proof-of-work, which isn't acceptable on private block chains 
due to the excessive energy usage, unclear incentives model and so on. They're also very useful for SPV operation, 
but we have no such requirements here. 

Possible alternative, blend of ideas from:

* Google Spanner
* Hawk
* Bitcoin
* Ethereum
* Intel/TCG
 
 + some of my own ideas



# Blockless operation

* A set of timestampers are set up around the world with clocks synchronised to GPS time (the most accurate clock 
  available as it's constantly recalibrated against the US Naval Observatory atomic clock). Public timestampers
  are available already and can be easily used in the prototyping phase, but as they're intended for low traffic
  applications eventually we'd want our own.
  
  There is a standard protocol for timestamp servers (RFC 3161). It appears to include everything that we might want
  and little more, i.e. it's a good place to start. A more modern version of it with the same features can be easily
  generated later.
  
* All transactions submitted to the global network must be timestamped by a recognised TSP (i.e. signed by a root cert
  owned by R3).
  
* Transactions are ordered according to these timestamps. They are assumed to be precise enough that conflicts where
  two transactions have actually equal times can ~never happen: a trivial resolution algorithm (e.g. based on whichever
  hash is lower) can be used in case that ever happens by fluke.
  
* If need be, clock uncertainty can be measured and overlapping intervals can result in conflict/reject states, as in
  Spanner's TrueTime. The timestamping protocol (RFC 3161) exposes clock uncertainty.
  
* Transactions are timestamped as a group. This ensures that if multiple transactions are needed to carry out a trade,
  individual transactions cannot be extracted by a malicious party and executed independently as the original bundle
  will always be able to win, when broadcast.
  
* Nodes listen to a broadcast feed of timestamped transactions. They know how to roll back transactions and replay
  new ones in case of conflict, but this is done independent of any block construct.
   
* Nodes that are catching up simply download all the transactions from peers that occur after the time they shut down.
  They can be sure they didn't miss any by asking peers to calculate a UTXO set summary at a given time and then 
  verifying it against their own local calculations (this is slow, but shouldn't normally flag any issues so it can
  be done asynchronously).

* Individual transactions/UTXOs can specify time bounds, e.g. "30 seconds". A node compares a transaction timestamp
  to its own local clock and applies the specified bound to the local clock: if the transaction is out of bounds and
  the node isn't catching up, then it is dropped. This prevents people timestamping a malicious transaction X and
  keeping it private, then broadcasting a publicly timestamped transaction Y, then overriding Y with X long after the
  underlying trade has become irreversible. Because time bounds are specified on a _per transaction_ basis, it is
  arbitrarily controllable: traders that want very, very fast clearing can specify a small time boundary and it's up
  to them to ensure their own systems are capable of getting an accurate trusted timestamp and broadcasting it within
  that tight bound. Traders that care less, e.g. because the trade represents physical movement of real goods, can use
  a much larger time bound and get more robustness against transient network/hardware hiccups.
  
* Like in Ethereum, transactions can update stored state (contracts? receipts? what is the right term?) 
  
This can be called transaction-chains. All transactions are public.

For political expedience, we may wish to impose a (not strictly necessary) block quantisation anyway, so the popular
term 'block chain' can be applied and also for auditing/reporting convenience.
  
# Privacy

* Transactions can have two halves: the public side and the private side. The public side is a "normal" transaction that
  includes a program of sufficient power to verify various kinds of signatures and proofs. The optional private side
  is an arbitrary program which is executed by a third party. Various techniques are used to lower the trust required
  in the third parties. We can call these notaries.
  
* It's up to the contract designer to decide how much they rely on notaries - if at all. They are technically not
  required at all: the system would work (and scale) without them. But they can be used to improve privacy.
  
* Simplest "dummy" notary is just a machine that signs the output of the program to state it ran it properly. The notary
  is trusted to execute the program correctly and privately. The signature is checked by the public side. This allows
  traders to perform e.g. a Dutch auction with only the final results being reflected on the public network.
  
* Next best is an SGX based notary. This can provide both privacy and assurance that the code is executed correctly,
  assuming Intel is trustworthy. Note: it's a safe assumption that if R3 becomes very popular with financial networks,
  intelligence agencies will attempt to gain covert access to it given the NSA/GCHQ hacking of Western Union and clear
  interest in SWIFT data. Thus care must be used to ensure the (entirely unprovable) SGX computers are not interdicted
  during delivery.
  
* In addition, zero knowledge proofs can be considered as a supplement to SGX. They can give extra assurance against
  corrupted notaries calculating incorrect results. However, unlike SGX, they cannot reduce the amount of information
  the notary sees, and thus they are strictly a "backup". In addition they have _severe_ caveats, in particular, a
  complex and expensive setup phase that must be executed for each contract (in fact for each version of each contract),
  and execution of the private side is extremely slow.
  
  This makes them suitable only for contracts that are basically finalised and in which the highest levels of assurance
  are required, and fast or frequent trading is not required. The technology may well improve over time.
  
* In some cases homomorphic encryption could be used as a privacy supplement to SGX.
  
# Scaling

* Global broadcast systems are frequently attacked for 'not scaling'. But this is an absolute statement in a world of 
  tradeoffs: technically speaking the NASDAQ is a broadcast system as you can subscribe to data feeds via e.g. OPRA.
  Some of these feeds can reach millions of messages per second. Nonetheless, financial firms are capable of digesting
  them without issue. Even the largest feeds have finite traffic and predictable growth patterns.
  
* We can assume powerful hardware, as the primary users of this system would be financial institutions. There is no 
  requirement to run on people's laptops, outside of testing/devnet scenarios. For instance it's safe to assume SSD
  based storage: we can simply tell institutions that want to get on the network to buy a proper server.
  
* There is no requirement for lightweight/mobile clients, unlike in Bitcoin.
  
* Transaction checking is highly parallelisable.

* Therefore, as long as transactions are kept computationally cheap, there should be no problem reaching even very high
  levels of traffic.
  
Conclusion: scaling in a Bitcoin style manner should not be a problem, even if high level languages like Java or Kotlin
are in use.

# Programmability

* The public side of a transaction must use a globally agreed execution environment, like the EVM is for Ethereum.
  The private sides can run anything: as the public side checks a proof of execution of the private side, there is
  no requirement that the private side use any particular language or runtime.

* Inventing a custom VM and language doesn't make sense: there is only one special requirement that is different 
  to most VMs and that's the ability to impose hard CPU usage limits. But existing VMs can be extended to deliver
  this functionality much more easily than entirely new VMs+languages can be created.
  
* For prototyping and possibly for production use, we should use the JVM:

   * Sandboxing already available, easy to use
   * Several languages available, developers are familiar
   * If host environment also runs on the JVM, no time wasted on interop issues, see the Ethereum ABI issues
   * HotSpot already has a CPU/memory tracking API and can interrupt threads (but lacks the ability to hard shut down
     malicious code)
   * Code annotations can be used to customise whatever languages are used for contract-specific use cases.
   * Can be forced to run in interpreted mode at first, but if we need the extra performance later due to high traffic
     the JIT compiler will automatically make contract code fast.
   * Has industrial strength debugging/monitoring tools.
   * Banks are already deeply familiar with it.



# Transaction design

Use a vaguely bitcoin-like design with "states" which are consumed and generated by "contracts" (programs). Everyone
runs the same programs simultaneously in order to verify state transitions. Transactions consist of input states,
output states and "commands" which represent signed auxiliary inputs to the transitions.



------

# Useful technologies

FIX SBE is a very (very) efficient binary encoding designed for HFT:

   http://real-logic.github.io/simple-binary-encoding/
   
It's mostly analogous to protocol buffers but imposes some additional constraints and has an uglier API, in return for
much higher performance. It probably isn't useful during the prototyping phase. But it may be a useful optimisation
later.

CopyCat is an implementation of Raft (similar to Paxos), as an embeddable framework. Raft/Paxos type algorithms are not
suitable as the basis for a global distributed ledger due to tiny throughput, but may be useful as a subcomponent of
other algorithms. For instance possibly a multi-step contract protocol could use Raft/Paxos between a limited number of
counterparties to synchronise changes.

   http://kuujo.github.io/copycat/user-manual/introduction/



------


# Prototyping

Stream 1:

1. Implement a simple star topology for message routing (full p2p can come later). Ensure it's got a clean modular API.
2. Implement a simple chat app on top of it. This will be useful later for sending commands to faucets, bots, etc.
3. Add auto-update
4. Design a basic transaction/transaction bundle abstraction and implement timestamping of the bundles. Make chat lines
   into "transactions", so they are digitally signed and timestamped properly.
5. Implement detection of conflicts and rollbacks.



Stream 2: Design straw-man contracts and data structures (in Java or Kotlin) for 

1. payments
2. simplified bond auctions
3. maybe a CDS