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

| Author            |      |
| ----------------- | ---- |
| Reviewer(s)       | TBD  |
| Final approver(s) | TBD  |

#### Design Decisions

| Description                              | Recommendation  | Approval*               |
| ---------------------------------------- | --------------- | ----------------------- |
| [Near-term solution](decisions/near-term.md) | Selected option | (Design Approval Board) |

\* only required for formal Design Approval Board meetings.

HIGH LEVEL DESIGN
---

## Overview

General overall of design proposal (goal, objectives, simple outline)

## Background

Existing Doorman code provides for optional integration with a JIRA project, in which issues mirror CSRs and movement of an issue to an Approved status is used to trigger approval of the underlying CSR (and subsequent release of a signed certificate to the client node)

Whilst expedient as a near-term solution, the long-term practicality of JIRA (specifically, the JIRA instance provided by R3's corporate Atlassian account) for managing workflow has been questioned on grounds of: 

- **Scalability**: Unproven that a system in which every request raises a JIRA issue would prove resistant to volume-based attacks
- **Extensibility**: Current work to develop an ID verification process is surfacing various requirements to track meta-data around a CSR - specifically, evidence captured on the requester's identity, checks performed etc. with reference to a policy, and evidence of controls (e.g. 4-eyes checks and approvals) applied. Whilst it is potentially feasible to implement these features through JIRA customisation, the required flexibility has not been demonstrated.
- **Security**: Threat model around JIRA as a third-party, cloud-hosted application cannot be easily qualified. At a minimum, it may be assumed that Atlassian administrators would have both visibility and permissions to change statuses of JIRA stories, thus compromising the integrity of the certificate issuance process.

## Scope

* To provide a mechanism for doorman operators to manage the approvals of CSRs in way which is fit for near-term purposes. This may involve third-party open-source or commercial components (e.g. JIRA) where appropriate.
* **Non-goal**: Develop a full bespoke end-to-end workflow solution equivalent to that used in financial institutions' KYC processes

## Timeline

* Solution required before Feb 1

## Requirements

* To be elaborated.


## Design Decisions

| Heading                                  | Recommendation |
| ---------------------------------------- | -------------- |
| [Near-term solution](decisions/near-term.md) | TBD     |
