Release process
===============

Corda is under heavy development. The current release process is therefore geared towards rapid iteration.

Each Corda development release is called a *milestone* and has its own branch in the git repository. Milestones are
temporarily stabilised snapshots of the Corda code which are suitable for developers to experiment with. They may
receive backported bugfixes but once announced a milestone will not have any API or backwards compatibility breaks.

Between milestones backwards compatibility is expected to break. Every new milestone comes with a short announcement
detailing:

* What major improvements have been made.
* How to forward port your code to the new milestone.
* What new documentation has become available.
* Important known issues.

Eventually, Corda will stabilise and release version 1. At that point backwards compatibility will be guaranteed
forever and the software will be considered production ready. Until then, expect it to be a building site and wear your
hard hat.

Our goal is to cut a new milestone roughly once a month. There are no fixed dates. If need be, a milestone may slip by
a few days to ensure the code is sufficiently usable.