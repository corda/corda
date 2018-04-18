![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

--------------------------------------------
Design Decision: Notary Backend - Galera or Permazen Raft
=========================================================

## Background / Context

We have evaluated Galera and Permazen as a possible replacement for Atomix CopyCat for the storage backend of our Notary Service, more specificalyl the Uniqueness Provider. 

## Options Analysis

### A. Galera Cluster

#### Advantages

1. Wider user base. In a survey of 478 OpenStack deployments, 32% decided to use Galera Cluster in production, see p. 47 of the [survey](https://www.openstack.org/assets/survey/April2017SurveyReport.pdf).

2. Very little additional work needed.

3. Entrerprise support. 

#### Disadvantages

1. Harder to customize.

### B. Permazen Raft KV Database

#### Advantages

1. ​Customizable.
2. ​Slightly faster in our tests.
3. Simpler to deploy (embedded in the Corda node).

#### Disadvantages

1. ​Not ready out of the box, needs rate limiting, follower might run OOM during snapshot transfer.
2. ​No large community behind it.

## Recommendation and justification

Proceed with Option A
