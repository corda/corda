Contributing
============

Corda is an open-source project and contributions are welcome. Our contributing philosophy is described in 
`CONTRIBUTING.md <https://github.com/corda/corda/blob/master/CONTRIBUTING.md>`_. This guide explains the mechanics 
of contributing to Corda.

.. contents::

Identifying an area to contribute
---------------------------------
There are several ways to identify an area where you can contribute to Corda:

* Ask in the ``#design`` channel of the `Corda Slack <http://slack.corda.net/>`_

* Browse the `Corda GitHub issues <https://github.com/corda/corda/issues>`_

  * It's always worth checking in the ``#design`` channel whether a given issue is a good target for your
    contribution. Someone else may already be working on it, or it may be blocked by an on-going piece of work

* Browse issues labelled as ``HelpWanted`` on the
  `Corda JIRA board <https://r3-cev.atlassian.net/issues/?jql=labels%20%3D%20HelpWanted>`_

  * Any issue with a ``HelpWanted`` label is considered ideal for open-source contributions
  * If there is a feature you would like to add and there isn't a corresponding issue labelled as ``HelpWanted``, that
    doesn't mean your contribution isn't welcome. Please reach out on the ``#design`` channel to clarify

Making the required changes
---------------------------

1. Create a fork of the master branch of the `Corda repo <https://github.com/corda/corda>`_
2. Clone the fork to your local machine
3. Make the changes, in accordance with the :doc:`code style guide </codestyle>`

Testing the changes
-------------------

Running the tests
^^^^^^^^^^^^^^^^^
Your changes must pass the tests described :doc:`here </testing>`.

Building against the master branch
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
You can test your changes against CorDapps defined in other repos by following the instructions :doc:`here </building-against-master>`.

Running the API scanner
^^^^^^^^^^^^^^^^^^^^^^^
Your changes must also not break compatibility with existing public API. We have an API scanning tool which runs as part of the build
process which can be used to flag up any accidental changes, which is detailed :doc:`here </api-scanner>`.


Updating the docs
-----------------

Any changes to Corda's public API must be documented as follows:

1. Update the relevant `.rst file(s) <https://github.com/corda/corda/tree/master/docs/source>`_
2. Include the change in the :doc:`changelog </changelog>` and :doc:`release notes </release-notes>` where applicable
3. :doc:`Build the docs locally </building-the-docs>`
4. Open the built .html files for the modified pages to ensure they render correctly

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

3. Request a review from a member of the Corda platform team via the `#design channel <http://slack.corda.net/>`_
4. Wait for your PR to pass all four types of continuous integration tests (integration, API stability, build and unit)

   * Currently, external contributors cannot see the output of these tests. If your PR fails a test that passed
     locally, ask the reviewer for further details

5. Once a reviewer has approved the PR and the tests have passed, squash-and-merge the PR as a single commit
