![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

--------------------------------------------
Design Decision: Replication framework
================================

## Background / Context

Multiple libraries/platforms exist for implementing fault-tolerant systems. In existing CFT notary implementations we experimented with using a traditional relational database with active replication, as well as a pure state machine replication approach based on CFT consensus algorithms.

## Options Analysis

### A. Atomix

*Raft-based fault-tolerant distributed coordination framework.*

Our first CFT notary notary implementation was based on Atomix. Atomix can be easily embedded into a Corda node and provides abstractions for implementing custom replicated state machines. In our case the state machine manages committed Corda contract states. When notarisation requests are sent to Atomix, they get forwarded to the leader node. The leader persists the request to a log, and replicates it to all followers. Once the majority of followers acknowledge receipt, it applies the request to the user-defined state machine. In our case we commit all input states in the request to a JDBC-backed map, or return an error if conflicts occur. 

#### Advantages

1. Lightweight, easy to integrate – embeds into Corda node.
2. Uses Raft for replication – simpler and requires less code than other algorithms like Paxos.

#### Disadvantages

1. Not designed for storing large datasets. State is expected to be maintained in memory only. On restart, each replica re-reads the entire command log to reconstruct the state. This behaviour is not configurable and would require code changes.
2. Does not support batching, not optimised for performance.
3. Since version 2.0, only supports snapshot replication. This means that each replica has to periodically dump the entire commit log to disk, and replicas that fall behind have to download the _entire_ snapshot.
4. Limited tooling.

### B. Permazen

*Java persistence layer with a built-in Raft-based replicated key-value store.*

Conceptually similar to Atomix, but persists the state machine instead of the request log. Built around an abstract persistent key-value store: requests get cleaned up after replication and processing.

#### Advantages

1. Lightweight, easy to integrate – embeds into Corda node.
2. Uses Raft for replication – simpler and requires less code than other algorithms like Paxos.
3. Built around a (optionally) persistent key-value store – supports large datasets.

#### Disadvantages

1. Maintained by a single developer, used by a single company in production. Code quality and documentation looks to be of a high standard though.
2. Not tested with large datasets.
3. Designed for read-write-delete workloads. Replicas that fall behind too much will have to download the entire state snapshot (similar to Atomix).
4. Does not support batching, not optimised for performance.
5. Limited tooling.

### C. Apache Kafka

*Paxos-based distributed streaming platform.*

Atomix and Permazen implement both the replicated request log and the state machine, but Kafka only provides the log component. In theory that means more complexity having to implement request log processing and state machine management, but for our use case it's fairly straightforward: consume requests and insert input states into a database, marking the position of the last processed request. If the database is lost, we can just replay the log from the beginning. The main benefit of this approach is that it gives a more granular control and performance tuning opportunities in different parts of the system.

#### Advantages

1. Stable – used in production for many years.
2. Optimised for performance. Provides multiple configuration options for performance tuning.
3. Designed for managing large datasets (performance not affected by dataset size). 

#### Disadvantages

1. Relatively complex to set up and operate, requires a Zookeeper cluster. Note that some hosting providers offer Kafka as-a-service (e.g. Confluent Cloud), so we could delegate the setup and management.
2. Dictates a more complex notary service architecture.

### D. Custom Raft-based implementation

For even more granular control, we could replace Kafka with our own replicated log implementation. Kafka was started before the Raft consensus algorithm was introduced, and is using Zookeeper for coordination, which is based on Paxos for consensus. Paxos is known to be complex to understand and implement, and the main driver behind Raft was to create a much simpler algorithm with equivalent functionality. Hence, while reimplementing Zookeeper would be an onerous task, building a Raft-based alternative from scratch is somewhat feasible.

#### Advantages

Most of the implementations above have many extra features our use-case does not require. We can implement a relatively simple clean optimised solution that will most likely outperform others (Thomas Schroeter already built a prototype).

#### Disadvantages

Large effort required to make it highly performant and reliable.

### E. Galera

*Synchronous replication plugin for MySQL, uses certification-based replication.*

All of the options discussed so far were based on abstract state machine replication. Another approach is simply using a more traditional RDBMS with active replication support. Note that most relational databases support some form replication in general, however, very few provide strong consistency guarantees and ensure no data loss. Galera is a plugin for MySQL enabling synchronous multi-master replication.

Galera uses certification-based replication, which operates on write-sets: a database server executes the (database) transaction, and only performs replication if the transaction requires write operations. If it does, the transaction is broadcasted to all other servers (using atomic broadcast). On delivery, each server executes a deterministic certification phase, which decides if the transaction can commit or must abort. If a conflict occurs, the entire cluster rolls back the transaction. This type of technique is quite efficient in low-conflict situations and allows read scaling (the latter is mostly irrelevant for our use case).

#### Advantages

1. Very little code required on Corda side to implement.
2. Stable – used in production for many years.
3. Large tooling and support ecosystem.

#### Disadvantages

1. Certification-based replication is based on database transactions. A replication round is performed on every transaction commit, and batching is not supported. To improve performance, we need to combine the committing of multiple Corda transactions into a single database transaction, which gets complicated when conflicts occur.
2. Only supports the InnoDB storage engine, which is based on B-trees. It works well for reads, but performs _very_ poorly on write-intensive workloads with "random" primary keys. In tests we were only able to achieve up to 60 TPS throughput. Moreover, the performance steadily drops with more data added.

### F. CockroachDB

*Distributed SQL database built on a transactional and strongly-consistent key-value store. Uses Raft-based replication.*

On paper, CockroachDB looks like a great candidate, but it relies on sharding: data is automatically split into partitions, and each partition is replicated using Raft. It performs great for single-shard database transactions, and also natively supports cross-shard atomic commits. However, the majority of Corda transactions are likely to have more than one input state, which means that most transaction commits will require cross-shard database transactions. In our tests we were only able to achieve up to 30 TPS in a 3 DC deployment.

#### Advantages

1. Scales very well horizontally by sharding data.
2. Easy to set up and operate.

#### Disadvantages

1. Cross-shard atomic commits are slow. Since we expect most transactions to contain more than one input state, each transaction commit will very likely span multiple shards.
2. Fairly new, limited use in production so far.

## Recommendation and justification

Proceed with Option C. A Kafka-based solution strikes the best balance between performance and the required effort to build a production-ready solution.