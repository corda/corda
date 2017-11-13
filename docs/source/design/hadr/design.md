![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

# High Availability and Disaster Recovery for Corda: A Phased Approach

============================================
DOCUMENT MANAGEMENT
============================================

## Document Control

* High Availability and Disaster Recovery for Corda: A Phased Approach
* Date: 13th November 2018
* Author: Matthew Nesbit
* Distribution: Design Review Board, Product Management, Services - Technical (Consulting), Platform Delivery
* Corda target version: Enterprise

## Document Sign-off

* Author: David Lee
* Reviewers(s): TBD
* Final approver(s): TBD

## Document History

============================================
HIGH LEVEL DESIGN
============================================

## Overview
### Background

The term high availability (HA) is used in this document to refer to the ability to rapidly handle any single component failure, whether due to physical issues (e.g. hard drive failure), network connectivity loss, or software faults.

Expectations of HA in modern enterprise systems are for systems to recover normal operation in a few minutes at most, while ensuring minimal/zero data loss. Whilst overall reliability is the overriding objective, it is desirable for Corda to offer HA mechanisms which are both highly automated and transparent to node operators. HA mechanism must not involve any configuration changes that require more than an appropriate admin tool, or a simple start/stop of a process as that would need an Emergency Change Request.

HA naturally grades into requirements for Disaster Recovery (DR), which requires that there is a tested procedure to handle large scale multi-component failures e.g. due to data centre flooding, acts of terrorism.  DR processes are permitted to involve significant manual intervention, although the complications of actually invoking a Business Continuity Plan (BCP) mean that the less manual intervention, the more competitive Corda will be in the modern vendor market.
For modern financial institutions, maintaining comprehensive and effective BCP procedures are a legal requirement which is generally tested at least once a year.

However, until Corda is the system of record, or the primary system for transactions we are unlikely to be required to have any kind of fully automatic DR. In fact, we are likely to be restarted only once BCP has restored the most critical systems.
In contrast, typical financial institutions maintain large, complex technology landscapes in which individual component failures can occur, such as:

* Small scale software failures
* Mandatory data centre power cycles
* Operating system patching and restarts
* Short lived network outages
* Middleware queue build-up
* Machine failures

Thus, HA is essential for enterprise Corda and providing help to administrators necessary for rapid fault diagnosis.

## Scope

* Goals
* Non-goals (eg. out of scope)
* Reference(s) to similar or related work

## Timeline 

This design document outlines a range of topologies which will be enabled through progressive enhancements from the short to long term.

On the timescales available for the current production pilot deployments we clearly do not have time to reach the ideal of a highly fault tolerant, horizontally scaled Corda.

Instead, I suggest that we can only achieve the simplest state of a standby Corda installation only by January 5th and even this is contingent on other enterprise features, such as external database and network map stabilisation being completed on this timescale, plus any issues raised by testing.

For the March 31st timeline, I hope that we can achieve a more fully automatic node failover state, with the Artemis broker running as a cluster too. I include a diagram of a fully scaled Corda for completeness and so that I can discuss what work is re-usable/throw away.

## Requirements

## Proposed Solution
### Current (single process)
### Hot-Cold (minimum requirement)
### Hot-Warm (Medium-term solution)
### Hot-Hot (Long-term strategic solution)

## Alternative Options

List any alternative solutions that may be viable but not recommended.

## Final recommendation

Proposed solution (if more than one option presented)
Proceed direct to implementation
Proceed to Technical Design stage
Proposed Platform Technical team(s) to implement design (if not already decided)

============================================
IMPLEMENTATION PLAN
============================================

The current Corda is built to run as a fully contained single process with the Flow logic, H2 database and Artemis broker all bundled together. This limits the options for automatic replication, or subsystem failure. Thus, we must use external mechanisms to replicate the data in the case of failure. We also should ensure that accidental dual start is not possible in case of mistakes, or slow shutdown of the primary.

Based on this situation, I suggest the following minimum development tasks are required for a tested HA deployment:

1. Complete and merge  JDBC support for an external clustered database. Azure SQL Server has been identified as the most likely Finastra. With this we should be able to point at an HA database instance for Ledger and Checkpoint data.
2. I am suggesting that for the near term we just use the Azure Load Balancer to hide the multiple machine addresses. This does require allowing a health monitoring link to the Artemis broker, but so far testing indicates that this operates without issue. Longer term we need to ensure that the network map and configuration support exists for the system to work with multiple TCP/IP endpoints advertised to external nodes. Ideally this should be rolled into the work for AMPQ bridges and Floats.
3. Implement a very simple mutual exclusion feature, so that an enterprise node cannot start if another is running onto the same database. This can be via a simple heartbeat update in the database, or possibly some other library. This feature should be enabled only when specified by configuration.
4. The replication of the Artemis Message Queues will have to be via an external mechanism. On Azure we believe that the only practical solution is the 'Azure Files' approach which maps a virtual Samba drive. This we are testing in-case it is too slow to work. The mounting of separate Data Disks is possible, but they can only be mounted to one VM at a time, so they would not be compatible with the goal of no change requests for HA.
5. Improve health monitoring to better indicate fault failure. Extending the existing JMX and logging support should achieve  this, although we probably need to create watchdog CordApp that verifies that the State Machine and Artemis messaging are able to process new work and to monitor flow latency.
6. Test the checkpointing mechanism and confirm that failures don't corrupt the data by deploying an HA setup on Azure and driving flows through the system as we stop the node randomly and switch to the other node. If this reveals any issues we will have to fix them.
7. Confirm that the behaviour of the RPC proxy is stable through these restarts, from the perspective of a stateless REST server calling through to RPC. The RPC API should provide positive feedback to the application, so that it can respond in a controlled fashion when disconnected.
8. WeWork on flow hospital tools where needed
