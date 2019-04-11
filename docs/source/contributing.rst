How to contribute
=================

.. contents::

Identifying an area to contribute
---------------------------------
There are several ways to identify an area where you can contribute to Corda:

* The easiest is just to message one of the :ref:`Community Maintainers <community-maintainers>` saying "I want to help!". They'll work
  with you to find an area for you to contribute

* If you have a specific contribution in mind, confirm whether the contribution is appropriate first by reaching out in the
  ``#contributing`` channel of the `Corda Slack <http://slack.corda.net/>`_ or contacting one of the
  :ref:`Community Maintainers <community-maintainers>` directly

* If you do not have a specific contribution in mind, you can also browse the issues labelled as ``help wanted`` on the
  `Corda GitHub issues <https://github.com/corda/corda/issues?q=is%3Aopen+is%3Aissue+label%3A%22good+first+issue%22>`_ page

  * Issues that additionally have the ``good first issue`` label are considered ideal for first-timers

Contribution guidelines
-----------------------
We believe one of the things that makes Corda special is its coherent design and we seek to retain this defining characteristic. From the
outset we defined some guidelines to ensure new contributions only ever enhance the project:

* **Quality**: Code in the Corda project should meet the :doc:`Corda coding style guidelines <codestyle>`, with sufficient test-cases,
  descriptive commit messages, evidence that the contribution does not break any compatibility commitments or cause adverse feature
  interactions, and evidence of high-quality peer-review

* **Size**: The Corda project's culture is one of small pull-requests, regularly submitted. The larger a pull-request, the more likely it
  is that you will be asked to resubmit as a series of self-contained and individually reviewable smaller PRs

* **Scope**: We try to ensure the Corda project remains coherent and focused so we ask that the feature's scope is within the definition
  specified in the `Corda Technical Whitepaper`_

* **Maintainability**: If the feature will require ongoing maintenance (eg support for a particular brand of database), we may ask you to
  accept responsibility for maintaining this feature

* **Non-duplicative**: If the contribution duplicates features that already exist or are already in progress, you may be asked to work with
  the project maintainers to reconcile this. As the major contributor to Corda, many employees of `R3 <https://r3.com>`_ will be working on
  features at any given time. To avoid surprises and foster transparency,
  `our Jira work tracking system is public <https://r3-cev.atlassian.net/projects/CORDA/summary>`_. If in doubt, reach out to one of the
  :ref:`Community Maintainers <community-maintainers>`

Making the required changes
---------------------------
You should make your changes as follows:

1. Create a fork of the master branch of the `Corda repo <https://github.com/corda/corda>`_
2. Clone the fork to your local machine
3. Build Corda by following the instructions :doc:`here </building-corda>`
4. Make the changes, in accordance with the :doc:`code style guide </codestyle>`

Things to check
^^^^^^^^^^^^^^^

* **Make sure your error handling is up to scratch:** Errors should not leak to the UI. When writing tools intended for end users, like the
  node or command line tools, remember to add ``try``/``catch`` blocks. Throw meaningful errors. For example, instead of throwing an
  ``OutOfMemoryError``, use the error message to indicate that a file is missing, a network socket was unreachable, etc. Tools should not
  dump stack traces to the end user

* **Look for API breaks:** We have an automated checker tool that runs as part of our continuous integration pipeline and helps a lot, but
  it can't catch semantic changes where the behavior of an API changes in ways that might violate app developer expectations

* **Suppress inevitable compiler warnings:** Compiler warnings should have a ``@Suppress`` annotation on them if they're expected and can't
  be avoided

* **Remove deprecated functionality:** When deprecating functionality, make sure you remove the deprecated uses in the codebase

* **Avoid making formatting changes as you work:** In Kotlin 1.2.20, new style guide rules were implemented. The new Kotlin style guide is
  significantly more detailed than before and IntelliJ knows how to implement those rules. Re-formatting the codebase creates a lot of
  diffs that make merging more complicated

* **Things to consider when writing CLI apps:** Make sure any changes to CLI applications conform to the :doc:`cli-ux-guidelines`

Extending the flow state machine
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
If you are interested in extending the flow state machine, you can find instructions on how to do this
:doc:`here </contributing-flow-state-machines>`.

Testing the changes
-------------------
You should test your changes as follows:

1. **Add tests**: Unit tests and integration tests for external API changes must cover Java and Kotlin. For internal API changes these
   tests can be scaled back to Kotlin only

2. **Run the tests**: Your changes must pass the tests described :doc:`here <testing>`

