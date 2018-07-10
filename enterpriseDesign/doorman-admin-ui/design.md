![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

# Design: Doorman Administration UI

DOCUMENT MANAGEMENT
---

## Document Control

| Title                | Doorman Administration UI |
| -------------------- | ------------------------- |
| Date                 | 29/11/2017                |
| Author               | David Lee                 |
| Distribution         | TBD                       |
| Corda target version | N/A - Doorman             |
| JIRA reference       | ENT-1130                  |

## Approvals

#### Document Sign-off

| Author            |            |
| ----------------- | ---------- |
| Reviewer(s)       | Mike Hearn |
| Final approver(s) | Mike Hearn |

#### Design Decisions

| Description                              | Recommendation                       | Approval*               |
| ---------------------------------------- | ------------------------------------ | ----------------------- |
| [Near-term solution](decisions/near-term.md) | Option C - Private JIRA installation | (Design Approval Board) |

HIGH LEVEL DESIGN
---

## Overview

This document describes the application and deployment design for an administrator UI.

## Background

Issuance of a certificate by the Doorman in a production context requires human approval. A business process to support this, including performing relevant checks to validate the identity of the requester of a certificate, is being designed in parallel. 

Existing Doorman code provides for optional integration with a JIRA project, in which issues mirror CSRs and movement of an issue to an Approved status is used to trigger approval of the underlying CSR (and subsequent release of a signed certificate to the client node). Testnet and other non-production deployments to date have used a cloud-based JIRA deployment provided by R3's general-purpose company Atlassian account.

The suitability of using the R3 general Atlassian account, specifically, is questioned on security grounds:

- Every HTTPS request raises a JIRA issue. Significant volumes of HTTP requests may be trivially posted by a malicious attacker with knowledge of the doorman URL, generating a very large volume of JIRA issues.  This would disrupt the CSR approvals process. More significantly, if the R3 cloud JIRA is used, it could significantly disrupt normal business operations for R3, which uses JIRA for team planning and organisation across both Platform and other teams.

  A working assumption has been that the URL for the doorman will be kept secret and only shared with trusted parties (R3 partners and their customers who run Corda nodes). However, no controls (legal or otherwise) are proposed to prevent this URL from being leaked into the public domain. 

  For the same reason, automated load testing of the doorman's CSR submission endpoint is not currently possible.

  Note: Evidence from the Atlassian website suggests that a single cloud JIRA instance is stable up to c. 200,000 issues, but they recommend splitting instances above this point, suggesting performance implications at that scale.

- The threat model around the Atlassian Cloud JIRA cannot be easily qualified. At a minimum, it may be assumed that Atlassian administrators would have both visibility and permissions to change statuses of JIRA stories, thus compromising the integrity of the certificate issuance process. Note that the final signing stage via HSM remains a control point protected by multi-factor 

The suitability of JIRA is further questioned in relation to future requirements to support ID verification workflows. These workflows will be conceptually similar to (but significantly less complex than) Know-Your-Client (KYC) processes used by large financial institutions, in which a series of checks need to be performed on each onboarding request. Such KYC/AML applications generally model each of these checks as a discrete work item which needs to be tracked to completion and recorded; the set of checks required by each case is determined by conditional policy logic. The set of checks then sum to an approval Yes/No outcome for the overall case. It is not clear, at present, whether such logic and management of checks/issues could be effectively managed in JIRA, using plugins or otherwise. 

Beyond this, requirements are anticipated to expose the progress of a given CSR to the requester who raised it, and to allow the requester to submit additional documentary evidence where needed to support ID verification checks. It is not clear how these requirements could also be met solely using JIRA.

## Scope

### Goals

Provide an admin solution for the near-term pilot and production phases which:

- Does not expose R3 business operations to risk of significant disruption from a volume-based attack.
- Can be meaningfully load tested

### Non-Goals

Design a strategic end-to-end solution incorporating specialist ID verification workflows, progress reporting to external clients etc.

## Timeline

* Solution required before the pilot launch (April 26)

## Target Solution

A private installation of JIRA will be deployed to R3's Azure cloud. This will be a dedicated JIRA instance solely for use by permissioned R3 doorman operators and DevOps administrators. 

Access to the JIRA instance will be over a VPN to the R3 (Ldn/NY) office network. Outside an R3 office, doorman operators will require their own VPN connection in order to access the doorman JIRA. (Note that under the proposed scheme where smart card-authentication is used to control the final signing stage, the user needs to be physically present in either the London or New York office anyway, so remote working is not assumed as a key requirement for this process.)

Note that a private installation of JIRA for 10 users (perpetual license) costs USD 10;  25 users costs USD 2,500.

## Final recommendation

- Proceed to DevOps implementation

## 