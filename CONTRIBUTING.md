# Contributing to Corda

## Contributions are Welcome

We welcome contributions to Corda! Here's how to go about it.

## Mission

Corda is an open source project with the aim of developing an enterprise-grade distributed ledger platform for use across a variety of industries.  Corda was designed and developed to apply the concepts of blockchain and smart contract technologies to the requirements of modern business transactions.  It is unique in its aim to build a platform for businesses to transact freely with any counter-party while retaining strict privacy. Corda provides an implementation of this vision in a code base which others are free to build on, contribute to or innovate around. The mission of Corda is further detailed in the [Corda introductory white paper](https://docs.corda.net/_static/corda-introductory-whitepaper.pdf).

## Scope

We believe one of the things that makes Corda special is its coherent design and we seek to retain this defining characteristic. One of the ways we do this is by encouraging developers of new features to write a design proposal for review, comment and advice before they start work and you are encouraged to take advantage of this process. Posting to the [design](https://cordaledger.slack.com/messages/C3J04VC3V/) channel on Slack is a good way to kick this process off. When reviewing a proposed feature, we use the [Corda Technical Whitepaper](https://docs.corda.net/_static/corda-technical-whitepaper.pdf) as a guide and you are strongly encouraged to study it. This white paper is a living document that is updated from time to time under the guidance of the project leader (see below).

## How to Get Your Contributions Merged

Contributions from the community are welcomed and strongly encouraged. From the outset we defined some guidelines to ensure new contributions only ever enhance the project:

* **Quality**: Code in the Corda project should meet the [Corda coding style guidelines](https://docs.corda.net/codestyle.html), with sufficient test-cases, evidence that the contribution does not break any compatibility commitments or cause adverse feature interactions, and evidence of high-quality peer-review.
* **Size**: The Corda project's culture is one of small pull-requests, regularly submitted. The larger a pull-request, the more likely it is that you will be asked to resubmit as a series of sell-contained and individually reviewable smaller PRs.
* **Scope**: We try to ensure the Corda project remains coherent and focused so we ask that the feature's scope is within the definition specified in the Technical White Paper.
* **Maintainability**: If the feature will require ongoing maintenance (eg support for a particular brand of database), we may ask you to accept responsibility for maintaining this feature.
* **Non-duplicative**: If the contribution duplicates features that already exist or are already in progress, you may be asked to work with the project maintainers to reconcile this. As the major contributor to Corda, many [R3](https://r3.com) employees will be working on features at any given time. To avoid surprises and foster transparency, our work tracking system, [Jira](https://r3-cev.atlassian.net/projects/CORDA/summary), is public. In addition, the maintainers and developers on the project are available on the [design](https://cordaledger.slack.com/messages/C3J04VC3V/) channel of our [Slack](https://slack.corda.net/) and they would be delighted to discuss any work you plan to do.

**Advice to contributors**: you are encouraged to join our [Slack](https://slack.corda.net/), observe the [Pull Request](https://github.com/corda/corda/pulls) process in action, contribute to code reviews and start by submitting small changes.

## Project Leadership and Maintainers

The leader of this project is currently [Mike Hearn](https://github.com/mikehearn), who is also the Lead Platform Engineer at R3. The project leader will appoint maintainers of the project, to whom responsibility is delegated for merging community contributions into the code base and acting as points of contact for the community. There is currently one such community maintainer, who is also currently an R3 employee. However, there is no requirement that they be such and we anticipate maintainers joining the project from across the community.

Transparency: in addition to the project lead and maintainer(s), developers employed by R3 who have passed our technical interview process have commit privileges to the repo. All R3 contributions undergo peer review, which is documented in public in GitHub, before they can be merged; they are held to the same standard as all other contributions. The community is encouraged both to observe and participate in this [review process](https://github.com/corda/corda/pulls).

## Community Locations

The Corda maintainers, developers and extended community make active use of the [Corda Slack](http://slack.corda.net/). We also support a very active community of question askers and answerers on [Stack Overflow](https://stackoverflow.com/questions/tagged/corda).

## Transparency and Conflict Policy

The project is supported and maintained by [R3](https://www.r3.com), an alliance of over two hundred firms working together to build and maintain this open source enterprise-grade blockchain platform.  R3 also maintains and distributes a commercial distribution of Corda and we expect other firms also to develop commercial distributions in the future on this open base. Our vision is that these distributions be compatible and interoperable, and our contribution and code review guidelines are designed in part to enable this.

We develop in the open and publish our Jira to give everyone visibility. In the interest of full transparency, it is important to note that many of the most prolific committers to the project are employees of R3, and some of them may also be working on features for R3's commercial distribution of Corda. And the current leader of the project is an R3 employee (see below).

So what happens if a member of the community contributes a feature which the R3 team have implemented only in their commercial product? How is this apparent conflict managed? Our approach is simple: if the contribution meets the standards for the project (see above), then the existence of a competing commercial implementation will not be used as a reason to reject it. In other words, it is our policy that should a community feature be contributed which meets the criteria above, we will accept it or work with the contributor to merge/reconcile it with the commercial feature.

## How to Contribute

To start contributing you can clone our repo and begin making pull requests. Please use descriptive commit messages and follow our [coding style guidelines](https://docs.corda.net/codestyle.html). All contributions to this project are subject to the terms of the Developer Certificate of Origin, reproduced at the bottom of this page.

## Developer Certificate of Origin

All contributions to this project are subject to the terms of the Developer Certificate of Origin, below:

Developer Certificate of Origin Version 1.1

Copyright (C) 2004, 2006 The Linux Foundation and its contributors. 1 Letterman Drive Suite D4700 San Francisco, CA, 94129

Everyone is permitted to copy and distribute verbatim copies of this license document, but changing it is not allowed.

Developer's Certificate of Origin 1.1

By making a contribution to this project, I certify that:

(a) The contribution was created in whole or in part by me and I have the right to submit it under the open source license indicated in the file; or

(b) The contribution is based upon previous work that, to the best of my knowledge, is covered under an appropriate open source license and I have the right under that license to submit that work with modifications, whether created in whole or in part by me, under the same open source license (unless I am permitted to submit under a different license), as indicated in the file; or

(c) The contribution was provided directly to me by some other person who certified (a), (b) or (c) and I have not modified it.

(d) I understand and agree that this project and the contribution are public and that a record of the contribution (including all personal information I submit with it, including my sign-off) is maintained indefinitely and may be redistributed consistent with this project or the open source license(s) involved.