3. **Perform manual testing**: Before sending that code for review, spend time poking and prodding the tool and thinking, “Would the
   experience of using this feature make my mum proud of me?”. Automated tests are not a substitute for dogfooding

4. **Build against the master branch**: You can test your changes against CorDapps defined in other repos by following the instructions
   :doc:`here <building-against-master>`

5. **Run the API scanner**: Your changes must also not break compatibility with existing public API. We have an API scanning tool which
   runs as part of the build process which can be used to flag up any accidental changes, which is detailed :doc:`here <api-scanner>`

Updating the docs
-----------------
You should document any changes to Corda's public API as follows:

1. Add comments and javadocs/kdocs. API functions must have javadoc/kdoc comments and sentences must be terminated
   with a full stop. We also start comments with capital letters, even for inline comments. Where Java APIs have
   synonyms (e.g. ``%d`` and ``%date``), we prefer the longer form for legibility reasons. You can configure your IDE
   to highlight these in bright yellow
2. Update the relevant `.rst file(s) <https://github.com/corda/corda/tree/master/docs/source>`_
3. Include the change in the :doc:`changelog </changelog>` if the change is external and therefore visible to CorDapp
   developers and/or node operators
4. :doc:`Build the docs locally </building-the-docs>` and check that the resulting .html files (under ``docs/build/html``) for the modified
   render correctly
5. If relevant, add a sample. Samples are one of the key ways in which users learn about what the platform can do.
   If you add a new API or feature and don't update the samples, your work will be much less impactful

Merging the changes back into Corda
-----------------------------------
You should merge the changes back into Corda as follows:

1. Create a pull request from your fork to the ``master`` branch of the Corda repo

2. In the PR comments box:

  * Complete the pull-request checklist:

    * [ ] Have you run the unit, integration and smoke tests as described here? https://docs.corda.net/head/testing.html
    * [ ] If you added/changed public APIs, did you write/update the JavaDocs?
    * [ ] If the changes are of interest to application developers, have you added them to the changelog, and potentially
      release notes?
    * [ ] If you are contributing for the first time, please read the agreement in CONTRIBUTING.md now and add to this
      Pull Request that you agree to it.

  * Add a clear description of the purpose of the PR
  
  * Add the following statement to confirm that your contribution is your own original work: "I hereby certify that my contribution is in
    accordance with the Developer Certificate of Origin (https://developercertificate.org/)."

3. Request a review by reaching out in the ``#contributing`` channel of the `Corda Slack <http://slack.corda.net/>`_ or contacting one of
   the :ref:`Community Maintainers <community-maintainers>` directly

4. The reviewer will either:

  * Accept and merge your PR
  * Leave comments requesting changes via the GitHub PR interface

    * You should make the changes by pushing directly to your existing PR branch. The PR will be updated automatically

5. (Optional) Open an additional PR to add yourself to the
   `contributors list <https://github.com/corda/corda/blob/master/CONTRIBUTORS.md>`_

    * The format is generally ``firstname surname (company)``, but the company can be omitted if desired

Developer Certificate of Origin
-------------------------------
All contributions to this project are subject to the terms of the Developer Certificate of Origin, available
`here <https://developercertificate.org/>`_ and reproduced below:

.. code-block:: none

    Developer Certificate of Origin
    Version 1.1

    Copyright (C) 2004, 2006 The Linux Foundation and its contributors.
    1 Letterman Drive
    Suite D4700
    San Francisco, CA, 94129

    Everyone is permitted to copy and distribute verbatim copies of this
    license document, but changing it is not allowed.

    Developer's Certificate of Origin 1.1

    By making a contribution to this project, I certify that:

    (a) The contribution was created in whole or in part by me and I
        have the right to submit it under the open source license
        indicated in the file; or

    (b) The contribution is based upon previous work that, to the best
        of my knowledge, is covered under an appropriate open source
        license and I have the right under that license to submit that
        work with modifications, whether created in whole or in part
        by me, under the same open source license (unless I am
        permitted to submit under a different license), as indicated
        in the file; or

    (c) The contribution was provided directly to me by some other
        person who certified (a), (b) or (c) and I have not modified
        it.

    (d) I understand and agree that this project and the contribution
        are public and that a record of the contribution (including all
        personal information I submit with it, including my sign-off) is
        maintained indefinitely and may be redistributed consistent with
        this project or the open source license(s) involved.

.. _`Corda Technical Whitepaper`: _static/corda-technical-whitepaper.pdf
