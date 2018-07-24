=======
How to contribute
=================

Identifying an area to contribute
---------------------------------

There are several ways to identify an area where you can contribute to Corda:

* Browse issues labelled as ``good first issue`` in the
  `Corda GitHub Issues <https://github.com/corda/corda/issues?q=is%3Aopen+is%3Aissue+label%3A%22good+first+issue%22>`_

  * Any issue with a ``good first issue`` label is considered ideal for open-source contributions
  * If there is a feature you would like to add and there isn't a corresponding issue labelled as ``good first issue``,
    that doesn't mean your contribution isn't welcome. Please reach out on the ``#design`` channel to clarify (see
    below)

* Ask in the ``#design`` channel of the `Corda Slack <http://slack.corda.net/>`_

Making the required changes
---------------------------

1. Create a fork of the master branch of the `Corda repo <https://github.com/corda/corda>`_
2. Clone the fork to your local machine
3. Build Corda by following the instructions :doc:`here </building-corda>`
4. Make the changes, in accordance with the :doc:`code style guide </codestyle>`

Things to check
^^^^^^^^^^^^^^^

Is your error handling up to scratch?
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Errors should not leak to the UI. When writing tools intended for end users, like the node or command line tools,
remember to add ``try``/``catch`` blocks. Throw meaningful errors. For example, instead of throwing an
``OutOfMemoryError``, use the error message to indicate that a file is missing, a network socket was unreachable, etc.
Tools should not dump stack traces to the end user.

Look for API breaks
~~~~~~~~~~~~~~~~~~~

We have an automated checker tool that runs as part of our continuous integration pipeline and helps a lot, but it
can't catch semantic changes where the behavior of an API changes in ways that might violate app developer expectations.

Suppress inevitable compiler warnings
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Compiler warnings should have a ``@Suppress`` annotation on them if they're expected and can't be avoided.

Remove deprecated functionality
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

When deprecating functionality, make sure you remove the deprecated uses in the codebase.

Avoid making formatting changes as you work
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

In Kotlin 1.2.20, new style guide rules were implemented. The new Kotlin style guide is significantly more detailed
than before and IntelliJ knows how to implement those rules. Re-formatting the codebase creates a lot of diffs that
make merging more complicated.

Things to consider when writing CLI apps
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

* Set exit codes using ``exitProcess``. Zero means success. Other numbers mean errors. Setting a unique error code
  (starting from 1) for each thing that can conceivably break makes your tool shell-scripting friendly

* Do a bit of work to figure out reasonable defaults. Nobody likes having to set a dozen flags before the tool will
  cooperate

* Your ``--help`` text or other docs should ideally include examples. Writing examples is also a good way to find out
  that your program requires a dozen flags to do anything

* Flags should have sensible defaults

* Don’t print logging output to the console unless the user requested it via a ``–verbose`` flag (conventionally
  shortened to ``-v``) or a ``–log-to-console`` flag. Logs should be either suppressed or saved to a text file during
  normal usage, except for errors, which are always OK to print

Testing the changes
-------------------

Adding tests
^^^^^^^^^^^^
Unit tests and integration tests for external API changes must cover Java and Kotlin. For internal API changes these
tests can be scaled back to kotlin only.

Running the tests
^^^^^^^^^^^^^^^^^
Your changes must pass the tests described :doc:`here </testing>`.

Manual testing
^^^^^^^^^^^^^^
Before sending that code for review, spend time poking and prodding the tool and thinking, “Would the experience of
using this feature make my mum proud of me?”. Automated tests are not a substitute for dogfooding.

Building against the master branch
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
You can test your changes against CorDapps defined in other repos by following the instructions
:doc:`here </building-against-master>`.

Running the API scanner
^^^^^^^^^^^^^^^^^^^^^^^
Your changes must also not break compatibility with existing public API. We have an API scanning tool which runs as part of the build
process which can be used to flag up any accidental changes, which is detailed :doc:`here </api-scanner>`.


Updating the docs
-----------------

Any changes to Corda's public API must be documented as follows:

1. Add comments and javadocs/kdocs. API functions must have javadoc/kdoc comments and sentences must be terminated
   with a full stop. We also start comments with capital letters, even for inline comments. Where Java APIs have
   synonyms (e.g. ``%d`` and ``%date``), we prefer the longer form for legibility reasons. You can configure your IDE
   to highlight these in bright yellow
2. Update the relevant `.rst file(s) <https://github.com/corda/corda/tree/master/docs/source>`_
3. Include the change in the :doc:`changelog </changelog>` if the change is external and therefore visible to CorDapp
   developers and/or node operators
4. :doc:`Build the docs locally </building-the-docs>`
5. Check the built .html files (under ``docs/build/html``) for the modified pages to ensure they render correctly
6. If relevant, add a sample. Samples are one of the key ways in which users learn about what the platform can do.
   If you add a new API or feature and don't update the samples, your work will be much less impactful

Merging the changes back into Corda
-----------------------------------

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

  * Add the following statement to confirm that your contribution is your own original work: "I hereby certify that my contribution is in accordance with the Developer Certificate of Origin (https://github.com/corda/corda/blob/master/CONTRIBUTING.md#developer-certificate-of-origin)."

4. Request a review from a member of the Corda platform team via the `#design channel <http://slack.corda.net/>`_

5. The reviewer will either:

  * Accept and merge your PR
  * Request that you make further changes. Do this by committing and pushing the changes onto the branch you are PRing
    into Corda. The PR will be updated automatically