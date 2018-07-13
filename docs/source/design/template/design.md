# Design doc template

## Overview

Please read the [Design Review Process](../design-review-process.md) before completing a design.

Each section of the document should be at the second level (two hashes at the start of a line).

This section should describe the desired change or feature, along with background on why it's needed and what problem
it solves. 

An outcome of the design document should be an implementation plan that defines JIRA stories and tasks to be completed
to produce shippable, demonstrable, executable code.

Please complete and/or remove section headings as appropriate to the design being proposed. These are provided as
guidance and to structure the design in a consistent and coherent manner.

## Background

Description of existing solution (if any) and/or rationale for requirement.

* Reference(s) to discussions held elsewhere (slack, wiki, etc).
* Definitions, acronyms and abbreviations 

## Goals

What's in scope to be solved.

## Non-goals

What won't be tackled as part of this design, either because it's not needed/wanted, or because it will be tackled later
as part of a separate design effort. Figuring out what you will *not* do is frequently a useful exercise.

## Timeline

* Is this a short, medium or long-term solution?
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

List of design decisions identified in defining the target solution.

For each item, please complete the attached [Design Decision template](decisions/decision.md)

Use the ``.. toctree::`` feature to list out the design decision docs here (see the source of this file for an example). 

.. toctree::
   :maxdepth: 2

   decisions/decision.md

## Design

Think about:

* Public API, backwards compatibility impact.
* UI requirements, if any. Illustrate with UI Mockups and/or wireframes.
* Data model & serialization impact and changes required.
* Infrastructure services: persistence (schemas), messaging.
* Impact on performance, scalability, high availability
* Versioning, upgradability, migration=
* Management: audit, alerting, monitoring, backup/recovery, archiving
* Data privacy, authentication, access control
* Logging
* Testability
