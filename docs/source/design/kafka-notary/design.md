# High Performance CFT Notary Service

.. important:: This design document describes a feature of Corda Enterprise.

## Overview

This proposal describes the architecture and an implementation for a high performance crash fault-tolerant notary
service, operated by a single party.

## Background

For initial deployments, we expect to operate a single non-validating CFT notary service. The current Raft and Galera
implementations cannot handle more than 100-200 TPS, which is likely to be a serious bottleneck in the near future. To
support our clients and compete with other platforms we need a notary service that can handle TPS in the order of
1,000s.

## Scope

Goals:

- A CFT non-validating notary service that can handle more than 1,000 TPS. Stretch goal: 10,000 TPS.
- Disaster recovery strategy and tooling.
- Deployment strategy.

Out-of-scope:

- Validating notary service.
- Byzantine fault-tolerance.

## Timeline

No strict delivery timeline requirements, depends on client throughput needs. Estimated delivery by end of Q3 2018.

## Requirements

The notary service should be able to:

- Notarise more than 1,000 transactions per second, with average 4 inputs per transaction.
- Notarise a single transaction within 1s (from the service perspective).
- Tolerate single node crash without affecting service availability.
- Tolerate single datacenter failure.
- Tolerate single disk failure/corruption.


## Design Decisions

.. toctree::
   :maxdepth: 2
   
   decisions/replicated-storage.md
   decisions/index-storage.md

## Target Solution

Having explored different solutions for implementing notaries we propose the following architecture for a CFT notary,
consisting of two components:

1. A central replicated request log, which orders and stores all notarisation requests. Efficient append-only log
   storage can be used along with batched replication, making performance mainly dependent on network throughput.
2. Worker nodes that service clients and maintain a consumed state index. The state index is a simple key-value store
   containing committed state references and pointers to the corresponding request positions in the log. If lost, it can be
   reconstructed by replaying and applying request log entries. There is a range of fast key-value stores that can be used
   for implementation.

![High level architecture](./images/high-level.svg)

At high level, client notarisation requests first get forwarded to a central replicated request log. The requests are
then applied in order to the consumed state index in each worker to verify input state uniqueness. Each individual
request outcome (success/conflict) is then sent back to the initiating client by the worker responsible for it. To
emphasise, each worker will process _all_ notarisation requests, but only respond to the ones it received directly.

Messages (requests) in the request log are persisted and retained forever. The state index has a relatively low
footprint and can in theory be kept entirely in memory. However, when a worker crashes, replaying the log to recover the
index may take too long depending on the SLAs. Additionally, we expect applying the requests to the index to be much
faster than consuming request batches even with persistence enabled.

_Technically_, the request log can also be kept entirely in memory, and the cluster will still be able to tolerate up to
$f < n/2$ node failures. However, if for some reason the entire cluster is shut down (e.g. administrator error), all
requests will be forever lost! Therefore, we should avoid it.

The request log does not need to be a separate cluster, and the worker nodes _could_ maintain the request log replicas
locally. This would allow workers to consume ordered requests from the local copy rather than from a leader node across
the network. It is hard to say, however, if this would have a significant performance impact without performing tests in
the specific network environment (e.g. the bottleneck could be the replication step).

One advantage of hosting the request log in a separate cluster is that it makes it easier to independently scale the
number of worker nodes. If, for example, if transaction validation and resolution is required when receiving a
notarisation request, we might find that a significant number of receivers is required to generate enough incoming
traffic to the request log. On the flipside, increasing the number of workers adds additional consumers and load on the
request log, so a balance needs to be found.

## Design Decisions

