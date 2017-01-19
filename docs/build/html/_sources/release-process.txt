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
a few days to ensure the code is sufficiently usable. Usually the release will happen around the end of the month.

Steps to cut a release
----------------------

1. Pick a commit that is stable and do basic QA: run all the tests, run the demos.
2. Review the commits between this release and the last looking for new features, API changes, etc. Make sure the
   summary in the current section of the :doc:`release-notes` is correct and update if not. Then move it into the right
   section for this release. This is the right place to put any advice on how to port app code from the last release.
3. Additionally, if there are any new features or APIs that deserve a new section in the docsite and the author didn't
   create one, bug them to do so a day or two before the release.
4. Regenerate the docsite if necessary and commit.
5. Create a branch with a name like `release-M0` where 0 is replaced by the number of the milestone.
6. Adjust the version in the root build.gradle file to take out the -SNAPSHOT and commit it on the branch.
7. Remove the "is master" warning from the docsite index page on this branch only.
8. Tag the branch with a tag like `release-M0.0`
9. Push the branch and the tag to git.
10. Write up a short announcement containing the summary of new features, changes, and API breaks. Send it to the r3dlg-awg mailing list.
11. On master, adjust the version number in the root build.gradle file upwards.

If there are serious bugs found in the release, backport the fix to the branch and then tag it with e.g. `release-M0.1`
Minor changes to the branch don't have to be announced unless it'd be critical to get all developers updated.
