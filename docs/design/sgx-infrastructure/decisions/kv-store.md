![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

--------------------------------------------
Design Decision: Key-value store implementation
============================================

This is a simple choice of technology.

## Options Analysis

### A. ZooKeeper

#### Advantages

1. Tried and tested
2. HA team already uses ZooKeeper

#### Disadvantages

1. Clunky API
2. No HTTP API
3. Handrolled protocol

### B. etcd

#### Advantages

1. Very simple API, UNIX philosophy
2. gRPC
3. Tried and tested
4. MVCC
5. Kubernetes uses it in the background already
6. "Successor" of ZooKeeper
7. Cross-platform, OSX and Windows support
8. Resiliency, supports backups for disaster recovery

#### Disadvantages

1. HA team uses ZooKeeper

### C. Consul

#### Advantages

1. End to end discovery including UIs

#### Disadvantages

1. Not very well spread
2. Need to store other metadata as well
3. HA team uses ZooKeeper

## Recommendation and justification

Proceed with Option B (etcd). It's practically a successor of ZooKeeper, the interface is quite simple, it focuses on 
primitives (CAS, leases, watches etc) and is tried and tested by many heavily used applications, most notably 
Kubernetes. In fact we have the option to use etcd indirectly by writing Kubernetes extensions, this would have the
advantage of getting readily available CLI and UI tools to manage an enclave cluster. 
