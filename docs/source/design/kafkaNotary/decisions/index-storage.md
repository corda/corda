![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

--------------------------------------------
Design Decision: Storage engine for committed state index
============================================

## Background / Context

The storage engine for the committed state index needs to support a single operation: "insert all values with unique keys, or abort if any key conflict found". A wide range of solutions could be used for that, from embedded key-value stores to full-fledged relational databases. However, since we don't need any extra features a RDBMS provides over a simple key-value store, we'll only consider lightweight embedded solutions to avoid extra operational costs. 

Most RDBMSs are also generally optimised for read performance (use B-tree based storage engines like InnoDB, MyISAM). Our workload is write-heavy and  uses "random" primary keys (state references), which leads to particularly poor write performance for those types of engines – as we have seen with our Galera-based notary service. One exception is the MyRocks storage engine, which is based on RocksDB and can handle write workloads well, and is supported by Percona Server, and MariaDB. It is easier, however, to just use RocksDB directly.

## Options Analysis

### A. RocksDB

An embedded key-value store based on log-structured merge-trees (LSM). It's highly configurable, provides lots of configuration options for performance tuning. E.g. can be tuned to run on different hardware – flash, hard disks or entirely in-memory.

### B. LMDB

An embedded key-value store using B+ trees, has ACID semantics and support for transactions.

### C. MapDB

An embedded Java database engine, providing persistent collection implementations. Uses memory mapped files. Simple to use, implements Java collection interfaces. Provides a HashMap implementation that we can use for storing committed states.

### D. MVStore

An embedded log structured key-value store. Provides a simple persistent map abstraction. Supports multiple map implementations (B-tree, R-tree, concurrent B-tree).

## Recommendation and justification

Performance test results when running on a Macbook Pro with Intel Core i7-4980HQ CPU @ 2.80GHz, 16 GB RAM, SSD:

![Comparison](../images/store-comparison.png)

Multiple tests were run with varying number of transactions and input states per transaction: "1m x 1" denotes a million transactions with one input state.

Proceed with Option A, as RocksDB provides most tuning options and achieves by far the best write performance.

Note that the index storage engine can be replaced in the future with minimal changes required on the notary service.