As the design decision documents below discuss, the most suitable platform for managing the request log was chosen to be
[Apache Kafka](https://kafka.apache.org/), and [RocksDB](http://rocksdb.org/) as the storage engine for the committed
state index.

| Heading | Recommendation |
| ---------------------------------------- | -------------- |
| [Replication framework](decisions/replicated-storage.md) | Option C |
| [Index storage engine](decisions/index-storage.md) | Option A       |

TECHNICAL DESIGN
---

## Functional

A Kafka-based notary service does not deviate much from the high-level target solution architecture as described above. 

![Kafka overview](./images/kafka-high-level.svg)

For our purposes we can view Kafka as a replicated durable queue we can push messages (_records_) to and consume from.
Consuming a record just increments the consumer's position pointer, and does not delete it. Old records eventually
expire and get cleaned up, but the expiry time can be set to "indefinite" so all data is retained (it's a supported
use-case).

The main caveat is that Kafka does not allow consuming records from replicas directly – all communication has to be
routed via a single leader node.

In Kafka, logical queues are called _topics_. Each topic can be split into multiple partitions. Topics are assigned a
_replication factor_, which specifies how many replicas Kafka should create for each partition. Each replicated
partition has an assigned leader node which producers and consumers can connect to. Partitioning topics and evenly
distributing partition leadership allows Kafka to scale well horizontally.

In our use-case, however, we can only use a single-partition topic for notarisation requests, which limits the total
capacity and throughput to a single machine. Partitioning requests would break global transaction ordering guarantees
for consumers. There is a [proposal](#kafka-throughput-scaling-via-partitioning) from Rick Parker on how we _could_ use
partitioning to potentially avoid traffic contention on the single leader node.

### Data model

Each record stored in the Kafka topic contains:
1. Transaction Id
2. List of input state references
2. Requesting party X.500 name
3. Notarisation request signature

The committed state index contains a map of:

`Input state reference: StateRef -> ( Transaction Id: SecureHash, Kafka record position: Long )`

It also stores a special key-value pair denoting the position of the last applied Kafka record.

## Non-Functional

### Fault tolerance, durability and consistency guarantees

Let's have a closer look at what exactly happens when a client sends a notarisation request to a notary worker node.

![Sequence diagram](./images/steps.svg)

A small note on terminology: the "notary service" we refer to in this section is the internal long-running service in the Corda node.

1. Client sends a notarisation request to the chosen Worker node. The load balancing is handled on the client by Artemis (round-robin).
2. Worker acknowledges receipt and starts the service flow. The flow validates the request: verifies the transaction if needed, validates timestamp and notarisation request signature. The flow then forwards the request to the notary service, and suspends waiting for a response. 
3. The notary service wraps the request in a Kafka record and sends it to the global log via a Kafka producer. The sends are asynchronous from the service's perspective, and the producer is configured to buffer records and perform sends in batches.    
4. The Kafka leader node responsible for the topic partition replicates the received records to followers. The producer also specifies "ack" settings, which control when the records are considered to be committed. Only committed records are available for consumers. Using the "all" setting ensures that the records are persisted all replicas before it is available for consumption. **This ensures that no worker will consume a record that may later be lost if the Kafka leader crashes**.
7. The notary service maintains a separate thread that continuously attempts to pull new available batches of records from the Kafka leader node. It processes the received batches of notarisation requests – commits input states to a local persistent key-value store. Once a batch is processed, the last record position in the Kafka partition is also persisted locally. On restart, the consumption of records is started from the last recorded position.
9. Kafka also tracks consumer positions in Zookeeper, and provides the ability for consumers to commit the last consumed position either synchronously, or asynchronously. Since we don't require exactly once delivery semantics, we opt for asynchronous position commits for performance reasons.
10. Once notarisation requests are processed, the notary service matches them against ones received by this particular worker node, and resumes the flows to send responses back to the clients. 

Now let's consider the possible failure scenarios and how they are handled:
* 2: Worker fails to acknowledge request. The Artemis broker on the client will redirect the message to a different worker node.
* 3: Worker fails right after acknowledging the request, nothing is sent to the Kafka request log. Without some heartbeat mechanism the client can't know if the worker has failed, or the request is simply taking a long time to process. For this reason clients have special logic to retry notarisation requests with different workers, if a response is not received before a specified timeout.
* 4: Kafka leader fails before replicating records. The producer does not receive an ack and the batch send fails. A new leader is elected and all producers and consumers switch to it. The producer retries sending with the new leader (it has to be configured to auto-retry). The lost records were not considered to be committed and therefore not made available for any consumers. Even if the producer did not re-send the batch to the new leader, client retries would fire and the requests would be reinserted into the "pipeline".
* 7: The worker fails after sending out a batch of requests. The requests will be replicated and processed by other worker nodes. However, other workers will not send back replies to clients that the failed worker was responsible for. 
  The client will retry with another worker. That worker will have already processed the same request, and committing the input states will result in a conflict. Since the conflict is caused by the same Corda transaction, it will ignore it and send back a successful response.
* 8: The worker fails right after consuming a record batch. The consumer position is not recorded anywhere so it would re-consume the batch once it's back up again. 
* 9: The worker fails right after committing input states, but before recording last processed record position. On restart, it will re-consume the last batch of requests it had already processed. Committing input states is idempotent so re-processing the same request will succeed. Committing the consumer position to Kafka is strictly speaking not needed in our case, since we maintain it locally and manually "rewind" the partition to the last processed position on startup.
* 10: The worker fails just before sending back a response. The client will retry with another worker.

The above discussion only considers crash failures which don't lead to data loss. What happens if the crash also results in disk corruption/failure? 
* If a Kafka leader node fails and loses all data, the machine can be re-provisioned, the Kafka node will reconnect to the cluster and automatically synchronise all data from one of the replicas. It can only become a leader again once it fully catches up.
* If a worker node fails and loses all data, it can replay the Kafka partition from the beginning to reconstruct the committed state index. To speed this up, periodical backups can be taken so the index can be restored from a more recent snapshot.

One open question is flow handling on the worker node. If notary service flow is checkpointed and the worker crashes while the flow is suspended and waiting for a response (the completion of a future), on restart the flow will re-issue the request to the notary service. The service will in turn forward it to the request log (Kafka) for processing. If the worker node was down long enough for the client to retry the request with a different worker, a single notarisation request will get processed 3 times. 

If the notary service flow is not checkpointed, the request won't be re-issued after restart, resulting in it being processed only twice. However, in the latter case, the client will need to wait for the entire duration until the timeout expires, and if the worker is down for only a couple of seconds, the first approach would result in a much faster response time. 

### Performance

Kafka provides various configuration parameters allowing to control producer and consumer record batch size, compression, buffer size, ack synchrony and other aspects. There are also guidelines on optimal filesystem setup.

RocksDB is highly tunable as well, providing different table format implementations, compression, bloom filters, compaction styles, and others.

Initial prototype tests showed up to *15,000* TPS for single-input state transactions, or *40,000* IPS (inputs/sec) for 1,000 input transactions. No performance drop observed even after 1.2m transactions were notarised. The tests were run on three 8 core, 28 GB RAM Azure VMs in separate datacenters. 

With the recent introduction of notarisation request signatures the figures are likely to be much lower, as the request payload size is increased significantly. More tuning and testing required.

### Scalability 

Not possible to scale beyond peak single machine throughput. Possible to scale the number of worker nodes for transactions verification and signing.

## Operational

As a general note, Kafka and Zookeeper are widely used in the industry and there are plenty of deployment guidelines and management tools available.

### Deployment

Different options available. A singe Kafka broker, Zookeeper replica and a Corda notary worker node can be hosted on the same machine for simplicity and cost-saving. At the other extreme, every Kafka/Zookeeper/Corda node can be hosted on its own machine. The latter arguably provides more room for error, at the expense of extra operational costs and effort.

### Management

Kafka provides command-line tools for managing brokers and topics. Third party UI-based tools are also available.

### Monitoring

Kafka exports a wide range of metrics via JMX. Datadog integration available.

### Disaster recovery

Failure modes:
1. **Single machine or datacenter failure**. No backup/restore procedures are needed – nodes can catch up with the cluster on start. The RocksDB-backed committed state index keeps a pointer to the position of the last applied Kafka record, and it can resume where it left after restart.
2. **Multi-datacenter disaster leading to data loss**. Out of scope. 
3. **User error**. It is possible for an admin to accidentally delete a topic – Kafka provides tools for that. However, topic deletion has to be explicitly enabled in the configuration (disabled by default). Keeping that option disabled should be a sufficient safeguard.
4. **Protocol-level corruption**. This covers scenarios when data stored in Kafka gets corrupted and the corruption is replicated to healthy replicas. In general, this is extremely unlikely to happen since Kafka records are immutable. The only such corruption in practical sense could happen due to record deletion during compaction, which would occur if the broker is misconfigured to not retrain records indefinitely. However, compaction is performed asynchronously and local to the broker. In order for all data to be lost, _all_ brokers have to be misconfigured.

It is not possible to recover without any data loss in the event of 3 or 4. We can only _minimise_ data loss. There are two options:
1. Run a backup Kafka cluster. Kafka provides a tool that forwards messages from one cluster to another (asynchronously).
2. Take periodical physical backups of the Kafka topic.

In both scenarios the most recent requests will be lost. If data loss only occurs in Kafka, and the worker committed state indexes are intact, the notary could still function correctly and prevent double-spends of the transactions that were lost. However, in the non-validating notary scenario, the notarisation request signature and caller identity will be lost, and it will be impossible to trace the submitter of a fraudulent transaction. We could argue that the likelihood of request loss  _and_ malicious transactions occurring at the same time is very low.

## Security

* **Communication**. Kafka supports SSL for both client-to-server and server-to-server communication. However, Zookeeper only supports SSL in client-to-server, which means that running Zookeeper across datacenters will require setting up a VPN. For simplicity, we can reuse the same VPN for the Kafka cluster as well. The notary worker nodes can talk to Kafka either via SSL or the VPN.

* **Data privacy**. No transaction contents or PII is revealed or stored. 

APPENDICES
---

## Kafka throughput scaling via partitioning

We have to use a single partition for global transaction ordering guarantees, but we could reduce the load on it by using it _just_ for ordering:

* Have a single-partition `transactions` topic where all worker nodes send only the transaction id.
* Have a separate _partitioned_ `payload` topic where workers send the entire notarisation request content: transaction id, inputs states, request signature. A single request can be around 1KB in size).

Workers would need to consume from the `transactions` partition to obtain the ordering, and from all `payload` partitions for the actual notarisation requests. A request will not be processed until its global order is known. Since Kafka tries to distribute leaders for different partitions evenly across the cluster, we would avoid a single Kafka broker handling all of the traffic. Load-wise, nothing changes from the worker node's perspective – it still has to process all requests – but a larger number of worker nodes could be supported.