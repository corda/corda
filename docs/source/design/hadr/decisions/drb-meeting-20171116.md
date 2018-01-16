![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

--------------------------------------------
Design Review Board Meeting Minutes
============================================

**Date / Time:** 16/11/2017, 16:30

 

## Attendees

- Mark Oldfield (MO)
- Matthew Nesbit (MN)
- Richard Gendal Brown (RGB)
- James Carlyle (JC)
- Mike Hearn (MH)
- Jose Coll (JoC)
- Rick Parker (RP)
- Andrey Bozhko (AB)
- Dave Hudson (DH)
- Nick Arini (NA)
- Ben Abineri (BA)
- Jonathan Sartin (JS)
- David Lee (DL)



## **Minutes**

The meeting re-opened following prior discussion of the float design.

MN introduced the design for high availability, clarifying that the design did not include support for DR-implied features (asynchronous replication etc.). 

MN highlighted limitations in testability: Azure had confirmed support for geo replication but with limited control by the user and no testing facility; all R3 can do is test for impact on performance. 

The design was noted to be dependent on a lot on external dependencies for replication, with R3's testing capability limited to Azure. Agent banks may want to use SAN across dark fiber sites, redundant switches etc. not available to R3.

MN noted that certain databases are not yet officially supported in Corda.

### [Near-term-target](./near-term-target.md), [Medium-term target](./medium-term-target.md)

Outlining the hot-cold design, MN highlighted importance of ensuring only one node is active at one time. MN argued for having a tested hot-cold solution as a ‘backstop’. MN confirmed the work involved was to develop DB/SAN exclusion checkers and test appropriately.

JC queried whether unknowns exist for hot-cold. MN described limitations of Azure file replication.

JC noted there was optionality around both the replication mechanisms and the on-premises vs. cloud deployment.

### [Message storage](./db-msg-store.md)

Lack of support for storing Artemis messages via JDBC was raised, and the possibility for RedHat to provide an enhancement was discussed.

MH raised the alternative of using Artemis’ inbuilt replication protocol - MN confirmed this was in scope for hot-warm, but not hot-cold.

JC posited that file system/SAN replication should be OK for banks

**DECISION AGREED**: Use storage in the file system (for now)

AB questioned about protections against corruption; RGB highlighted the need for testing on this. MH described previous testing activity, arguing for a performance cluster that repeatedly runs load tests, kills nodes,checking they come back etc.

MN could not comment on testing status of current code. MH noted the notary hasn't been tested.

AB queried how basic node recovery would work. MN explained, highlighting the limitation for RPC callbacks.

JC proposed these limitations should be noted and explained to Finastra; move on. 

There was discussion of how RPC observables could be made to persist across node outages. MN argued that for most applications, a clear signal of the outage that triggered clients to resubscribe was preferable. This was agreed.

JC argued for using Kafka. 

MN presented the Hot-warm solution as a target for March-April and provide clarifications on differences vs. hot-cold and hot-hot.

JC highlighted that the clustered artemis was an important intermediate step. MN highlighted other important features

MO noted that different banks may opt for different solutions. 

JoC raised the question of multi-IP per node.

MN described the Hot-hot solution, highlighting that flows remained 'sticky' to a particular instance but could be picked up by another when needed. 

AB preferred the hot-hot solution. MN noted the many edge cases to be worked through.

AB Queried the DR story. MO stated this was out of scope at present. 

There was discussion of the implications of not having synchronous replication.

MH questioned the need for a backup strategy that allows winding back the clock. MO stated this was out of scope at present. 

MO drew attention to the expectation that Corda would be considered part of larger solutions with controlled restore procedures under BCP.

JC noted the variability in many elements as a challenge.

MO argued for providing a 'shrink-wrapped' solution based around equipment R3 could test (e.g. Azure)

JC argued for the need to manage testing of banks' infrastructure choices in order to reduce time to implementation.

There was discussion around the semantic difference between HA and DR. MH argued for a definition based around rolling backups. MN and MO shared banks' view of what DR is. MH contrasted this with Google definitions. AB noted HA and DR have different SLAs. 

**DECISION AGREED:** Near-term target: Hot Cold; Medium-term target: Hot-warm (RGB, JC, MH agreed)

RGB queried why Artemis couldn't be run in clustered mode now. MN explained.

AB queried what Finastra asked for. MO implied nothing specific; MH maintained this would be needed anyway.

### [Broker separation](./external-broker.md)

MN outlined his rationale for Broker separation.

JC queried whether this would affect demos.

MN gave an assumption that HA was for enterprise only; RGB, JC: pointed out that Enterprise might still be made available for non-production use. 

**DECISION AGREED**: The broker should only be separated if required by other features (e.g. the float), otherwise not. (RGB, JC, MH agreed).

### [Load balancers and multi-IP](./ip-addressing.md)

The topic was discussed. 

**DECISION AGREED**: The design can allow for optional load balancers to be implemented by clients.

### [Crash shell](./crash-shell.md)

MN provided outline explanation. 

**DECISION AGREED**: Restarts should be handled by polite shutdown, followed by a hard clear. (RGB, JC, MH agreed)

