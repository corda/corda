![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

<a href="https://ci-master.corda.r3cev.com/viewType.html?buildTypeId=CordaEnterprise_Build&tab=buildTypeStatusDiv"><img src="https://ci.corda.r3cev.com/app/rest/builds/buildType:Corda_CordaBuild/statusIcon"/></a>

# Design Documentation

This directory should be used to version control Corda design documents.

These should be written in [Markdown](https://github.com/adam-p/markdown-here/wiki/Markdown-Cheatsheet) (a design template is provided for general guidance) and follow the design review process outlined below. It is recommended you use a Markdown editor such as [Typora](https://typora.io/), or an appropriate plugin for your favourite editor (eg. [Sublime Markdown editing theme](http://plaintext-productivity.net/2-04-how-to-set-up-sublime-text-for-markdown-editing.html)).

## Design Review Process

Please see the [design review process](design-review-process.md).

* Feature request submission
* High level design
* Review / approve gate
* Technical design
* Review / approve gate
* Plan, prototype, implement, QA 

## Design Template

Please copy this [directory](./designTemplate) to a new location under `/docs/source/design` (use a meaningful short descriptive directory name) and use the [Design Template](./designTemplate/design.md) contained within to guide writing your Design Proposal. Whilst the section headings may be treated as placeholders for guidance, you are expected to be able to answer any questions related to pertinent section headings (where relevant to your design) at the design review stage. Use the [Design Decision Template](./designTemplate/decisions/decision.md)  (as many times as needed) to record the pros and cons, and justification of any design decision recommendations where multiple options are available. These should be directly referenced from the *Design Decisions* section of the main design document.

The design document may be completed in one or two iterations, by completing the following main two sections individually or singularly:

* High level design   
  Where a feature requirement is specified at a high level, and multiple design solutions are possible, this section should be completed and circulated for review prior to completing the detailed technical design.
  High level designs will often benefit from a formal meeting and discussion review amongst stakeholders to reach consensus on the preferred way to proceed. The design author will then incorporate all meeting outcome decisions back into a revision for final GitHub PR approval.   
* Technical design 
  The technical design will consist of implementation specific details which require a deeper understanding of the Corda software stack, such as public API's and services, libraries, and associated middleware infrastructure (messaging,security, database persistence, serialization) used to realize these.
  Technical designs should lead directly to a GitHub PR review process.

Once a design is approved using the GitHub PR process, please commit the PR to the GitHub repository with a meaningful version identifier (eg. my super design document - **V1.0**)

## Design Repository

All design documents will be version controlled under github under the directory `/docs/source/design`.
For designs that relate to Enterprise-only features (and that may contain proprietary IP), these should be stored under the [Enterprise Github repository](https://github.com/corda/enterprise). All other public designs should be stored under the [Open Source Github repository](https://github.com/corda/corda). 
