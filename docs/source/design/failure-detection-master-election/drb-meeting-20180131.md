# Design Review Board Meeting Minutes

**Date / Time:** Jan 31 2018, 11.00

## Attendees

- Matthew Nesbit (MN)
- Bogdan Paunescu (BP)
- James Carlyle (JC)
- Mike Hearn (MH)
- Wawrzyniec Niewodniczanski (WN)
- Jonathan Sartin (JS)
- Gavin Thomas (GT)


## **Decision**

Proceed with recommendation to use Zookeeper as the master selection solution


## **Primary Requirement of Design**

- Client can run just 2 nodes, master and slave
- Current deployment model to not change significantly
- Prioritised mastering or be able to automatically elect a master. Useful to allow clients to do rolling upgrades, or for use when a high spec machine is used for master
- Nice to have: use for flow sharding and soft locking

## **Minutes**

MN presented a high level summary of the options:
- Galera:
    - Negative: does not have leader election and failover capability.

- Atomix IO:
    - Positive: does integrate into node easily, can setup ports
    - Negative: requires min 3 nodes, cannot manipulate election e.g. drop the master rolling deployments / upgrades, cannot select the 'beefy' host for master where cost efficiencies have been used for the slave / DR, young library and has limited functionality, poor documentation and examples

- Zookeeper (recommended option): industry standard widely used and trusted. May be able to leverage clients' incumbent Zookeeper infrastructure
    - Positive: has flexibility for storage and a potential for future proofing; good permissioning capabilities; standalone cluster of Zookeeper servers allows 2 nodes solution rather than 3
    - Negative: adds deployment complexity due to need for Zookeeper cluster split across data centers
Wrapper library choice for Zookeeper requires some analysis


MH: predictable source of API for RAFT implementations and Zookeeper compared to Atomix. Be better to have master
selector implemented as an abstraction

MH: hybrid approach possible - 3rd node for oversight, i.e. 2 embedded in the node, 3rd is an observer. Zookeeper can
have one node in primary data centre, one in secondary data centre and 3rd as tie-breaker

WN: why are we concerned about cost of 3 machines? MN: we're seeing / hearing clients wanting to run many nodes on one
VM. Zookeeper is good for this since 1 Zookepper cluster can serve 100+ nodes

MH: terminology clarification required: what holds the master lock? Ideally would be good to see design thinking around
split node and which bits need HA. MB: as a long term vision, ideally have 1 database for many IDs and the flows for
those IDs are load balanced. Regarding services internally to node being suspended, this is being investigated.

MH: regarding auto failover, in the event a database has its own perception of master and slave, how is this handled?
Failure detector will need to grow or have local only schedule to confirm it is processing everything including
connectivity between database and bus, i.e. implement a 'healthiness' concept

MH: can you get into a situation where the node fails over but the database does not, but database traffic continues to
be sent to down node? MB: database will go offline leading to an all-stop event.

MH: can you have master affinity between node and database? MH: need watchdog / heartbeat solutions to confirm state of
all components

JC: how long will this solution live? MB: will work for hot / hot flow sharding, multiple flow workers and soft locks,
then this is long term solution. Service abstraction will be used so we are not wedded to Zookeeper however the
abstraction work can be done later

JC: does the implementation with Zookeeper have an impact on whether cloud or physical deployments are used? MB: its an
internal component, not part of the larger Corda network therefore can be either. For the customer they will have to
deploy a separate Zookeeper solution, but this is the same for Atomix.

WN: where Corda as a service is being deployed with many nodes in the cloud. Zookeeper will be better suited to big
providers.

WN: concern is the customer expects to get everything on a plate, therefore will need to be educated on how to implement
Zookeeper, but this is the same for other master selection solutions.

JC: is it possible to launch R3 Corda with a button on Azure marketplace to commission a Zookeeper? Yes, if we can
resource it. But expectation is Zookeeper will be used by well-informed clients / implementers so one-click option is
less relevant.

MH: how does failover work with HSMs? 

MN: can replicate realm so failover is trivial

JC: how do we document Enterprise features? Publish design docs? Enterprise fact sheets? R3 Corda marketing material?
Clear separation of documentation is required. GT: this is already achieved by having docs.corda.net for open source
Corda and docs.corda.r3.com for enterprise R3 Corda


### Next Steps

MN proposed the following steps:

1)   Determine who has experience in the team to help select wrapper library
2)   Build container with Zookeeper for development
3)   Demo hot / cold with current R3 Corda Dev Preview release (writing a guide)
4)   Turn nodes passive or active
5)   Leader election
6)   Failure detection and tooling
7)   Edge case testing
