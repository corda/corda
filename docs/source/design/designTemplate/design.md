![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

# Design Template

Please read the [Design Review Process](../design-review-process.md) before completing a design.

This design template should be used for capturing new Corda feature requests that have been raised as JIRA requirements stories by the product management team. The design may be completed in two stages depending on the complexity and scope of the new feature.

1. High-level: conceptual designs based on business requirements and/or technical vision. Without detailing implementation, this level of design should position the overall solution within the Corda architecture from a logical perspective (independent from code implementation). It should illustrate and walk through the use case scenarios intended to be satisfied by this new feature request. The design should consider non-functional aspects of the system such as performance, scalability, high availability, security, and operational aspects such as management and monitoring.

This section of the document should go through a formal review process (eg. presentation of design at meeting and subsequent PR review workflow)

2. Technical: implementable designs with reference to Corda code. This level of design should focus on API specifications, service definitions, public library additions, data models and schemas, code modularity, configuration, execution and deployment of the new feature. It should also list any new software libraries, frameworks or development approaches to be adopted. The technical design should also consider all aspects of the test lifecycle (unit, integration, smoke tests, performance).

This section of the document should be raised as a PR for development team review.

An outcome of the Design Document should be an implementation plan that defines JIRA stories and tasks to be completed to produce shippable, demonstrable, executable code.

Please complete and/or remove section headings as appropriate to the design being proposed. These are provided as guidance and to structure the design in a consistent and coherent manner.

DOCUMENT MANAGEMENT
---

Design documents should follow the standard GitHub version management and pull request (PR) review workflow mechanism.

## Document Control

| Title                |                                          |
| -------------------- | ---------------------------------------- |
| Date                 |                                          |
| Author               |                                          |
| Distribution         | (see review groups in design review process) |
| Corda target version | (enterprise, open source and enterprise) |
| JIRA reference       | (reference to primary Feature Request JIRA story outlining requirements) |

## Approvals

#### Document Sign-off

| Author            |                                          |
| ----------------- | ---------------------------------------- |
| Reviewer(s)       | (GitHub PR reviewers)                    |
| Final approver(s) | (GitHub PR approver(s) from Design Approval Board) |

#### Design Decisions

| Description                              | Recommendation  | Approval*               |
| ---------------------------------------- | --------------- | ----------------------- |
| [Design Decision 1](decisions/decision.md) | Selected option | (Design Approval Board) |
| [Design Decision 2](decisions/decision.md) | Selected option | (Design Approval Board) |
| [Design Decision 3](decisions/decision.md) | Selected option | (Design Approval Board) |

\* only required for formal Design Approval Board meetings.

## Document History 

To be managed by GitHub revision control 
(please use meaningful identifiers when committing a PR approved design to GitHub - eg. my super design V1.0)

HIGH LEVEL DESIGN
---

## Overview

General overall of design proposal (goal, objectives, simple outline)

## Background

Description of existing solution (if any) and/or rationale for requirement.

* Reference(s) to discussions held elsewhere (slack, wiki, etc).
* Definitions, acronyms and abbreviations 

## Scope

* Goals
* Non-goals (eg. out of scope)
* Reference(s) to similar or related work

## Timeline

* Is this a short, medium or long-term solution?
* Outline timeline expectations

    Eg1. required for Customer Project X by end of Qy'2049)

    Eg2. required to release Enterprise Vx.y (reference roadmap)

* Where short-term design, is this evolvable / extensible or stop-gap (eg. potentially throwaway)?

## Requirements

* Reference(s) to any of following:

    * Captured Product Backlog JIRA entry

    * Internal White Paper feature item and/or visionary feature

    * Project related requirement (POC, RFP, Pilot, Prototype) from

        * Internal Incubator / Accelerator project

        * Direct from Customer, ISV, SI, Partner
* Use Cases 
* Assumptions

## Design Decisions

List of design decisions identified in defining the target solution:
(for each item, please complete the attached [Design Decision template](decisions/decision.md))

| Heading (link to completed Decision document using template) | Recommendation |
| ---------------------------------------- | -------------- |
| [Design Decision 1](decisions/decision.md) | Option A       |
| [Design Decision 2](decisions/decision.md) | TBD*           |
| [Design Decision 3](decisions/decision.md) | Option B       |

It is reasonable to expect decisions to be challenged prior to any formal review and approval. 
*In certain scenarios the Design Decision itself may solicit a recommendation from reviewers.

## Target Solution

* Illustrate any business process with diagrams

    * Business Process Flow (or formal BPMN 2.0), swimlane activity

    * UML:  activity, state, sequence

* Illustrate operational solutions with deployment diagrams

    * Network

* Validation matrix (against requirements)

    * Role, requirement, how design satisfies requirement

* Sample walk through (against Use Cases)

* Implications

    * Technical
    * Operational
    * Security

* Adherence to existing industry standards or approaches
* List any standards to be followed / adopted
* Outstanding issues

## Complementary solutions

Other solutions that provide similar functionality and/or overlap with the proposed.
Where overlap with existing solution(s), describe how this design fits in and complements the current state. 

## Final recommendation

* Proposed solution (if more than one option presented)
* Proceed direct to implementation
* Proceed to Technical Design stage
* Proposed Platform Technical team(s) to implement design (if not already decided)

TECHNICAL DESIGN
---

## Interfaces

* Public APIs impact
* Internal APIs impacted
* Modules impacted

    * Illustrate with Software Component diagrams

## Functional

* UI requirements

    * Illustrate with UI Mockups and/or Wireframes

* (Subsystem) Components descriptions and interactions)

    Consider and list existing impacted components and services within Corda:

    * Doorman
    * Network Map
    * Public API's (ServiceHub, RPCOps)
    * Vault
    * Notaries
    * Identity services
    * Flow framework
    * Attachments
    * Core data structures, libraries or utilities
    * Testing frameworks
    * Pluggable infrastructure: DBs, Message Brokers, LDAP

* Data model & serialization impact and changes required

    * Illustrate with ERD diagrams

* Infrastructure services: persistence (schemas), messaging

## Non-Functional

* Performance
* Scalability
* High Availability

## Operational

* Deployment

    * Versioning

* Maintenance

    * Upgradability, migration

* Management

    * Audit, alerting, monitoring, backup/recovery, archiving

## Security

* Data privacy
* Authentication
* Access control

## Software Development Tools and Programming Standards to be adopted.

* languages
* frameworks
* 3rd party libraries
* architectural / design patterns
* supporting tools

## Testability

* Unit
* Integration
* Smoke
* Non-functional (performance)

APPENDICES
---
