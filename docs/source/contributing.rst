Contributing
============

Corda is an open-source project and we welcome contributions. This guide explains how to contribute back to Corda.

.. contents::

Identifying an area to contribute
---------------------------------
There are several ways to identify an area where you can contribute to Corda:

* Browse open issues on the
  `Corda JIRA board <https://r3-cev.atlassian.net/projects/CORDA/issues/CORDA-601?filter=allopenissues>`_
* Check the `Corda GitHub issues <https://github.com/corda/corda/issues>`_
* Ask in the `Corda Slack channel <http://slack.corda.net/>`_

It's always worth checking in the Corda Slack channel whether a given area or issue is a good target for your
contribution. Someone else may already be working on it, or it may be blocked by another on-going piece of work.

Making the required changes
---------------------------

1. Create a fork of the master branch of the Corda repo (https://github.com/corda/corda)
2. Clone the fork to your local machine
3. Make the changes, in accordance with the :doc:`code style guide </codestyle>`

Testing the changes
-------------------

Running the tests
^^^^^^^^^^^^^^^^^
Your changes must pass the tests described :doc:`here </testing>`.

Building against the master branch
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
You may also want to test your changes against a CorDapp defined outside of the Corda repo. To do so, please follow the
instructions :doc:`here </building-against-master>`.

Merging the changes back into Corda
-----------------------------------

1. Create a pull request from your fork to the master branch of the Corda repo
2. Complete the pull-request checklist in the comments box:

    * State that you have run the tests
    * State that you have included JavaDocs for any new public APIs
    * State that you have included the change in the :doc:`changelog </changelog>` and
      :doc:`release notes </release-notes>` where applicable
    * State that you are in agreement with the terms of
      `CONTRIBUTING.md <https://github.com/corda/corda/blob/master/CONTRIBUTING.md>`_

3. Request a review from a member of the Corda platform team via the `Corda Slack channel <http://slack.corda.net/>`_
4. Wait for your PR to pass all four types of continuous integration tests (integration, API stability, build and unit)

   * Currently, external contributors cannot see the output of these tests. If your PR fails a test that passed
     locally, ask the reviewer for further details

5. Once a reviewer has approved the PR and the tests have passed, squash-and-merge the PR as a single commit