![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

--------------------------------------------
Design Decision: Near-term solution for Doorman Administration UI
============================================

## Background / Context

A decision is required specifically on the doorman UI to be used for the near-term Pilot network, to be launched on Feb 1, 2018. See [main design doc](../design.md) for more context.

## Options Analysis

### A. Existing R3 Atlassian-Hosted JIRA

#### Advantages

1.    Incumbent option - no change required

#### Disadvantages

1.    Risk to R3 corporate operations exposed by JIRA integration of Doorman
2.    Potential security challenges (See [main design doc](../design.md))

### B. Separate Atlassian-Hosted JIRA

#### Advantages

1. Minimal infrastructure deployment requirement - just buy a new account
2. HA and DR provided by Atlassian (in principle - no easily enforceable SLAs etc.) 

#### Disadvantages

1. Scalability and performance under large loads unclear 
2. Flexibility to support long term requirements unclear - may be just a 'stop-gap'

### C. Private JIRA installation

#### Advantages

1. Low/no development effort to change
2. A single installation with no HA etc. may be relatively cheap - [$10 for 10 users ](https://www.atlassian.com/software/jira/pricing?tab=self-hosted)
3. Self-ownership of HA/DR issues may provide greater certainty
4. Easier to secure (can put behind a firewall with extra authentication etc.)

#### Disadvantages

1. Scalability and performance under large loads unclear - may end up spending c. £12k for a data centre license.
2. Flexibility to support long term requirements unclear - may be just a 'stop-gap'

### D. Bespoke application

Code a bespoke client UI (e.g. in AngularJS) that administers the doorman via direct calls to the API. 

#### Advantages

1. Starting point for eventual strategic solution that meets precise onboarding process requirements
2. No flexibility issues
3. No performance issues (driven off doorman database)
4. Easier to secure (can put behind a firewall with extra authentication etc.)

#### Disadvantages

1. Extra development effort
2. Higher potential for bugs and security vulnerabilities
3. Replication of workflow logic that already exists
4. Harder for business-side employees to change workflow or logic vs. JIRA

## Recommendation and justification

Proceed with Option C - Private JIRA installation