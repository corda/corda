Testing your changes
====================

Automated tests
---------------
Corda has a suite of tests that any contributing developers must maintain and extend when adding new code.

There are several test suites:

* **Unit tests**: These are traditional unit tests that should only test a single code unit, typically a method or class.
* **Integration tests**: These tests should test the integration of small numbers of units, preferably with mocked out services.
* **Smoke tests**: These are full end to end tests which start a full set of Corda nodes and verify broader behaviour.
* **Other**: These include tests such as performance tests, stress tests, etc, and may be in an external repo.

Running the automated tests
^^^^^^^^^^^^^^^^^^^^^^^^^^^
These tests are mostly written with JUnit and can be run via ``gradle``:

* **Windows**: Run ``gradlew test integrationTest smokeTest``
* **Unix/Mac OSX**: Run ``./gradlew test integrationTest smokeTest``

Before creating a pull request please make sure these pass.

Manual testing
--------------
You should manually test anything that would be impacted by your changes. The areas that usually need to be manually tested and when are
as follows:

* **Node startup** - changes in the ``node`` or ``node:capsule`` project in both the Kotlin or gradle or the ``cordformation`` gradle plugin.
* **Sample project** - changes in the ``samples`` project. eg; changing the IRS demo means you should manually test the IRS demo.
* **Explorer** - changes to the ``tools/explorer`` project.
* **Demobench** - changes to the ``tools/demobench`` project.

How to manually test each of these areas differs and is currently not fully specified. For now the best thing to do is to ensure the
program starts, that you can interact with it, and that no exceptions are generated in normal operation.
